package com.argus.centralhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "feishu.bot")
public class FeishuBotProperties {

    private String appId;
    private String appSecret;
    private String verificationToken;
    private String encryptKey;
    private String tenantKey;
    private List<String> allowedOperatorIds = new ArrayList<>();
    private String alertReceiverOpenId;
    private String alertReceiverChatId;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public List<String> getAllowedOperatorIds() {
        return allowedOperatorIds;
    }

    public void setAllowedOperatorIds(List<String> allowedOperatorIds) {
        this.allowedOperatorIds = allowedOperatorIds;
    }

    public String getAlertReceiverOpenId() {
        return alertReceiverOpenId;
    }

    public void setAlertReceiverOpenId(String alertReceiverOpenId) {
        this.alertReceiverOpenId = alertReceiverOpenId;
    }

    public String getAlertReceiverChatId() {
        return alertReceiverChatId;
    }

    public void setAlertReceiverChatId(String alertReceiverChatId) {
        this.alertReceiverChatId = alertReceiverChatId;
    }
}
