package io.github.marcussi02.notification.repository;

import io.github.marcussi02.notification.domain.notification.SuppressionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SuppressionListRepository extends JpaRepository<SuppressionEntry, UUID> {

    boolean existsByTenantIdAndRecipientIdAndChannel(UUID tenantId, String recipientId, String channel);
}
