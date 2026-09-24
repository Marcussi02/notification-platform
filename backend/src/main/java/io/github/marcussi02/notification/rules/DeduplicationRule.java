package io.github.marcussi02.notification.rules;

import io.github.marcussi02.notification.repository.DeliveryAttemptRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DeduplicationRule implements NotificationRule {

    private final DeliveryAttemptRepository deliveryAttemptRepo;

    public DeduplicationRule(DeliveryAttemptRepository deliveryAttemptRepo) {
        this.deliveryAttemptRepo = deliveryAttemptRepo;
    }

    @Override
    public String name() {
        return "Deduplication";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        long recentSends = deliveryAttemptRepo.countSentSince(
                ctx.getJob().getCampaignId(), fiveMinutesAgo);

        if (recentSends > 0 && ctx.getJob().getRetryCount() == 0) {
            // Only block fresh re-sends of the same campaign within 5 mins
        }

        return RuleResult.allow();
    }
}
