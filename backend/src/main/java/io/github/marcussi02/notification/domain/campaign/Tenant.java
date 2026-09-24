package io.github.marcussi02.notification.domain.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "monthly_campaign_limit")
    private int monthlyCampaignLimit = 100;

    @Column(name = "monthly_message_limit")
    private int monthlyMessageLimit = 1000000;

    @Column(name = "campaigns_used")
    private int campaignsUsed = 0;

    @Column(name = "messages_used")
    private int messagesUsed = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Tenant() {}

    public boolean hasExceededCampaignLimit() {
        return campaignsUsed >= monthlyCampaignLimit;
    }

    public boolean hasExceededMessageLimit(int newMessages) {
        return (messagesUsed + newMessages) > monthlyMessageLimit;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getMonthlyCampaignLimit() { return monthlyCampaignLimit; }
    public void setMonthlyCampaignLimit(int monthlyCampaignLimit) { this.monthlyCampaignLimit = monthlyCampaignLimit; }
    public int getMonthlyMessageLimit() { return monthlyMessageLimit; }
    public void setMonthlyMessageLimit(int monthlyMessageLimit) { this.monthlyMessageLimit = monthlyMessageLimit; }
    public int getCampaignsUsed() { return campaignsUsed; }
    public void setCampaignsUsed(int campaignsUsed) { this.campaignsUsed = campaignsUsed; }
    public int getMessagesUsed() { return messagesUsed; }
    public void setMessagesUsed(int messagesUsed) { this.messagesUsed = messagesUsed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
