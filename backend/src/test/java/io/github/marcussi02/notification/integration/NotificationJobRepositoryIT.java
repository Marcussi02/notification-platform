package io.github.marcussi02.notification.integration;

import io.github.marcussi02.notification.domain.campaign.Campaign;
import io.github.marcussi02.notification.domain.campaign.Channel;
import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.NotificationStatus;
import io.github.marcussi02.notification.domain.notification.Recipient;
import io.github.marcussi02.notification.repository.*;
import io.github.marcussi02.notification.service.NotificationProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class NotificationJobRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockBean
    NotificationProcessor notificationProcessor;

    @Autowired NotificationJobRepository jobRepository;
    @Autowired RecipientRepository recipientRepository;
    @Autowired CampaignRepository campaignRepository;
    @Autowired DeliveryAttemptRepository deliveryAttemptRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final UUID DEMO_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private UUID campaignId;
    private UUID recipientId;

    @BeforeEach
    void createBaseEntities() {
        Campaign campaign = new Campaign();
        campaign.setTenantId(DEMO_TENANT_ID);
        campaign.setName("IT Campaign");
        campaign.setChannel(Channel.EMAIL);
        campaign.setMessageTemplate("Hello");
        campaign.setTotalRecipients(5);
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();

        Recipient recipient = new Recipient();
        recipient.setCampaignId(campaignId);
        recipient.setTenantId(DEMO_TENANT_ID);
        recipient.setRecipientId("r1");
        recipient.setEmail("r1@test.com");
        recipient = recipientRepository.save(recipient);
        recipientId = recipient.getId();
    }

    @AfterEach
    void cleanup() {
        deliveryAttemptRepository.deleteAll();
        jobRepository.deleteAll();
        recipientRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    @Test
    void flyway_migratesAllExpectedTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' ORDER BY table_name",
                String.class
        );
        assertThat(tables).contains(
                "campaigns", "delivery_attempts", "notification_jobs",
                "recipients", "suppression_list", "tenants"
        );
    }

    @Test
    void flyway_seedsThreeDefaultTenants() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenants", Long.class);
        assertThat(count).isGreaterThanOrEqualTo(3);

        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM tenants WHERE id IN (" +
                "'00000000-0000-0000-0000-000000000001'," +
                "'00000000-0000-0000-0000-000000000002'," +
                "'00000000-0000-0000-0000-000000000003')",
                String.class
        );
        assertThat(names).containsExactlyInAnyOrder("Demo Tenant", "Acme Corp", "Tech Startup");
    }

    @Test
    void tryClaimJob_transitionsStatusToProcessing() {
        NotificationJob job = new NotificationJob();
        job.setCampaignId(campaignId);
        job.setTenantId(DEMO_TENANT_ID);
        job.setRecipientId(recipientId);
        job.setIdempotencyKey(campaignId + ":claim-test");
        job = jobRepository.save(job);

        int updated = jobRepository.tryClaimJob(job.getId(), LocalDateTime.now());

        assertThat(updated).isEqualTo(1);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM notification_jobs WHERE id = ?",
                String.class, job.getId()
        );
        assertThat(status).isEqualTo("PROCESSING");
    }

    @Test
    void tryClaimJob_returnZeroForAlreadyProcessingJob() {
        NotificationJob job = new NotificationJob();
        job.setCampaignId(campaignId);
        job.setTenantId(DEMO_TENANT_ID);
        job.setRecipientId(recipientId);
        job.setIdempotencyKey(campaignId + ":already-claimed");
        job.setStatus(NotificationStatus.PROCESSING);
        job = jobRepository.save(job);

        int updated = jobRepository.tryClaimJob(job.getId(), LocalDateTime.now());

        assertThat(updated).isEqualTo(0);
    }

    @Test
    void idempotencyConstraint_rejectsDuplicateKey() {
        Recipient r2 = new Recipient();
        r2.setCampaignId(campaignId);
        r2.setTenantId(DEMO_TENANT_ID);
        r2.setRecipientId("r2");
        r2.setEmail("r2@test.com");
        r2 = recipientRepository.save(r2);

        NotificationJob job1 = new NotificationJob();
        job1.setCampaignId(campaignId);
        job1.setTenantId(DEMO_TENANT_ID);
        job1.setRecipientId(recipientId);
        job1.setIdempotencyKey("idempotent-key-abc");
        jobRepository.save(job1);

        final Recipient r2Final = r2;
        assertThatThrownBy(() -> {
            NotificationJob job2 = new NotificationJob();
            job2.setCampaignId(campaignId);
            job2.setTenantId(DEMO_TENANT_ID);
            job2.setRecipientId(r2Final.getId());
            job2.setIdempotencyKey("idempotent-key-abc");
            jobRepository.saveAndFlush(job2);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findRetryEligibleJobs_onlyReturnsExpiredBackoffJobs() {
        Recipient r2 = new Recipient();
        r2.setCampaignId(campaignId);
        r2.setTenantId(DEMO_TENANT_ID);
        r2.setRecipientId("r2");
        r2.setEmail("r2@test.com");
        r2 = recipientRepository.save(r2);

        NotificationJob eligible = new NotificationJob();
        eligible.setCampaignId(campaignId);
        eligible.setTenantId(DEMO_TENANT_ID);
        eligible.setRecipientId(recipientId);
        eligible.setIdempotencyKey(campaignId + ":retry-eligible");
        eligible.setStatus(NotificationStatus.FAILED);
        eligible.setRetryCount(1);
        eligible.setNextRetryAt(LocalDateTime.now().minusMinutes(5));
        eligible = jobRepository.save(eligible);

        NotificationJob notYet = new NotificationJob();
        notYet.setCampaignId(campaignId);
        notYet.setTenantId(DEMO_TENANT_ID);
        notYet.setRecipientId(r2.getId());
        notYet.setIdempotencyKey(campaignId + ":retry-not-yet");
        notYet.setStatus(NotificationStatus.FAILED);
        notYet.setRetryCount(1);
        notYet.setNextRetryAt(LocalDateTime.now().plusMinutes(30));
        jobRepository.save(notYet);

        List<NotificationJob> results = jobRepository.findRetryEligibleJobs(
                3, LocalDateTime.now(), Pageable.ofSize(10));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(eligible.getId());
    }

    @Test
    void findRetryEligibleJobs_excludesJobsAtMaxRetries() {
        NotificationJob job = new NotificationJob();
        job.setCampaignId(campaignId);
        job.setTenantId(DEMO_TENANT_ID);
        job.setRecipientId(recipientId);
        job.setIdempotencyKey(campaignId + ":max-retries");
        job.setStatus(NotificationStatus.FAILED);
        job.setRetryCount(3);
        job.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        jobRepository.save(job);

        List<NotificationJob> results = jobRepository.findRetryEligibleJobs(
                3, LocalDateTime.now(), Pageable.ofSize(10));

        assertThat(results).isEmpty();
    }

    @Test
    void resetFailedJobs_resetsOnlyFailedJobsToPending() {
        Recipient r2 = new Recipient();
        r2.setCampaignId(campaignId); r2.setTenantId(DEMO_TENANT_ID);
        r2.setRecipientId("r2"); r2.setEmail("r2@test.com");
        r2 = recipientRepository.save(r2);

        Recipient r3 = new Recipient();
        r3.setCampaignId(campaignId); r3.setTenantId(DEMO_TENANT_ID);
        r3.setRecipientId("r3"); r3.setEmail("r3@test.com");
        r3 = recipientRepository.save(r3);

        NotificationJob f1 = new NotificationJob();
        f1.setCampaignId(campaignId); f1.setTenantId(DEMO_TENANT_ID); f1.setRecipientId(recipientId);
        f1.setIdempotencyKey(campaignId + ":fail-1"); f1.setStatus(NotificationStatus.FAILED); f1.setRetryCount(3);
        jobRepository.save(f1);

        NotificationJob f2 = new NotificationJob();
        f2.setCampaignId(campaignId); f2.setTenantId(DEMO_TENANT_ID); f2.setRecipientId(r2.getId());
        f2.setIdempotencyKey(campaignId + ":fail-2"); f2.setStatus(NotificationStatus.FAILED); f2.setRetryCount(3);
        jobRepository.save(f2);

        NotificationJob s1 = new NotificationJob();
        s1.setCampaignId(campaignId); s1.setTenantId(DEMO_TENANT_ID); s1.setRecipientId(r3.getId());
        s1.setIdempotencyKey(campaignId + ":sent-1"); s1.setStatus(NotificationStatus.SENT);
        jobRepository.save(s1);

        int reset = jobRepository.resetFailedJobsForCampaign(campaignId, LocalDateTime.now());
        assertThat(reset).isEqualTo(2);

        long pendingCount = jobRepository.findByCampaignId(campaignId).stream()
                .filter(j -> j.getStatus() == NotificationStatus.PENDING).count();
        long sentCount = jobRepository.findByCampaignId(campaignId).stream()
                .filter(j -> j.getStatus() == NotificationStatus.SENT).count();
        assertThat(pendingCount).isEqualTo(2);
        assertThat(sentCount).isEqualTo(1);
    }

    @Test
    void notificationJob_lifecycleTransitions() {
        NotificationJob job = new NotificationJob();
        job.setCampaignId(campaignId);
        job.setTenantId(DEMO_TENANT_ID);
        job.setRecipientId(recipientId);
        job.setIdempotencyKey(campaignId + ":lifecycle");
        job = jobRepository.save(job);

        assertThat(job.getStatus()).isEqualTo(NotificationStatus.PENDING);

        job.markFailed("Provider timeout", 3);
        assertThat(job.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getNextRetryAt()).isNotNull();
        assertThat(job.isRetryEligible(3)).isFalse();

        job.markSent();
        assertThat(job.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(job.getErrorMessage()).isNull();
    }

    @Test
    void notificationJob_permanentlyFailedAfterMaxRetries() {
        NotificationJob job = new NotificationJob();
        job.setCampaignId(campaignId);
        job.setTenantId(DEMO_TENANT_ID);
        job.setRecipientId(recipientId);
        job.setIdempotencyKey(campaignId + ":perm-fail");

        job.markFailed("error", 3);
        job.markFailed("error", 3);
        job.markFailed("error", 3);

        assertThat(job.getRetryCount()).isEqualTo(3);
        assertThat(job.isRetryEligible(3)).isFalse();
        assertThat(job.getNextRetryAt()).isNull();
    }
}
