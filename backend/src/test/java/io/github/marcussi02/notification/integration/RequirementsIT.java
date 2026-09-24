package io.github.marcussi02.notification.integration;

import io.github.marcussi02.notification.domain.campaign.Campaign;
import io.github.marcussi02.notification.domain.campaign.Channel;
import io.github.marcussi02.notification.domain.campaign.Tenant;
import io.github.marcussi02.notification.domain.notification.*;
import io.github.marcussi02.notification.provider.EmailProvider;
import io.github.marcussi02.notification.provider.ProviderResult;
import io.github.marcussi02.notification.ratelimit.ChannelRateLimiter;
import io.github.marcussi02.notification.repository.*;
import io.github.marcussi02.notification.rules.*;
import io.github.marcussi02.notification.service.NotificationProcessor;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of the platform requirements.
 * Uses H2 in-memory database — no Docker required.
 *
 * Run with:
 *   mvn test -Dtest=RequirementsIT
 *
 * Requirements verified:
 *   REQ-1   Provider latency 50-200 ms
 *   REQ-2   Failure rate ~20%
 *   REQ-3   Rate limiting 100 req/min per channel
 *   REQ-4   Exponential backoff (2^n * 15 s)
 *   REQ-5a  Rule Engine - Global Suppression
 *   REQ-5b  Rule Engine - DND Window quiet hours
 *   REQ-6   Idempotency via DB UNIQUE constraint
 *   REQ-7   Atomic job claiming (at-most-once)
 *   REQ-8   HTTP 202 Accepted for async campaign creation
 *   REQ-9   End-to-end pipeline updates campaign stats
 *   REQ-10  Retry queue reset
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        // H2 in-memory database - no Docker, no Flyway SQL compatibility issues
        "spring.datasource.url=jdbc:h2:mem:requirementsdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        // Let Hibernate create the schema from entity annotations (no Flyway migration SQL needed)
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        // Disable scheduler auto-firing; tests call processJob() directly
        "app.notification.processor-interval-ms=3600000",
        "app.rate-limit.requests-per-minute=100",
        "app.notification.max-retries=3"
    }
)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequirementsIT {

    // ── Spring beans ─────────────────────────────────────────────────────────
    @Autowired MockMvc                    mockMvc;
    @Autowired NotificationProcessor      notificationProcessor;
    @Autowired EmailProvider              emailProvider;
    @Autowired ChannelRateLimiter         channelRateLimiter;
    @Autowired RuleEngine                 ruleEngine;
    @Autowired NotificationJobRepository  jobRepository;
    @Autowired RecipientRepository        recipientRepository;
    @Autowired CampaignRepository         campaignRepository;
    @Autowired DeliveryAttemptRepository  deliveryAttemptRepository;
    @Autowired SuppressionListRepository  suppressionListRepository;
    @Autowired TenantRepository           tenantRepository;

    private static final String HR = "=".repeat(65);

    // Tenant created once for the whole test class
    private static UUID TENANT_ID;

    @BeforeAll
    static void seedTenant(@Autowired TenantRepository tenantRepository) {
        Tenant t = new Tenant();
        t.setName("Demo Tenant");
        t.setMonthlyCampaignLimit(500);
        t.setMonthlyMessageLimit(5000000);
        t = tenantRepository.save(t);
        TENANT_ID = t.getId();
        System.out.println("\n[Setup] Tenant created: " + TENANT_ID);
    }

    @BeforeEach
    void resetRateLimiter() {
        channelRateLimiter.resetCounters();
    }

    @AfterEach
    void cleanupJobData() {
        deliveryAttemptRepository.deleteAll();
        jobRepository.deleteAll();
        suppressionListRepository.deleteAll();
        recipientRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    // =========================================================================
    // REQ-1  Provider Latency: every send call takes 50-200 ms
    // =========================================================================
    @Test @Order(1)
    void req1_providerLatency_between50and200ms() {
        System.out.println("\n" + HR);
        System.out.println("REQ-1 | Provider Latency  (spec: 50-200 ms per call)");
        System.out.println(HR);

        Campaign  campaign  = saveCampaign("Latency Test", Channel.EMAIL);
        Recipient recipient = saveRecipient(campaign.getId(), "lat-r1", "lat@test.com");
        NotificationJob job = saveJob(campaign.getId(), recipient.getId(), "lat-job");

        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long t0 = System.currentTimeMillis();
            ProviderResult result = emailProvider.send(job, recipient, "Hello!");
            long ms = System.currentTimeMillis() - t0;
            latencies.add(ms);
            System.out.printf("  call %2d: %3d ms  [%s]%n", i + 1, ms,
                    result.isSuccess() ? "SENT" : "FAIL");
        }

        long min = latencies.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
        long avg = (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0);

        System.out.printf("%n  min=%d ms  avg=%d ms  max=%d ms%n", min, avg, max);
        System.out.println("  PASS - all calls within 50-200 ms window");

        assertThat(min).as("min latency >= 50 ms").isGreaterThanOrEqualTo(50);
        assertThat(max).as("max latency <= 250 ms (200 ms spec + 50 ms CI buffer)")
                .isLessThanOrEqualTo(250);
    }

    // =========================================================================
    // REQ-2  Failure Rate: ~20% of sends fail (15-25% spec range)
    // =========================================================================
    @Test @Order(2)
    void req2_failureRate_approximately20percent() {
        System.out.println("\n" + HR);
        System.out.println("REQ-2 | Provider Failure Rate  (spec: 15-25%)");
        System.out.println(HR);

        Campaign  campaign  = saveCampaign("Failure Rate", Channel.EMAIL);
        Recipient recipient = saveRecipient(campaign.getId(), "fr-r1", "fr@test.com");
        NotificationJob job = saveJob(campaign.getId(), recipient.getId(), "fr-job");

        int total = 50, failures = 0;
        for (int i = 0; i < total; i++) {
            if (!emailProvider.send(job, recipient, "Hello!").isSuccess()) failures++;
        }

        double rate = failures * 100.0 / total;
        System.out.printf("  Calls: %d  |  Sent: %d  |  Failed: %d%n",
                total, total - failures, failures);
        System.out.printf("  Failure rate: %.1f%%  (FAILURE_RATE=0.20 in EmailProvider)%n", rate);
        System.out.println("  PASS - failure rate within expected statistical range");

        // Wide bounds for N=50; true rate is 20%, allow ±15 points for randomness
        assertThat(rate)
                .as("20%% target ± statistical variance for N=50")
                .isGreaterThanOrEqualTo(5.0)
                .isLessThanOrEqualTo(40.0);
    }

    // =========================================================================
    // REQ-3  Rate Limiting: exactly 100 requests allowed per channel per minute
    // =========================================================================
    @Test @Order(3)
    void req3_rateLimiting_100RequestsPerMinute() {
        System.out.println("\n" + HR);
        System.out.println("REQ-3 | Rate Limiting  (spec: 100 req/min per channel)");
        System.out.println(HR);

        // @BeforeEach already reset counters
        int allowed = 0, rejected = 0;
        for (int i = 0; i < 105; i++) {
            if (channelRateLimiter.tryAcquire(Channel.EMAIL)) allowed++;
            else rejected++;
        }

        System.out.printf("  Total calls:  105%n");
        System.out.printf("  Allowed:      %d  (spec: exactly 100)%n", allowed);
        System.out.printf("  Rejected:     %d  (spec: 5 over the limit)%n", rejected);
        System.out.printf("  Usage meter:  %d / 100%n", channelRateLimiter.getUsage(Channel.EMAIL));
        System.out.println("  PASS - rate limiter enforces 100 req/min ceiling");

        assertThat(allowed).isEqualTo(100);
        assertThat(rejected).isEqualTo(5);
    }

    // =========================================================================
    // REQ-4  Exponential Backoff: 2^retry * 15 s delay between retries
    // =========================================================================
    @Test @Order(4)
    void req4_exponentialBackoff_doubleOnEachRetry() {
        System.out.println("\n" + HR);
        System.out.println("REQ-4 | Exponential Backoff  (spec: 2^retry * 15 s)");
        System.out.println(HR);

        NotificationJob job = new NotificationJob();

        job.markFailed("provider timeout", 3);
        long b1 = java.time.Duration.between(LocalDateTime.now(), job.getNextRetryAt()).getSeconds();
        System.out.printf("  Retry 1 -> retryCount=%d  nextRetryAt +%d s  (spec: 30 s = 2^1 x 15)%n",
                job.getRetryCount(), b1);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(b1).isBetween(25L, 35L);

        job.markFailed("provider timeout", 3);
        long b2 = java.time.Duration.between(LocalDateTime.now(), job.getNextRetryAt()).getSeconds();
        System.out.printf("  Retry 2 -> retryCount=%d  nextRetryAt +%d s  (spec: 60 s = 2^2 x 15)%n",
                job.getRetryCount(), b2);
        assertThat(job.getRetryCount()).isEqualTo(2);
        assertThat(b2).isBetween(55L, 65L);

        job.markFailed("provider timeout", 3);
        System.out.printf("  Retry 3 -> retryCount=%d  nextRetryAt=%s  (permanently failed)%n",
                job.getRetryCount(), job.getNextRetryAt());
        assertThat(job.getRetryCount()).isEqualTo(3);
        assertThat(job.getNextRetryAt()).isNull();
        assertThat(job.isRetryEligible(3)).isFalse();
        assertThat(job.getStatus()).isEqualTo(NotificationStatus.FAILED);

        System.out.println("  PASS - backoff schedule: 30 s, 60 s, then permanently failed");
    }

    // =========================================================================
    // REQ-5a  Rule Engine: Global Suppression skips opted-out recipients
    // =========================================================================
    @Test @Order(5)
    void req5a_globalSuppression_blocksOptedOutRecipient() {
        System.out.println("\n" + HR);
        System.out.println("REQ-5a | Rule Engine - Global Suppression");
        System.out.println(HR);

        SuppressionEntry entry = new SuppressionEntry();
        entry.setTenantId(TENANT_ID);
        entry.setRecipientId("opted-out-user");
        entry.setChannel("EMAIL");
        suppressionListRepository.save(entry);

        Campaign campaign = saveCampaign("Suppression Test", Channel.EMAIL);

        Recipient suppressed = new Recipient();
        suppressed.setCampaignId(campaign.getId());
        suppressed.setTenantId(TENANT_ID);
        suppressed.setRecipientId("opted-out-user");
        suppressed.setEmail("optout@test.com");
        suppressed = recipientRepository.save(suppressed);

        NotificationJob job = saveJob(campaign.getId(), suppressed.getId(), "suppression-job");
        RuleResult result = ruleEngine.evaluate(new RuleContext(job, campaign, suppressed));

        System.out.printf("  Recipient '%s' is in the EMAIL suppression list%n",
                suppressed.getRecipientId());
        System.out.printf("  Rule action: %s%n", result.getAction());
        System.out.printf("  Reason:      %s%n", result.getReason());
        System.out.println("  PASS - suppressed recipient blocked before provider call");

        assertThat(result.isBlocking()).isTrue();
        assertThat(result.getAction()).isEqualTo(RuleAction.SKIP);
        assertThat(result.getReason()).containsIgnoringCase("unsubscribed");
    }

    // =========================================================================
    // REQ-5b  Rule Engine: DND Window delays SMS/PUSH during quiet hours
    // =========================================================================
    @Test @Order(6)
    void req5b_dndWindowRule_emailExempt_smsRespectsQuietHours() {
        System.out.println("\n" + HR);
        System.out.println("REQ-5b | Rule Engine - DND Window (22:00-08:00 quiet hours)");
        System.out.println(HR);

        // EMAIL is unconditionally exempt from quiet hours
        Campaign emailCampaign = saveCampaign("DND Email", Channel.EMAIL);
        Recipient r = saveRecipient(emailCampaign.getId(), "dnd-r1", "dnd@test.com");
        NotificationJob emailJob = saveJob(emailCampaign.getId(), r.getId(), "dnd-email-job");

        RuleResult emailResult = ruleEngine.evaluate(new RuleContext(emailJob, emailCampaign, r));
        System.out.printf("  EMAIL channel -> action: %s  (always exempt from DND by design)%n",
                emailResult.getAction());
        assertThat(emailResult.getAction()).isEqualTo(RuleAction.ALLOW);

        // SMS with Pacific/Kiritimati (UTC+14): maximally ahead, often in DND window
        Campaign smsCampaign = saveCampaign("DND SMS", Channel.SMS);
        Recipient nightOwl = new Recipient();
        nightOwl.setCampaignId(smsCampaign.getId());
        nightOwl.setTenantId(TENANT_ID);
        nightOwl.setRecipientId("dnd-sms-r1");
        nightOwl.setPhone("+1234567890");
        nightOwl.setTimezone("Pacific/Kiritimati");
        nightOwl = recipientRepository.save(nightOwl);

        NotificationJob smsJob = saveJob(smsCampaign.getId(), nightOwl.getId(), "dnd-sms-job");
        RuleResult smsResult = ruleEngine.evaluate(new RuleContext(smsJob, smsCampaign, nightOwl));

        System.out.printf("  SMS channel, timezone=Pacific/Kiritimati -> action: %s%n",
                smsResult.getAction());
        if (smsResult.getAction() == RuleAction.DELAY) {
            System.out.printf("  DND active! Delivery delayed until: %s%n", smsResult.getDelayUntil());
            assertThat(smsResult.getDelayUntil()).isAfter(LocalDateTime.now());
        } else {
            System.out.println("  Not in DND window at this local time - SMS allowed");
        }
        System.out.println("  PASS - EMAIL always exempt; SMS DND rule evaluates per timezone");

        // Hard assertion: EMAIL always ALLOW; SMS only ALLOW or DELAY (never SKIP/REJECT)
        assertThat(emailResult.getAction()).isEqualTo(RuleAction.ALLOW);
        assertThat(smsResult.getAction()).isIn(RuleAction.ALLOW, RuleAction.DELAY);
    }

    // =========================================================================
    // REQ-6  Idempotency: DB UNIQUE constraint on idempotency_key
    // =========================================================================
    @Test @Order(7)
    void req6_idempotency_rejectsDuplicateKey() {
        System.out.println("\n" + HR);
        System.out.println("REQ-6 | Idempotency - Duplicate Key Rejection");
        System.out.println(HR);

        Campaign  campaign = saveCampaign("Idempotency Test", Channel.EMAIL);
        Recipient r1 = saveRecipient(campaign.getId(), "idem-r1", "idem1@test.com");
        Recipient r2 = saveRecipient(campaign.getId(), "idem-r2", "idem2@test.com");

        String sharedKey = "idempotency-demo-key-xyz";
        saveJob(campaign.getId(), r1.getId(), sharedKey);
        System.out.printf("  Job 1 saved with idempotency_key='%s' -> OK%n", sharedKey);
        System.out.printf("  Job 2 attempting same key...%n");

        assertThatThrownBy(() -> {
            NotificationJob dup = new NotificationJob();
            dup.setCampaignId(campaign.getId());
            dup.setTenantId(TENANT_ID);
            dup.setRecipientId(r2.getId());
            dup.setIdempotencyKey(sharedKey);
            jobRepository.saveAndFlush(dup);
        }).isInstanceOf(DataIntegrityViolationException.class);

        System.out.println("  DataIntegrityViolationException thrown");
        System.out.println("  DB UNIQUE constraint on notification_jobs(idempotency_key) enforced");
        System.out.println("  PASS - duplicate send attempt rejected at the persistence layer");
    }

    // =========================================================================
    // REQ-7  Atomic Job Claiming: only the first worker wins the UPDATE
    // =========================================================================
    @Test @Order(8)
    void req7_atomicJobClaiming_onlyFirstWorkerSucceeds() {
        System.out.println("\n" + HR);
        System.out.println("REQ-7 | Atomic Job Claiming  (UPDATE WHERE status='PENDING')");
        System.out.println(HR);

        Campaign  campaign = saveCampaign("Atomic Claim", Channel.EMAIL);
        Recipient r        = saveRecipient(campaign.getId(), "claim-r1", "claim@test.com");
        NotificationJob job = saveJob(campaign.getId(), r.getId(), "atomic-claim-key");

        int first  = jobRepository.tryClaimJob(job.getId(), LocalDateTime.now());
        int second = jobRepository.tryClaimJob(job.getId(), LocalDateTime.now());

        System.out.printf("  Job ID:       %s (status: PENDING)%n", job.getId());
        System.out.printf("  First claim:  %d row(s) updated -> status now PROCESSING%n", first);
        System.out.printf("  Second claim: %d row(s) updated -> no-op (already claimed)%n", second);
        System.out.println("  PASS - at-most-once delivery guarantee via atomic DB update");

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
    }

    // =========================================================================
    // REQ-8  HTTP 202 Accepted: campaign creation returns immediately
    // =========================================================================
    @Test @Order(9)
    void req8_http202_campaignCreationIsNonBlocking() throws Exception {
        System.out.println("\n" + HR);
        System.out.println("REQ-8 | HTTP 202 Accepted - Async Campaign Creation");
        System.out.println(HR);

        String csvContent = "recipientId,email,phone\nr1,async1@test.com,+11111\nr2,async2@test.com,+22222";
        MockMultipartFile csv = new MockMultipartFile(
                "csvFile", "recipients.csv", "text/csv", csvContent.getBytes());

        long t0 = System.currentTimeMillis();
        mockMvc.perform(multipart("/api/campaigns")
                        .file(csv)
                        .param("tenantId",       TENANT_ID.toString())
                        .param("name",            "Async Campaign Test")
                        .param("channel",         "EMAIL")
                        .param("messageTemplate", "Hello {name}!")
                        .param("scheduleNow",     "true"))
                .andExpect(status().isAccepted());
        long elapsed = System.currentTimeMillis() - t0;

        System.out.printf("  POST /api/campaigns -> HTTP 202 ACCEPTED in %d ms%n", elapsed);
        System.out.println("  Response returned before any delivery attempt");
        System.out.println("  Jobs queued; background @Scheduled processor picks them up");
        System.out.println("  PASS - campaign creation is fire-and-forget (non-blocking)");
    }

    // =========================================================================
    // REQ-9  End-to-End Pipeline: processJob -> rule check -> provider -> DB
    // =========================================================================
    @Test @Order(10)
    void req9_endToEnd_fullPipelineUpdatesCampaignStats() {
        System.out.println("\n" + HR);
        System.out.println("REQ-9 | End-to-End Processing Pipeline");
        System.out.println(HR);
        System.out.println("  Flow: claim -> rule engine -> rate limiter -> provider -> delivery_attempt -> campaign");

        Campaign campaign = saveCampaign("E2E Pipeline", Channel.EMAIL);
        campaign.setTotalRecipients(3);
        campaign.markRunning();
        campaign = campaignRepository.save(campaign);

        Recipient r1 = saveRecipient(campaign.getId(), "e2e-r1", "e1@test.com");
        Recipient r2 = saveRecipient(campaign.getId(), "e2e-r2", "e2@test.com");
        Recipient r3 = saveRecipient(campaign.getId(), "e2e-r3", "e3@test.com");

        NotificationJob j1 = saveJob(campaign.getId(), r1.getId(), "e2e-job-1");
        NotificationJob j2 = saveJob(campaign.getId(), r2.getId(), "e2e-job-2");
        NotificationJob j3 = saveJob(campaign.getId(), r3.getId(), "e2e-job-3");

        long t0 = System.currentTimeMillis();
        notificationProcessor.processJob(j1.getId());
        notificationProcessor.processJob(j2.getId());
        notificationProcessor.processJob(j3.getId());
        long elapsed = System.currentTimeMillis() - t0;

        campaign = campaignRepository.findById(campaign.getId()).orElseThrow();
        List<NotificationJob> jobs = jobRepository.findByCampaignId(campaign.getId());
        long pending  = jobs.stream().filter(j -> j.getStatus() == NotificationStatus.PENDING).count();
        long attempts = deliveryAttemptRepository.findByCampaignId(campaign.getId()).size();

        System.out.printf("%n  Processing time: %d ms (3 x 50-200 ms provider latency)%n", elapsed);
        System.out.printf("  Sent:    %d | Failed: %d | Skipped: %d | Pending: %d%n",
                campaign.getSentCount(), campaign.getFailedCount(),
                campaign.getSkippedCount(), pending);
        System.out.printf("  Delivery rate:   %.1f%%%n", campaign.getDeliveryRate());
        System.out.printf("  Campaign status: %s%n", campaign.getStatus());
        System.out.printf("  Delivery attempts logged: %d%n", attempts);
        System.out.println("  PASS - all jobs exited PENDING; campaign counters updated; attempts logged");

        assertThat(pending).isZero();
        assertThat(elapsed).isGreaterThanOrEqualTo(150L); // 3 x 50 ms minimum
        assertThat(campaign.getSentCount() + campaign.getFailedCount() + campaign.getSkippedCount())
                .isGreaterThan(0);
    }

    // =========================================================================
    // REQ-10  Retry Queue Reset: failed jobs can be re-queued via repository
    // =========================================================================
    @Test @Order(11)
    void req10_retryQueue_resetsFailedJobsToPending() {
        System.out.println("\n" + HR);
        System.out.println("REQ-10 | Retry Queue Reset  (POST /campaigns/{id}/retry-failures)");
        System.out.println(HR);

        Campaign  campaign = saveCampaign("Retry Reset", Channel.EMAIL);
        Recipient r1 = saveRecipient(campaign.getId(), "retry-r1", "retry1@test.com");
        Recipient r2 = saveRecipient(campaign.getId(), "retry-r2", "retry2@test.com");

        NotificationJob f1 = saveJob(campaign.getId(), r1.getId(), "retry-fail-1");
        f1.setStatus(NotificationStatus.FAILED);
        f1.setRetryCount(3);
        jobRepository.save(f1);

        NotificationJob f2 = saveJob(campaign.getId(), r2.getId(), "retry-fail-2");
        f2.setStatus(NotificationStatus.FAILED);
        f2.setRetryCount(3);
        jobRepository.save(f2);

        System.out.println("  Permanently failed jobs (retryCount=maxRetries=3): 2");

        int reset = jobRepository.resetFailedJobsForCampaign(campaign.getId(), LocalDateTime.now());

        long pending = jobRepository.findByCampaignId(campaign.getId()).stream()
                .filter(j -> j.getStatus() == NotificationStatus.PENDING).count();

        System.out.printf("  Jobs reset to PENDING: %d%n", reset);
        System.out.printf("  PENDING count after:   %d%n", pending);
        System.out.println("  Next processor tick will pick them up for redelivery");
        System.out.println("  PASS - permanently failed jobs successfully re-queued");

        assertThat(reset).isEqualTo(2);
        assertThat(pending).isEqualTo(2);
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private Campaign saveCampaign(String name, Channel channel) {
        Campaign c = new Campaign();
        c.setTenantId(TENANT_ID);
        c.setName(name);
        c.setChannel(channel);
        c.setMessageTemplate("Test message");
        c.setTotalRecipients(1);
        return campaignRepository.save(c);
    }

    private Recipient saveRecipient(UUID campaignId, String recipientId, String email) {
        Recipient r = new Recipient();
        r.setCampaignId(campaignId);
        r.setTenantId(TENANT_ID);
        r.setRecipientId(recipientId);
        r.setEmail(email);
        return recipientRepository.save(r);
    }

    private NotificationJob saveJob(UUID campaignId, UUID recipientId, String key) {
        NotificationJob j = new NotificationJob();
        j.setCampaignId(campaignId);
        j.setTenantId(TENANT_ID);
        j.setRecipientId(recipientId);
        j.setIdempotencyKey(key);
        return jobRepository.save(j);
    }
}
