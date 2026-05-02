package com.argus.centralhub.shutdown.rule;

import com.argus.centralhub.domain.enums.InstanceStatus;
import com.argus.centralhub.domain.model.CloudInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunningStatusRule extends AbstractShutdownRule {

    private static final Logger log = LoggerFactory.getLogger(RunningStatusRule.class);

    public RunningStatusRule() {
        super("RunningStatusRule", 10);
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        CloudInstance instance = context.getInstance();
        InstanceStatus status = instance.getStatus();

        if (status == InstanceStatus.RUNNING) {
            String reason = "实例状态为运行中 (RUNNING)";
            log.debug("规则 [{}] 匹配: {}", getName(), reason);
            return RuleResult.shutdown(getName(), reason);
        } else {
            String reason = String.format("实例状态非运行中: %s", status.getLabel());
            return RuleResult.noAction(getName(), reason);
        }
    }
}
