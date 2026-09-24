package io.github.marcussi02.notification.rules;

import io.github.marcussi02.notification.repository.SuppressionListRepository;
import org.springframework.stereotype.Component;

@Component
public class GlobalSuppressionRule implements NotificationRule {

    private final SuppressionListRepository suppressionRepo;

    public GlobalSuppressionRule(SuppressionListRepository suppressionRepo) {
        this.suppressionRepo = suppressionRepo;
    }

    @Override
    public String name() {
        return "Global Suppression";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        String channel     = ctx.getCampaign().getChannel().name();
        String recipientId = ctx.getRecipient().getRecipientId();

        if (suppressionRepo.existsByTenantIdAndRecipientIdAndChannel(
                ctx.getJob().getTenantId(), recipientId, channel)) {
            return RuleResult.skip("Recipient has unsubscribed from " + channel);
        }

        return RuleResult.allow();
    }
}
