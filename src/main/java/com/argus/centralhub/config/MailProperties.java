package com.argus.centralhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.mail")
public class MailProperties {

    private String host;
    private int port;
    private String username;
    private String password;
    private String protocol;
    private String defaultEncoding;
    private SmtpProperties smtp;
    private String to;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public void setDefaultEncoding(String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

    public SmtpProperties getSmtp() {
        return smtp;
    }

    public void setSmtp(SmtpProperties smtp) {
        this.smtp = smtp;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public static class SmtpProperties {
        private boolean auth;
        private boolean startTlsEnable;
        private boolean sslEnable;
        private String sslTrust;

        public boolean isAuth() {
            return auth;
        }

        public void setAuth(boolean auth) {
            this.auth = auth;
        }

        public boolean isStartTlsEnable() {
            return startTlsEnable;
        }

        public void setStartTlsEnable(boolean startTlsEnable) {
            this.startTlsEnable = startTlsEnable;
        }

        public boolean isSslEnable() {
            return sslEnable;
        }

        public void setSslEnable(boolean sslEnable) {
            this.sslEnable = sslEnable;
        }

        public String getSslTrust() {
            return sslTrust;
        }

        public void setSslTrust(String sslTrust) {
            this.sslTrust = sslTrust;
        }
    }
}
