package com.argus.centralhub.audit;

import lombok.Getter;

@Getter
public enum OperationType {

    START("START", "启动实例"),
    STOP("STOP", "停止实例"),
    RESTART("RESTART", "重启实例"),
    TERMINATE("TERMINATE", "销毁实例");

    private final String code;
    private final String description;

    OperationType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
