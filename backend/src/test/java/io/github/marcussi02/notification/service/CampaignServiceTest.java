package io.github.marcussi02.notification.service;

import io.github.marcussi02.notification.domain.campaign.Campaign;
import io.github.marcussi02.notification.domain.campaign.CampaignStatus;
import io.github.marcussi02.notification.domain.campaign.Channel;
import io.github.marcussi02.notification.domain.campaign.Tenant;
import io.github.marcussi02.notification.dto.CreateCampaignRequest;
import io.github.marcussi02.notification.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private NotificationJobRepository notificationJobRepository;
    @Mock private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock private CsvProcessingService csvProcessingService;

    @InjectMocks
    private CampaignService campaignService;

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Test Tenant");
        tenant.setMonthlyCampaignLimit(100);
        tenant.setMonthlyMessageLimit(100000);
        ReflectionTestUtils.setField(campaignService, "maxRetries", 3);
    }

    @Test
    void createCampaign_success() throws Exception {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        Campaign saved = new Campaign();
        saved.setId(UUID.randomUUID());
        saved.setTenantId(tenantId);
        saved.setName("Test Campaign");
        saved.setChannel(Channel.EMAIL);
        saved.setMessageTemplate("Hello!");
        saved.setStatus(CampaignStatus.SCHEDULED);

        when(campaignRepository.save(any())).thenReturn(saved);
        when(csvProcessingService.processRecipients(any(), any(), any())).thenReturn(5);
        when(tenantRepository.save(any())).thenReturn(tenant);

        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setTenantId(tenantId);
        request.setName("Test Campaign");
        request.setChannel(Channel.EMAIL);
        request.setMessageTemplate("Hello!");
        request.setScheduleNow(true);

        String csvContent = "recipientId,email,phone\nr1,a@b.com,123";
        MockMultipartFile file = new MockMultipartFile("csvFile", "test.csv", "text/csv", csvContent.getBytes());

        var response = campaignService.createCampaign(request, file);
        assertThat(response.getName()).isEqualTo("Test Campaign");
        assertThat(response.getChannel()).isEqualTo(Channel.EMAIL);
    }

    @Test
    void createCampaign_throwsWhenTenantNotFound() {
        when(tenantRepository.findById(any())).thenReturn(Optional.empty());

        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setTenantId(UUID.randomUUID());
        request.setName("Test");
        request.setChannel(Channel.SMS);
        request.setMessageTemplate("Hi");

        MockMultipartFile file = new MockMultipartFile("csvFile", "test.csv", "text/csv", "data".getBytes());

        assertThatThrownBy(() -> campaignService.createCampaign(request, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    void createCampaign_throwsWhenCampaignLimitExceeded() {
        tenant.setCampaignsUsed(100);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setTenantId(tenantId);
        request.setName("Over Limit");
        request.setChannel(Channel.EMAIL);
        request.setMessageTemplate("Hi");

        MockMultipartFile file = new MockMultipartFile("csvFile", "test.csv", "text/csv", "data".getBytes());

        assertThatThrownBy(() -> campaignService.createCampaign(request, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("monthly campaign limit");
    }

    @Test
    void campaign_deliveryRateCalculation() {
        Campaign c = new Campaign();
        c.setTotalRecipients(100);
        c.setSentCount(80);
        c.setFailedCount(20);
        assertThat(c.getDeliveryRate()).isEqualTo(80.0);
    }

    @Test
    void campaign_marksCompletedWhenAllProcessed() {
        Campaign c = new Campaign();
        c.setTotalRecipients(3);
        c.markRunning();
        c.incrementSent();
        c.incrementSent();
        c.incrementFailed();
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
    }
}
