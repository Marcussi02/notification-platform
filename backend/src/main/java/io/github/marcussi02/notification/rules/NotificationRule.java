package io.github.marcussi02.notification.rules;

public interface NotificationRule {
    String name();
    RuleResult evaluate(RuleContext context);
}
