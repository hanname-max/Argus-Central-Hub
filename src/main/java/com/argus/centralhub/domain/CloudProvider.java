package com.argus.centralhub.domain;

import lombok.Getter;

/**
 * 云厂商枚举
 * 采用DDD中的值对象思想，定义云厂商的固定集合
 */
@Getter
public enum CloudProvider {
    
    ALIBABA_CLOUD("AlibabaCloud", "阿里云"),
    TENCENT_CLOUD("TencentCloud", "腾讯云"),
    AWS("AWS", "亚马逊云"),
    AZURE("Azure", "微软云"),
    GOOGLE_CLOUD("GoogleCloud", "谷歌云"),
    HUAWEI_CLOUD("HuaweiCloud", "华为云");
    
    private final String code;
    private final String name;
    
    CloudProvider(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public static CloudProvider fromCode(String code) {
        for (CloudProvider provider : CloudProvider.values()) {
            if (provider.getCode().equalsIgnoreCase(code)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown cloud provider: " + code);
    }
}
