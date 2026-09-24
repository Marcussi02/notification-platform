package io.github.marcussi02.notification.service;

import io.github.marcussi02.notification.domain.campaign.Campaign;
import io.github.marcussi02.notification.domain.campaign.CampaignStatus;
import io.github.marcussi02.notification.domain.campaign.Tenant;
import io.github.marcussi02.notification.dto.*;
import io.github.marcussi02.notification.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaignRepository;
    private final TenantRepository tenantRepository;
    private final NotificationJobRepository notificationJobRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final CsvProcessingService csvProcessingService;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    public CampaignService(CampaignRepository campaignRepository,
                           TenantRepository tenantRepository,
                           NotificationJobRepository notificationJobRepository,
                           DeliveryAttemptRepository deliveryAttemptRepository,
                           CsvProcessingService csvProcessingService) {
        this.campaignRepository        = campaignRepository;
        this.tenantRepository          = tenantRepository;
        this.notificationJobRepository = notificationJobRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.csvProcessingService      = csvProcessingService;
    }

    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest request, MultipartFile csvFile) throws IOException {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + request.getTenantId()));

        if (tenant.hasExceededCampaignLimit()) {
            throw new IllegalStateException("Tenant has exceeded monthly campaign limit");
        }

        Campaign campaign = new Campaign();
        campaign.setTenantId(request.getTenantId());
        campaign.setName(request.getName());
        campaign.setChannel(request.getChannel());
        campaign.setMessageTemplate(request.getMessageTemplate());
        campaign.setStatus(CampaignStatus.SCHEDULED);
        campaign.setScheduledTime(request.isScheduleNow() ? LocalDateTime.now() : request.getScheduledTime());

        campaign = campaignRepository.save(campaign);
        final UUID campaignId = campaign.getId();

        MDC.put("tenantId", request.getTenantId().toString());
        MDC.put("campaignId", campaignId.toString());
        log.info("Campaign created | name={} | channel={}", request.getName(), request.getChannel());

        int recipientCount = csvProcessingService.processRecipients(
                csvFile.getInputStream(), campaignId, request.getTenantId());

        campaign.setTotalRecipients(recipientCount);
        campaign = campaignRepository.save(campaign);

        tenant.setCampaignsUsed(tenant.getCampaignsUsed() + 1);
        tenant.setMessagesUsed(tenant.getMessagesUsed() + recipientCount);
        tenantRepository.save(tenant);

        log.info("Campaign enqueued | recipients={}", recipientCount);
        MDC.clear();

        return CampaignResponse.from(campaign);
    }

    public PageResponse<CampaignResponse> getCampaigns(int page, int size, String search,
                                                        String status, String tenantIdStr) {
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
        CampaignStatus campaignStatus = status != null ? CampaignStatus.valueOf(status) : null;

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Campaign> result = campaignRepository.findWithFilters(tenantId, campaignStatus, search, pageRequest);

        return PageResponse.from(result, CampaignResponse::from);
    }

    public CampaignDetailResponse getCampaignDetail(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        int retryQueueSize = notificationJobRepository.countRetryQueueSize(campaignId, maxRetries);
        double throughput  = deliveryAttemptRepository.countSentSince(campaignId, LocalDateTime.now().minusMinutes(1));

        List<CampaignDetailResponse.TimelinePoint> timeline = buildTimeline(campaignId);
        Map<String, Long> errorBreakdown = buildErrorBreakdown(campaignId);

        CampaignDetailResponse r = new CampaignDetailResponse();
        r.setId(campaign.getId());
        r.setTenantId(campaign.getTenantId());
        r.setName(campaign.getName());
        r.setChannel(campaign.getChannel());
        r.setMessageTemplate(campaign.getMessageTemplate());
        r.setStatus(campaign.getStatus());
        r.setScheduledTime(campaign.getScheduledTime());
        r.setTotalRecipients(campaign.getTotalRecipients());
        r.setSentCount(campaign.getSentCount());
        r.setFailedCount(campaign.getFailedCount());
        r.setSkippedCount(campaign.getSkippedCount());
        r.setCreatedAt(campaign.getCreatedAt());
        r.setUpdatedAt(campaign.getUpdatedAt());
        r.setDeliveryRate(campaign.getDeliveryRate());
        r.setRetryQueueSize(retryQueueSize);
        r.setThroughputPerMinute(throughput);
        r.setDeliveryTimeline(timeline);
        r.setErrorBreakdown(errorBreakdown);
        return r;
    }

    @Transactional
    public int retryFailures(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        int reset = notificationJobRepository.resetFailedJobsForCampaign(campaignId, LocalDateTime.now());

        if (reset > 0 && campaign.getStatus() == CampaignStatus.COMPLETED) {
            campaign.setStatus(CampaignStatus.RUNNING);
            campaignRepository.save(campaign);
        }

        log.info("Retry failures | campaignId={} | jobsReset={}", campaignId, reset);
        return reset;
    }

    private List<CampaignDetailResponse.TimelinePoint> buildTimeline(UUID campaignId) {
        var attempts = deliveryAttemptRepository.findByCampaignId(campaignId);
        if (attempts.isEmpty()) return List.of();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        Map<String, long[]> buckets = new LinkedHashMap<>();

        attempts.forEach(a -> {
            String key = a.getAttemptedAt().format(fmt);
            buckets.computeIfAbsent(key, k -> new long[]{0, 0});
            if ("SENT".equals(a.getStatus()))        buckets.get(key)[0]++;
            else if ("FAILED".equals(a.getStatus())) buckets.get(key)[1]++;
        });

        return buckets.entrySet().stream()
                .map(e -> new CampaignDetailResponse.TimelinePoint(
                        e.getKey(), e.getValue()[0], e.getValue()[1]))
                .collect(Collectors.toList());
    }

    private Map<String, Long> buildErrorBreakdown(UUID campaignId) {
        return deliveryAttemptRepository.getErrorBreakdown(campaignId)
                .stream()
                .filter(row -> row[0] != null)
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long)   row[1],
                        Long::sum,
                        LinkedHashMap::new
                ));
    }
}
