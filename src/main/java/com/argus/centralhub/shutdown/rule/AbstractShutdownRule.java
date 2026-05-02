package com.argus.centralhub.shutdown.rule;

public abstract class AbstractShutdownRule implements ShutdownRule {

    private final String name;
    private final int priority;
    private boolean enabled = true;

    protected AbstractShutdownRule(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    protected AbstractShutdownRule(String name, int priority, boolean enabled) {
        this.name = name;
        this.priority = priority;
        this.enabled = enabled;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
