package com.argus.centralhub.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InstanceStatus {

    PENDING("创建中"),
    RUNNING("运行中"),
    STOPPED("已停止"),
    RESTARTING("重启中"),
    TERMINATED("已销毁");

    private final String label;
}
