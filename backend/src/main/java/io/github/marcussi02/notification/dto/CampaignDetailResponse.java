package io.github.marcussi02.notification.dto;

import io.github.marcussi02.notification.domain.campaign.CampaignStatus;
import io.github.marcussi02.notification.domain.campaign.Channel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CampaignDetailResponse {

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
    private double deliveryRate;
    private int retryQueueSize;
    private double throughputPerMinute;
    private List<TimelinePoint> deliveryTimeline;
    private Map<String, Long> errorBreakdown;

    public static class TimelinePoint {
        private String time;
        private long sent;
        private long failed;

        public TimelinePoint(String time, long sent, long failed) {
            this.time = time;
            this.sent = sent;
            this.failed = failed;
        }

        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public long getSent() { return sent; }
        public void setSent(long sent) { this.sent = sent; }
        public long getFailed() { return failed; }
        public void setFailed(long failed) { this.failed = failed; }
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
    public double getDeliveryRate() { return deliveryRate; }
    public void setDeliveryRate(double deliveryRate) { this.deliveryRate = deliveryRate; }
    public int getRetryQueueSize() { return retryQueueSize; }
    public void setRetryQueueSize(int retryQueueSize) { this.retryQueueSize = retryQueueSize; }
    public double getThroughputPerMinute() { return throughputPerMinute; }
    public void setThroughputPerMinute(double throughputPerMinute) { this.throughputPerMinute = throughputPerMinute; }
    public List<TimelinePoint> getDeliveryTimeline() { return deliveryTimeline; }
    public void setDeliveryTimeline(List<TimelinePoint> deliveryTimeline) { this.deliveryTimeline = deliveryTimeline; }
    public Map<String, Long> getErrorBreakdown() { return errorBreakdown; }
    public void setErrorBreakdown(Map<String, Long> errorBreakdown) { this.errorBreakdown = errorBreakdown; }
}
