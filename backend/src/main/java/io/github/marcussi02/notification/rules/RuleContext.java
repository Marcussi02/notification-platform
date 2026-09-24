package io.github.marcussi02.notification.rules;

import io.github.marcussi02.notification.domain.campaign.Campaign;
import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.Recipient;

public final class RuleContext {
    private final NotificationJob job;
    private final Campaign campaign;
    private final Recipient recipient;

    public RuleContext(NotificationJob job, Campaign campaign, Recipient recipient) {
        this.job = job;
        this.campaign = campaign;
        this.recipient = recipient;
    }

    public NotificationJob getJob() { return job; }
    public Campaign getCampaign() { return campaign; }
    public Recipient getRecipient() { return recipient; }
}
