package com.argus.centralhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "docker")
public class DockerNodeProperties {

    private List<NodeConfig> nodes = new ArrayList<>();

    public List<NodeConfig> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeConfig> nodes) {
        this.nodes = nodes;
    }

    public static class NodeConfig {
        private String name;
        private String host;
        private int port = 2375;
        private boolean tlsEnabled = false;
        private String certPath;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

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

        public boolean isTlsEnabled() {
            return tlsEnabled;
        }

        public void setTlsEnabled(boolean tlsEnabled) {
            this.tlsEnabled = tlsEnabled;
        }

        public String getCertPath() {
            return certPath;
        }

        public void setCertPath(String certPath) {
            this.certPath = certPath;
        }

        public String getDockerHost() {
            return "tcp://" + host + ":" + port;
        }
    }
}
