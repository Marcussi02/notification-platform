package io.github.marcussi02.notification.dto;

import io.github.marcussi02.notification.domain.campaign.Campaign;
import io.github.marcussi02.notification.domain.campaign.CampaignStatus;
import io.github.marcussi02.notification.domain.campaign.Channel;

import java.time.LocalDateTime;
import java.util.UUID;

public class CampaignResponse {

    private UUID id;
    private UUID tenantId;
    private String name;
    private Channel channel;
    private String messageTemplate;
    private CampaignStatus status;
    private LocalDateTime scheduledTime;
    private int totalRecipients;
    private int sentCount;
    private int failedCount;
    private int skippedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CampaignResponse from(Campaign c) {
        CampaignResponse r = new CampaignResponse();
        r.id = c.getId();
        r.tenantId = c.getTenantId();
        r.name = c.getName();
        r.channel = c.getChannel();
        r.messageTemplate = c.getMessageTemplate();
        r.status = c.getStatus();
        r.scheduledTime = c.getScheduledTime();
        r.totalRecipients = c.getTotalRecipients();
        r.sentCount = c.getSentCount();
        r.failedCount = c.getFailedCount();
        r.skippedCount = c.getSkippedCount();
        r.createdAt = c.getCreatedAt();
        r.updatedAt = c.getUpdatedAt();
        return r;
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
