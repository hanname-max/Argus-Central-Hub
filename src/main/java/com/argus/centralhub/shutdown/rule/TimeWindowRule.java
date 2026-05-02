package com.argus.centralhub.shutdown.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeWindowRule extends AbstractShutdownRule {

    private static final Logger log = LoggerFactory.getLogger(TimeWindowRule.class);

    private final int startHour;
    private final int endHour;

    public TimeWindowRule(int startHour, int endHour) {
        super("TimeWindowRule", 100);
        this.startHour = startHour;
        this.endHour = endHour;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        int hour = context.getHour();
        
        boolean inWindow = isInTimeWindow(hour);
        
        if (inWindow) {
            String reason = String.format("当前时间 %d:00 在限制窗口 [%d:00 - %d:00] 内",
                    hour, startHour, endHour);
            log.debug("规则 [{}] 触发: {}", getName(), reason);
            return RuleResult.shutdown(getName(), reason);
        } else {
            String reason = String.format("当前时间 %d:00 不在限制窗口 [%d:00 - %d:00] 内",
                    hour, startHour, endHour);
            return RuleResult.noAction(getName(), reason);
        }
    }

    private boolean isInTimeWindow(int hour) {
        if (startHour <= endHour) {
            return hour >= startHour && hour < endHour;
        } else {
            return hour >= startHour || hour < endHour;
        }
    }

    public int getStartHour() {
        return startHour;
    }

    public int getEndHour() {
        return endHour;
    }
}
