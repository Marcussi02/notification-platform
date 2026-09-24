package io.github.marcussi02.notification.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final GlobalSuppressionRule suppressionRule;
    private final DndWindowRule dndWindowRule;
    private final DeduplicationRule deduplicationRule;

    public RuleEngine(GlobalSuppressionRule suppressionRule,
                      DndWindowRule dndWindowRule,
                      DeduplicationRule deduplicationRule) {
        this.suppressionRule   = suppressionRule;
        this.dndWindowRule     = dndWindowRule;
        this.deduplicationRule = deduplicationRule;
    }

    public RuleResult evaluate(RuleContext context) {
        List<NotificationRule> rules = List.of(suppressionRule, dndWindowRule, deduplicationRule);

        for (NotificationRule rule : rules) {
            RuleResult result = rule.evaluate(context);
            if (result.isBlocking()) {
                log.info("Rule [{}] blocked job {} - action={} reason={}",
                        rule.name(),
                        context.getJob().getId(),
                        result.getAction(),
                        result.getReason());
                return result;
            }
        }

        return RuleResult.allow();
    }
}
