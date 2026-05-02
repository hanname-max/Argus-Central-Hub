package com.argus.centralhub.shutdown;

import com.argus.centralhub.config.ShutdownRuleProperties;
import com.argus.centralhub.shutdown.rule.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "shutdown-rule",
        name = "enabled",
        havingValue = "true"
)
public class ShutdownRuleConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ShutdownRuleConfigurer.class);

    private final ShutdownRuleEngine ruleEngine;
    private final ShutdownRuleProperties properties;

    public ShutdownRuleConfigurer(
            ShutdownRuleEngine ruleEngine,
            ShutdownRuleProperties properties) {
        this.ruleEngine = ruleEngine;
        this.properties = properties;
    }

    @PostConstruct
    public void configureRules() {
        log.info("开始配置关停规则...");

        ruleEngine.clearRules();

        CompositeRule mainRule = new CompositeRule(
                "DevInstanceNightShutdownRule",
                CompositeRule.Logic.AND
        );

        mainRule.addRule(new RunningStatusRule());

        TimeWindowRule timeWindowRule = new TimeWindowRule(
                properties.getTimeWindow().getStartHour(),
                properties.getTimeWindow().getEndHour()
        );
        mainRule.addRule(timeWindowRule);

        if (properties.getTagRules().isEmpty()) {
            TagMatchRule defaultTagRule = new TagMatchRule("env", "dev");
            mainRule.addRule(defaultTagRule);
            log.info("添加默认标签规则: env=dev");
        } else {
            for (ShutdownRuleProperties.TagRule tagRule : properties.getTagRules()) {
                TagMatchRule matchRule = new TagMatchRule(tagRule.getKey(), tagRule.getValue());
                mainRule.addRule(matchRule);
                log.info("添加标签规则: {}={}", tagRule.getKey(), tagRule.getValue());
            }
        }

        ruleEngine.registerRule(mainRule);

        log.info("关停规则配置完成。时间窗口: {}:00 - {}:00",
                properties.getTimeWindow().getStartHour(),
                properties.getTimeWindow().getEndHour());
    }
}
