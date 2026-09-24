package io.github.marcussi02.notification.service;

import io.github.marcussi02.notification.domain.campaign.Campaign;
import io.github.marcussi02.notification.domain.notification.DeliveryAttempt;
import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.NotificationStatus;
import io.github.marcussi02.notification.domain.notification.Recipient;
import io.github.marcussi02.notification.provider.ProviderGateway;
import io.github.marcussi02.notification.provider.ProviderResult;
import io.github.marcussi02.notification.ratelimit.ChannelRateLimiter;
import io.github.marcussi02.notification.repository.*;
import io.github.marcussi02.notification.rules.RuleContext;
import io.github.marcussi02.notification.rules.RuleEngine;
import io.github.marcussi02.notification.rules.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class NotificationProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessor.class);

    private final NotificationJobRepository jobRepository;
    private final RecipientRepository recipientRepository;
    private final CampaignRepository campaignRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final RuleEngine ruleEngine;
    private final ChannelRateLimiter rateLimiter;
    private final ProviderGateway providerGateway;
    private final Executor notificationExecutor;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    @Value("${app.notification.batch-size:20}")
    private int batchSize;

    public NotificationProcessor(NotificationJobRepository jobRepository,
                                 RecipientRepository recipientRepository,
                                 CampaignRepository campaignRepository,
                                 DeliveryAttemptRepository deliveryAttemptRepository,
                                 RuleEngine ruleEngine,
                                 ChannelRateLimiter rateLimiter,
                                 ProviderGateway providerGateway,
                                 Executor notificationExecutor) {
        this.jobRepository          = jobRepository;
        this.recipientRepository    = recipientRepository;
        this.campaignRepository     = campaignRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.ruleEngine             = ruleEngine;
        this.rateLimiter            = rateLimiter;
        this.providerGateway        = providerGateway;
        this.notificationExecutor   = notificationExecutor;
    }

    @Scheduled(fixedDelayString = "${app.notification.processor-interval-ms:5000}")
    public void processPendingJobs() {
        PageRequest pageRequest = PageRequest.of(0, batchSize);
        List<NotificationJob> pendingJobs = jobRepository.findPendingJobs(pageRequest);
        List<NotificationJob> retryJobs   = jobRepository.findRetryEligibleJobs(maxRetries, LocalDateTime.now(), pageRequest);
        List<NotificationJob> delayedJobs = jobRepository.findReadyDelayedJobs(LocalDateTime.now(), pageRequest);

        int total = pendingJobs.size() + retryJobs.size() + delayedJobs.size();
        if (total == 0) return;

        log.debug("Processor tick | pending={} | retry={} | delayed={}",
                pendingJobs.size(), retryJobs.size(), delayedJobs.size());

        for (NotificationJob job : pendingJobs) submitJob(job);
        for (NotificationJob job : retryJobs)   submitJob(job);
        for (NotificationJob job : delayedJobs) submitJob(job);
    }

    private void submitJob(NotificationJob job) {
        try {
            notificationExecutor.execute(() -> processJob(job.getId()));
        } catch (Exception e) {
            log.warn("Worker queue full - back-pressure for job {}", job.getId());
        }
    }

    @Transactional
    public void processJob(UUID jobId) {
        int claimed = jobRepository.tryClaimJob(jobId, LocalDateTime.now());
        if (claimed == 0) return;

        NotificationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        Recipient recipient = recipientRepository.findById(job.getRecipientId()).orElse(null);
        Campaign  campaign  = campaignRepository.findById(job.getCampaignId()).orElse(null);
        if (recipient == null || campaign == null) {
            job.markFailed("Missing recipient or campaign data", maxRetries);
            jobRepository.save(job);
            return;
        }

        MDC.put("tenantId",      job.getTenantId().toString());
        MDC.put("campaignId",    job.getCampaignId().toString());
        MDC.put("notificationId", jobId.toString());
        MDC.put("retryCount",    String.valueOf(job.getRetryCount()));

        try {
            RuleContext ctx = new RuleContext(job, campaign, recipient);
            RuleResult ruleResult = ruleEngine.evaluate(ctx);

            if (ruleResult.isBlocking()) {
                applyRuleAction(job, campaign, ruleResult);
                return;
            }

            if (!rateLimiter.tryAcquire(campaign.getChannel())) {
                log.warn("Rate limited on channel={} | jobId={}", campaign.getChannel(), jobId);
                job.setStatus(NotificationStatus.PENDING);
                job.setUpdatedAt(LocalDateTime.now());
                jobRepository.save(job);
                return;
            }

            ProviderResult result = providerGateway.send(
                    campaign.getChannel(), job, recipient, campaign.getMessageTemplate());

            DeliveryAttempt attempt = new DeliveryAttempt();
            attempt.setNotificationJobId(jobId);
            attempt.setAttemptNumber(job.getRetryCount() + 1);
            attempt.setStatus(result.isSuccess() ? "SENT" : "FAILED");
            attempt.setErrorMessage(result.getErrorMessage());
            deliveryAttemptRepository.save(attempt);

            if (result.isSuccess()) {
                job.markSent();
                campaign.incrementSent();
                log.info("Notification sent | status=SENT");
            } else {
                job.markFailed(result.getErrorMessage(), maxRetries);
                if (job.getRetryCount() >= maxRetries) {
                    campaign.incrementFailed();
                    log.warn("Notification permanently failed after {} retries", maxRetries);
                } else {
                    log.info("Notification failed, scheduled retry {} | nextRetryAt={}",
                            job.getRetryCount(), job.getNextRetryAt());
                }
            }

            jobRepository.save(job);
            campaignRepository.save(campaign);

        } finally {
            MDC.clear();
        }
    }

    private void applyRuleAction(NotificationJob job, Campaign campaign, RuleResult result) {
        switch (result.getAction()) {
            case SKIP -> {
                job.markSkipped(result.getReason());
                campaign.incrementSkipped();
            }
            case DELAY -> job.markDelayed(result.getDelayUntil());
            case REJECT, DISCARD -> {
                job.markSkipped(result.getReason());
                campaign.incrementSkipped();
            }
            default -> {}
        }
        jobRepository.save(job);
        campaignRepository.save(campaign);
    }
}
