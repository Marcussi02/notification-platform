package io.github.marcussi02.notification.repository;

import io.github.marcussi02.notification.domain.notification.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

    List<Recipient> findByCampaignId(UUID campaignId);

    int countByCampaignId(UUID campaignId);
}
