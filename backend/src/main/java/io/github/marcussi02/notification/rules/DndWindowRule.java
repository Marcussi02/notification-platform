package io.github.marcussi02.notification.rules;

import io.github.marcussi02.notification.domain.campaign.Channel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Prevents SMS and PUSH sends between 10 PM and 8 AM in the recipient's timezone.
 * EMAIL is exempt (not a "social" channel with same interruption concerns).
 */
@Component
public class DndWindowRule implements NotificationRule {

    private static final LocalTime DND_START = LocalTime.of(22, 0);
    private static final LocalTime DND_END = LocalTime.of(8, 0);

    @Override
    public String name() {
        return "DND Window";
    }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        Channel channel = ctx.getCampaign().getChannel();

        // Email is not subject to quiet hours
        if (channel == Channel.EMAIL) {
            return RuleResult.allow();
        }

        String timezone = ctx.getRecipient().getTimezone();
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (Exception e) {
            zone = ZoneId.of("UTC");
        }

        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalTime localTime = now.toLocalTime();

        if (isDndTime(localTime)) {
            // Delay until 8 AM in the recipient's timezone
            ZonedDateTime next = now.toLocalDate().atTime(DND_END).atZone(zone);
            if (!next.isAfter(now)) {
                next = next.plusDays(1); // after 22:00 -> 08:00 tomorrow, in the recipient's zone
            }
            // Job timestamps use the server's local clock (LocalDateTime.now()),
            // so convert to the server zone rather than UTC to keep comparisons consistent.
            LocalDateTime nextSendTime = next.withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
            return RuleResult.delay("Outside social hours (10pm-8am)", nextSendTime);
        }

        return RuleResult.allow();
    }

    private boolean isDndTime(LocalTime time) {
        return time.isAfter(DND_START) || time.isBefore(DND_END);
    }
}
