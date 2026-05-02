package com.argus.centralhub.shutdown.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CompositeRule extends AbstractShutdownRule {

    private static final Logger log = LoggerFactory.getLogger(CompositeRule.class);

    private final List<ShutdownRule> rules = new ArrayList<>();
    private final Logic logic;

    public enum Logic {
        AND,
        OR
    }

    public CompositeRule(String name, Logic logic) {
        super(name, 50);
        this.logic = logic;
    }

    public CompositeRule addRule(ShutdownRule rule) {
        this.rules.add(rule);
        this.rules.sort(Comparator.comparingInt(ShutdownRule::getPriority));
        return this;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        if (rules.isEmpty()) {
            return RuleResult.noAction(getName(), "复合规则为空，无规则可评估");
        }

        List<String> reasons = new ArrayList<>();
        int matchedCount = 0;

        for (ShutdownRule rule : rules) {
            if (!rule.isEnabled()) {
                log.debug("规则 [{}] 已禁用，跳过", rule.getName());
                continue;
            }

            RuleResult result = rule.evaluate(context);
            reasons.add(String.format("[%s]: %s", rule.getName(), result.getReason()));

            if (result.isShouldShutdown()) {
                matchedCount++;
                if (logic == Logic.OR) {
                    String reason = String.format("OR逻辑 - 第一个匹配规则触发: %s",
                            String.join("; ", reasons));
                    log.debug("复合规则 [{}] OR逻辑触发: {}", getName(), reason);
                    return RuleResult.shutdown(getName(), reason);
                }
            } else {
                if (logic == Logic.AND) {
                    String reason = String.format("AND逻辑 - 规则不满足: %s",
                            String.join("; ", reasons));
                    log.debug("复合规则 [{}] AND逻辑不满足: {}", getName(), reason);
                    return RuleResult.noAction(getName(), reason);
                }
            }
        }

        if (logic == Logic.AND) {
            String reason = String.format("AND逻辑 - 所有规则匹配: %s",
                    String.join("; ", reasons));
            log.debug("复合规则 [{}] AND逻辑全部匹配: {}", getName(), reason);
            return RuleResult.shutdown(getName(), reason);
        } else {
            String reason = String.format("OR逻辑 - 无规则匹配: %s",
                    String.join("; ", reasons));
            return RuleResult.noAction(getName(), reason);
        }
    }

    public List<ShutdownRule> getRules() {
        return new ArrayList<>(rules);
    }

    public Logic getLogic() {
        return logic;
    }
}
