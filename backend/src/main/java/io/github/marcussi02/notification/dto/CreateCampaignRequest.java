package io.github.marcussi02.notification.dto;

import io.github.marcussi02.notification.domain.campaign.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public class CreateCampaignRequest {

    @NotNull(message = "tenantId is required")
    private UUID tenantId;

    @NotBlank(message = "Campaign name is required")
    private String name;

    @NotNull(message = "Channel is required")
    private Channel channel;

    @NotBlank(message = "Message template is required")
    private String messageTemplate;

    private boolean scheduleNow = true;

    private LocalDateTime scheduledTime;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public String getMessageTemplate() { return messageTemplate; }
    public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }
    public boolean isScheduleNow() { return scheduleNow; }
    public void setScheduleNow(boolean scheduleNow) { this.scheduleNow = scheduleNow; }
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
}
