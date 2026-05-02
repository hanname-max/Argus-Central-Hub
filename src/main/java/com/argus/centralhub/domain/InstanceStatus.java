package com.argus.centralhub.domain;

import lombok.Getter;

/**
 * 实例状态枚举
 * 采用DDD中的值对象思想，定义实例的固定状态集合
 */
@Getter
public enum InstanceStatus {
    
    PENDING("pending", "创建中"),
    RUNNING("running", "运行中"),
    STOPPED("stopped", "已停止"),
    STOPPING("stopping", "停止中"),
    STARTING("starting", "启动中"),
    REBOOTING("rebooting", "重启中"),
    TERMINATED("terminated", "已销毁"),
    TERMINATING("terminating", "销毁中"),
    ERROR("error", "错误");
    
    private final String code;
    private final String name;
    
    InstanceStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public static InstanceStatus fromCode(String code) {
        for (InstanceStatus status : InstanceStatus.values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown instance status: " + code);
    }
    
    /**
     * 判断实例是否处于稳定状态（非过渡状态）
     */
    public boolean isStable() {
        return this == RUNNING || this == STOPPED || this == TERMINATED || this == ERROR;
    }
    
    /**
     * 判断实例是否处于过渡状态
     */
    public boolean isTransitional() {
        return !isStable();
    }
}
