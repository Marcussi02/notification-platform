package io.github.marcussi02.notification.domain.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(name = "message_template", nullable = false)
    private String messageTemplate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.SCHEDULED;

    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Column(name = "total_recipients")
    private int totalRecipients = 0;

    @Column(name = "sent_count")
    private int sentCount = 0;

    @Column(name = "failed_count")
    private int failedCount = 0;

    @Column(name = "skipped_count")
    private int skippedCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Campaign() {}

    public void markRunning() {
        this.status = CampaignStatus.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementSent() {
        this.sentCount++;
        checkCompletion();
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementFailed() {
        this.failedCount++;
        checkCompletion();
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementSkipped() {
        this.skippedCount++;
        checkCompletion();
        this.updatedAt = LocalDateTime.now();
    }

    private void checkCompletion() {
        int processed = sentCount + failedCount + skippedCount;
        if (totalRecipients > 0 && processed >= totalRecipients) {
            this.status = CampaignStatus.COMPLETED;
        }
    }

    public double getDeliveryRate() {
        if (totalRecipients == 0) return 0.0;
        return (double) sentCount / totalRecipients * 100.0;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public String getMessageTemplate() { return messageTemplate; }
    public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }
    public CampaignStatus getStatus() { return status; }
    public void setStatus(CampaignStatus status) { this.status = status; }
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
    public int getTotalRecipients() { return totalRecipients; }
    public void setTotalRecipients(int totalRecipients) { this.totalRecipients = totalRecipients; }
    public int getSentCount() { return sentCount; }
    public void setSentCount(int sentCount) { this.sentCount = sentCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
