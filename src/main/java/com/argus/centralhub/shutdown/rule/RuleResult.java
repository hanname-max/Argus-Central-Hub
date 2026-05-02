package com.argus.centralhub.shutdown.rule;

public class RuleResult {

    private final boolean shouldShutdown;
    private final String ruleName;
    private final String reason;

    private RuleResult(boolean shouldShutdown, String ruleName, String reason) {
        this.shouldShutdown = shouldShutdown;
        this.ruleName = ruleName;
        this.reason = reason;
    }

    public static RuleResult shutdown(String ruleName, String reason) {
        return new RuleResult(true, ruleName, reason);
    }

    public static RuleResult noAction(String ruleName, String reason) {
        return new RuleResult(false, ruleName, reason);
    }

    public boolean isShouldShutdown() {
        return shouldShutdown;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getReason() {
        return reason;
    }
}
