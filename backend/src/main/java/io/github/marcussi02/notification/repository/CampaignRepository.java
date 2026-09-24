package io.github.marcussi02.notification.repository;

import io.github.marcussi02.notification.domain.campaign.Campaign;
import io.github.marcussi02.notification.domain.campaign.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    Page<Campaign> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);

    Page<Campaign> findByTenantIdAndStatus(UUID tenantId, CampaignStatus status, Pageable pageable);

    @Query("SELECT c FROM Campaign c WHERE " +
           "(:tenantId IS NULL OR c.tenantId = :tenantId) AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Campaign> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("status") CampaignStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    List<Campaign> findByStatusIn(List<CampaignStatus> statuses);
}
