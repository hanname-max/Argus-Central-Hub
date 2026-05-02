package com.argus.centralhub.shutdown.rule;

public interface ShutdownRule {

    String getName();

    int getPriority();

    boolean isEnabled();

    RuleResult evaluate(RuleContext context);
}
