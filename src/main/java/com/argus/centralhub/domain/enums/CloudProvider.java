package com.argus.centralhub.domain.enums;

public enum CloudProvider {

    ALIYUN("ALIYUN", "阿里云"),
    AWS("AWS", "AWS"),
    TENCENT("TENCENT", "腾讯云"),
    HUAWEI("HUAWEI", "华为云"),
    AZURE("AZURE", "微软云"),
    GOOGLE_CLOUD("GOOGLE_CLOUD", "谷歌云"),
    VOLCENGINE("VOLCENGINE", "火山引擎"),
    JD_CLOUD("JD_CLOUD", "京东云"),
    ORACLE("ORACLE", "Oracle云"),
    OTHER("OTHER", "第三方/其他");

    private final String code;
    private final String label;

    CloudProvider(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
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
