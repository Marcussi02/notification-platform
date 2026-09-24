package io.github.marcussi02.notification.repository;

import io.github.marcussi02.notification.domain.notification.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByNotificationJobId(UUID notificationJobId);

    @Query("SELECT COUNT(a) FROM DeliveryAttempt a " +
           "JOIN NotificationJob j ON a.notificationJobId = j.id " +
           "WHERE j.campaignId = :campaignId AND a.status = 'SENT' AND a.attemptedAt >= :since")
    long countSentSince(@Param("campaignId") UUID campaignId, @Param("since") LocalDateTime since);

    @Query("SELECT a FROM DeliveryAttempt a " +
           "JOIN NotificationJob j ON a.notificationJobId = j.id " +
           "WHERE j.campaignId = :campaignId ORDER BY a.attemptedAt ASC")
    List<DeliveryAttempt> findByCampaignId(@Param("campaignId") UUID campaignId);

    @Query("SELECT a.errorMessage, COUNT(a) FROM DeliveryAttempt a " +
           "JOIN NotificationJob j ON a.notificationJobId = j.id " +
           "WHERE j.campaignId = :campaignId AND a.status = 'FAILED' " +
           "GROUP BY a.errorMessage")
    List<Object[]> getErrorBreakdown(@Param("campaignId") UUID campaignId);
}
