package com.argus.centralhub.shutdown;

import com.argus.centralhub.domain.model.CloudInstance;
import com.argus.centralhub.shutdown.rule.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class ShutdownRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(ShutdownRuleEngine.class);

    private final List<ShutdownRule> rules = new ArrayList<>();

    public ShutdownRuleEngine() {
    }

    public void registerRule(ShutdownRule rule) {
        this.rules.add(rule);
        this.rules.sort(Comparator.comparingInt(ShutdownRule::getPriority));
        log.info("已注册关停规则: {} (优先级: {})", rule.getName(), rule.getPriority());
    }

    public void unregisterRule(String ruleName) {
        this.rules.removeIf(r -> r.getName().equals(ruleName));
        log.info("已注销关停规则: {}", ruleName);
    }

    public Optional<RuleResult> evaluate(CloudInstance instance) {
        RuleContext context = new RuleContext(instance);
        return evaluate(context);
    }

    public Optional<RuleResult> evaluate(RuleContext context) {
        if (rules.isEmpty()) {
            log.debug("无可用规则，跳过评估");
            return Optional.empty();
        }

        CloudInstance instance = context.getInstance();
        log.debug("开始评估实例关停规则: {}/{}",
                instance.getCloudProvider().getLabel(),
                instance.getInstanceId());

        for (ShutdownRule rule : rules) {
            if (!rule.isEnabled()) {
                log.debug("规则 [{}] 已禁用，跳过", rule.getName());
                continue;
            }

            RuleResult result = rule.evaluate(context);

            if (result.isShouldShutdown()) {
                log.info("规则 [{}] 触发关停: 实例={}/{}, 原因={}",
                        rule.getName(),
                        instance.getCloudProvider().getLabel(),
                        instance.getInstanceId(),
                        result.getReason());
                return Optional.of(result);
            } else {
                log.debug("规则 [{}] 不满足: {}", rule.getName(), result.getReason());
            }
        }

        log.debug("实例 {}/{} 无匹配的关停规则",
                instance.getCloudProvider().getLabel(),
                instance.getInstanceId());
        return Optional.empty();
    }

    public List<ShutdownRule> getRegisteredRules() {
        return new ArrayList<>(rules);
    }

    public void clearRules() {
        this.rules.clear();
        log.info("已清空所有关停规则");
    }
}
