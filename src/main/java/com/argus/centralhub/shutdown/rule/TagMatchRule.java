package com.argus.centralhub.shutdown.rule;

import com.argus.centralhub.domain.model.CloudInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TagMatchRule extends AbstractShutdownRule {

    private static final Logger log = LoggerFactory.getLogger(TagMatchRule.class);

    private final String tagKey;
    private final String tagValue;

    public TagMatchRule(String tagKey, String tagValue) {
        super("TagMatchRule_" + tagKey, 200);
        this.tagKey = tagKey;
        this.tagValue = tagValue;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        CloudInstance instance = context.getInstance();
        Map<String, String> tags = instance.getTags();

        if (tags == null || tags.isEmpty()) {
            String reason = "实例无任何标签";
            return RuleResult.noAction(getName(), reason);
        }

        String actualValue = tags.get(tagKey);

        if (actualValue == null) {
            String reason = String.format("实例缺少标签: %s", tagKey);
            return RuleResult.noAction(getName(), reason);
        }

        if (tagValue != null && !tagValue.equals(actualValue)) {
            String reason = String.format("标签值不匹配: 期望 %s=%s, 实际 %s=%s",
                    tagKey, tagValue, tagKey, actualValue);
            return RuleResult.noAction(getName(), reason);
        }

        String reason = String.format("标签匹配: %s=%s", tagKey, actualValue);
        log.debug("规则 [{}] 触发: {}", getName(), reason);
        return RuleResult.shutdown(getName(), reason);
    }

    public String getTagKey() {
        return tagKey;
    }

    public String getTagValue() {
        return tagValue;
    }
}
