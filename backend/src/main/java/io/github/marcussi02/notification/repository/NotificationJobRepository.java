package io.github.marcussi02.notification.repository;

import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationJobRepository extends JpaRepository<NotificationJob, UUID> {

    // Picks up PENDING jobs ready to process
    @Query("SELECT j FROM NotificationJob j WHERE j.status = 'PENDING' ORDER BY j.createdAt ASC")
    List<NotificationJob> findPendingJobs(Pageable pageable);

    // Picks up retry-eligible FAILED jobs where next_retry_at has passed
    @Query("SELECT j FROM NotificationJob j WHERE j.status = 'FAILED' " +
           "AND j.retryCount < :maxRetries " +
           "AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now) " +
           "ORDER BY j.nextRetryAt ASC")
    List<NotificationJob> findRetryEligibleJobs(
            @Param("maxRetries") int maxRetries,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    // Picks up DELAYED jobs whose delay has expired
    @Query("SELECT j FROM NotificationJob j WHERE j.status = 'DELAYED' " +
           "AND j.nextRetryAt <= :now ORDER BY j.nextRetryAt ASC")
    List<NotificationJob> findReadyDelayedJobs(@Param("now") LocalDateTime now, Pageable pageable);

    // Atomic status transition: only one worker can claim a job
    @Transactional
    @Modifying
    @Query("UPDATE NotificationJob j SET j.status = 'PROCESSING', j.updatedAt = :now " +
           "WHERE j.id = :id AND j.status IN ('PENDING', 'FAILED', 'DELAYED')")
    int tryClaimJob(@Param("id") UUID id, @Param("now") LocalDateTime now);

    int countByCampaignIdAndStatus(UUID campaignId, NotificationStatus status);

    int countByCampaignId(UUID campaignId);

    @Query("SELECT COUNT(j) FROM NotificationJob j WHERE j.campaignId = :campaignId " +
           "AND j.status = 'FAILED' AND j.retryCount < :maxRetries")
    int countRetryQueueSize(@Param("campaignId") UUID campaignId, @Param("maxRetries") int maxRetries);

    // Resets FAILED jobs to PENDING for the retry-failures endpoint
    @Transactional
    @Modifying
    @Query("UPDATE NotificationJob j SET j.status = 'PENDING', j.nextRetryAt = NULL, j.updatedAt = :now " +
           "WHERE j.campaignId = :campaignId AND j.status = 'FAILED'")
    int resetFailedJobsForCampaign(@Param("campaignId") UUID campaignId, @Param("now") LocalDateTime now);

    List<NotificationJob> findByCampaignId(UUID campaignId);
}
