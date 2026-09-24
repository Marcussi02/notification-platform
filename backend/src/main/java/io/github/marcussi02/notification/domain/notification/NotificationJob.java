package io.github.marcussi02.notification.domain.notification;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_jobs")
public class NotificationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public NotificationJob() {}

    public void markProcessing() {
        this.status = NotificationStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.errorMessage = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String error, int maxRetries) {
        this.retryCount++;
        this.errorMessage = error;
        if (this.retryCount >= maxRetries) {
            this.status = NotificationStatus.FAILED;
            this.nextRetryAt = null;
        } else {
            long delaySeconds = (long) Math.pow(2, retryCount) * 15;
            this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
            this.status = NotificationStatus.FAILED;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void markSkipped(String reason) {
        this.status = NotificationStatus.SKIPPED;
        this.errorMessage = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void markDelayed(LocalDateTime until) {
        this.status = NotificationStatus.DELAYED;
        this.nextRetryAt = until;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isRetryEligible(int maxRetries) {
        return status == NotificationStatus.FAILED
                && retryCount < maxRetries
                && (nextRetryAt == null || LocalDateTime.now().isAfter(nextRetryAt));
    }

    public UUID getId() { return id; }
    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getRecipientId() { return recipientId; }
    public void setRecipientId(UUID recipientId) { this.recipientId = recipientId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
