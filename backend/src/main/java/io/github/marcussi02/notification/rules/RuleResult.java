package io.github.marcussi02.notification.rules;

import java.time.LocalDateTime;

public final class RuleResult {
    private final RuleAction action;
    private final String reason;
    private final LocalDateTime delayUntil;

    public RuleResult(RuleAction action, String reason, LocalDateTime delayUntil) {
        this.action = action;
        this.reason = reason;
        this.delayUntil = delayUntil;
    }

    public RuleAction getAction() { return action; }
    public String getReason() { return reason; }
    public LocalDateTime getDelayUntil() { return delayUntil; }

    public boolean isBlocking() {
        return action != RuleAction.ALLOW;
    }

    public static RuleResult allow() {
        return new RuleResult(RuleAction.ALLOW, null, null);
    }

    public static RuleResult skip(String reason) {
        return new RuleResult(RuleAction.SKIP, reason, null);
    }

    public static RuleResult delay(String reason, LocalDateTime until) {
        return new RuleResult(RuleAction.DELAY, reason, until);
    }

    public static RuleResult reject(String reason) {
        return new RuleResult(RuleAction.REJECT, reason, null);
    }

    public static RuleResult discard(String reason) {
        return new RuleResult(RuleAction.DISCARD, reason, null);
    }
}
