package com.argus.centralhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "shutdown-rule")
public class ShutdownRuleProperties {

    private boolean enabled = false;

    private String cron = "0 0 * * * ?";

    private TimeWindow timeWindow = new TimeWindow();

    private List<TagRule> tagRules = new ArrayList<>();

    public static class TimeWindow {
        private int startHour = 1;
        private int endHour = 6;

        public int getStartHour() {
            return startHour;
        }

        public void setStartHour(int startHour) {
            this.startHour = startHour;
        }

        public int getEndHour() {
            return endHour;
        }

        public void setEndHour(int endHour) {
            this.endHour = endHour;
        }
    }

    public static class TagRule {
        private String key;
        private String value;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public TimeWindow getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(TimeWindow timeWindow) {
        this.timeWindow = timeWindow;
    }

    public List<TagRule> getTagRules() {
        return tagRules;
    }

    public void setTagRules(List<TagRule> tagRules) {
        this.tagRules = tagRules;
    }
}
