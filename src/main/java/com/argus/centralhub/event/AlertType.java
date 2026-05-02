package com.argus.centralhub.event;

public enum AlertType {

    SERVER_DOWN("server_down", "服务器宕机"),
    CRITICAL_ERROR("critical_error", "严重异常"),
    HIGH_CPU_USAGE("high_cpu_usage", "CPU使用率过高"),
    HIGH_MEMORY_USAGE("high_memory_usage", "内存使用率过高"),
    DISK_FULL("disk_full", "磁盘满"),
    NETWORK_ERROR("network_error", "网络异常");

    private final String code;
    private final String label;

    AlertType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCritical() {
        return this == SERVER_DOWN || this == CRITICAL_ERROR;
    }
}
