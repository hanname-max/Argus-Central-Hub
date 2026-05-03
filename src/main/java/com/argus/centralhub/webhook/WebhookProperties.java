package com.argus.centralhub.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "webhook")
public class WebhookProperties {

    private boolean enabled = true;
    private String secret;
    private String signatureHeader = "X-Hub-Signature-256";
    private String signaturePrefix = "sha256=";
    private List<String> allowedIps = new ArrayList<>();
    private int maxPayloadSize = 102400;
    private List<RegistryAuthConfig> registryAuths = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public String getSignaturePrefix() {
        return signaturePrefix;
    }

    public void setSignaturePrefix(String signaturePrefix) {
        this.signaturePrefix = signaturePrefix;
    }

    public List<String> getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    public int getMaxPayloadSize() {
        return maxPayloadSize;
    }

    public void setMaxPayloadSize(int maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    public List<RegistryAuthConfig> getRegistryAuths() {
        return registryAuths;
    }

    public void setRegistryAuths(List<RegistryAuthConfig> registryAuths) {
        this.registryAuths = registryAuths;
    }

    public RegistryAuthConfig findRegistryAuthForImage(String imageName) {
        if (registryAuths == null || registryAuths.isEmpty()) {
            return null;
        }

        for (RegistryAuthConfig auth : registryAuths) {
            if (auth.getImagePrefix() == null || auth.getImagePrefix().isEmpty()) {
                return auth;
            }
            if (imageName.startsWith(auth.getImagePrefix())) {
                return auth;
            }
        }

        return null;
    }

    public static class RegistryAuthConfig {
        private String imagePrefix;
        private String username;
        private String password;

        public String getImagePrefix() {
            return imagePrefix;
        }

        public void setImagePrefix(String imagePrefix) {
            this.imagePrefix = imagePrefix;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
