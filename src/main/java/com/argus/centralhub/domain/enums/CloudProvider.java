package com.argus.centralhub.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CloudProvider {

    ALIYUN("阿里云"),
    AWS("AWS"),
    TENCENT("腾讯云"),
    HUAWEI("华为云");

    private final String label;
}
