package io.github.marcussi02.notification.controller;

import io.github.marcussi02.notification.domain.campaign.Channel;
import io.github.marcussi02.notification.domain.campaign.Tenant;
import io.github.marcussi02.notification.dto.*;
import io.github.marcussi02.notification.repository.TenantRepository;
import io.github.marcussi02.notification.service.CampaignService;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CampaignController {

    private static final Logger log = LoggerFactory.getLogger(CampaignController.class);

    private final CampaignService campaignService;
    private final TenantRepository tenantRepository;

    public CampaignController(CampaignService campaignService, TenantRepository tenantRepository) {
        this.campaignService   = campaignService;
        this.tenantRepository  = tenantRepository;
    }

    @PostMapping("/campaigns")
    public ResponseEntity<?> createCampaign(
            @RequestParam @NotNull UUID tenantId,
            @RequestParam String name,
            @RequestParam Channel channel,
            @RequestParam String messageTemplate,
            @RequestParam(defaultValue = "true") boolean scheduleNow,
            @RequestParam(required = false) String scheduledTime,
            @RequestParam("csvFile") MultipartFile csvFile) {

        if (csvFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "CSV file is required"));
        }

        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setTenantId(tenantId);
        request.setName(name);
        request.setChannel(channel);
        request.setMessageTemplate(messageTemplate);
        request.setScheduleNow(scheduleNow);
        if (scheduledTime != null) {
            request.setScheduledTime(LocalDateTime.parse(scheduledTime));
        }

        try {
            CampaignResponse response = campaignService.createCampaign(request, csvFile);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to process CSV", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process CSV file"));
        }
    }

    @GetMapping("/campaigns")
    public ResponseEntity<PageResponse<CampaignResponse>> getCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantId) {

        PageResponse<CampaignResponse> response = campaignService.getCampaigns(page, size, search, status, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/campaigns/{id}")
    public ResponseEntity<?> getCampaign(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(campaignService.getCampaignDetail(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/campaigns/{id}/retry-failures")
    public ResponseEntity<Map<String, Object>> retryFailures(@PathVariable UUID id) {
        try {
            int count = campaignService.retryFailures(id);
            return ResponseEntity.ok(Map.of("message", "Retry queued", "jobsReset", count));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<Tenant>> getTenants() {
        return ResponseEntity.ok(tenantRepository.findAll());
    }
}
