package com.argus.centralhub.domain.enums;

public enum InstanceStatus {

    PENDING("pending", "创建中"),
    RUNNING("running", "运行中"),
    STOPPED("stopped", "已停止"),
    STOPPING("stopping", "停止中"),
    STARTING("starting", "启动中"),
    RESTARTING("restarting", "重启中"),
    TERMINATED("terminated", "已销毁"),
    TERMINATING("terminating", "销毁中"),
    ERROR("error", "错误");

    private final String code;
    private final String label;

    InstanceStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static InstanceStatus fromCode(String code) {
        for (InstanceStatus status : InstanceStatus.values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown instance status: " + code);
    }

    public boolean isStable() {
        return this == RUNNING || this == STOPPED || this == TERMINATED || this == ERROR;
    }

    public boolean isTransitional() {
        return !isStable();
    }
}
