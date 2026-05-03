package com.argus.centralhub.strategy.impl;

import com.argus.centralhub.circuitbreaker.annotation.CircuitBreakerProtect;
import com.argus.centralhub.config.DockerNodeProperties;
import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.enums.InstanceStatus;
import com.argus.centralhub.domain.model.CloudInstance;
import com.argus.centralhub.strategy.AbstractCloudProviderStrategy;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DockerNodeStrategy extends AbstractCloudProviderStrategy {

    private static final Logger log = LoggerFactory.getLogger(DockerNodeStrategy.class);

    private final DockerNodeProperties dockerNodeProperties;
    private final Map<String, DockerClient> dockerClientCache = new ConcurrentHashMap<>();

    public DockerNodeStrategy(DockerNodeProperties dockerNodeProperties) {
        this.dockerNodeProperties = dockerNodeProperties;
    }

    @Override
    public CloudProvider getProviderType() {
        return CloudProvider.DOCKER;
    }

    @Override
    @CircuitBreakerProtect(
            name = "docker-api-listInstances",
            timeoutMs = 30000,
            maxRetries = 2,
            fallbackMethod = "listInstancesFallback"
    )
    public List<CloudInstance> listInstances(String region) {
        log.info("调用 Docker API，获取所有 Docker 节点的容器列表");
        List<CloudInstance> instances = new ArrayList<>();

        for (DockerNodeProperties.NodeConfig node : dockerNodeProperties.getNodes()) {
            try {
                DockerClient dockerClient = getDockerClient(node);
                List<Container> containers = dockerClient.listContainersCmd()
                        .withShowAll(true)
                        .exec();

                for (Container container : containers) {
                    CloudInstance instance = convertToCloudInstance(container, node);
                    instances.add(instance);
                }

                log.info("从 Docker 节点 {} 获取到 {} 个容器", node.getName(), containers.size());
            } catch (Exception e) {
                log.error("从 Docker 节点 {} 获取容器列表失败: {}", node.getName(), e.getMessage(), e);
            }
        }

        return instances;
    }

    public List<CloudInstance> listInstancesFallback(String region, Throwable cause) {
        log.warn("Docker API listInstances 断路器触发，使用 fallback 方法。原因: {}", cause.getMessage());
        return Collections.emptyList();
    }

    @Override
    @CircuitBreakerProtect(
            name = "docker-api-getInstanceById",
            timeoutMs = 15000,
            maxRetries = 2,
            fallbackMethod = "getInstanceByIdFallback"
    )
    public CloudInstance getInstanceById(String instanceId, String region) {
        log.info("调用 Docker API，获取容器 ID 为 {} 的详情", instanceId);

        for (DockerNodeProperties.NodeConfig node : dockerNodeProperties.getNodes()) {
            try {
                DockerClient dockerClient = getDockerClient(node);
                List<Container> containers = dockerClient.listContainersCmd()
                        .withShowAll(true)
                        .withIdFilter(List.of(instanceId))
                        .exec();

                if (!containers.isEmpty()) {
                    return convertToCloudInstance(containers.get(0), node);
                }
            } catch (Exception e) {
                log.error("从 Docker 节点 {} 获取容器详情失败: {}", node.getName(), e.getMessage(), e);
            }
        }

        return null;
    }

    public CloudInstance getInstanceByIdFallback(String instanceId, String region, Throwable cause) {
        log.warn("Docker API getInstanceById 断路器触发，使用 fallback 方法。原因: {}", cause.getMessage());
        return null;
    }

    @Override
    @CircuitBreakerProtect(
            name = "docker-api-startInstance",
            timeoutMs = 20000,
            maxRetries = 1,
            fallbackMethod = "operationFallback"
    )
    public boolean startInstance(String instanceId, String region) {
        log.info("调用 Docker API，启动容器 ID 为 {}", instanceId);

        for (DockerNodeProperties.NodeConfig node : dockerNodeProperties.getNodes()) {
            try {
                DockerClient dockerClient = getDockerClient(node);
                dockerClient.startContainerCmd(instanceId).exec();
                log.info("容器 {} 已在节点 {} 启动", instanceId, node.getName());
                return true;
            } catch (Exception e) {
                log.debug("在节点 {} 启动容器 {} 失败: {}", node.getName(), instanceId, e.getMessage());
            }
        }

        log.error("无法在任何 Docker 节点找到并启动容器 {}", instanceId);
        return false;
    }

    @Override
    @CircuitBreakerProtect(
            name = "docker-api-stopInstance",
            timeoutMs = 20000,
            maxRetries = 1,
            fallbackMethod = "operationFallback"
    )
    public boolean stopInstance(String instanceId, String region) {
        log.info("调用 Docker API，停止容器 ID 为 {}", instanceId);

        for (DockerNodeProperties.NodeConfig node : dockerNodeProperties.getNodes()) {
            try {
                DockerClient dockerClient = getDockerClient(node);
                dockerClient.stopContainerCmd(instanceId).exec();
                log.info("容器 {} 已在节点 {} 停止", instanceId, node.getName());
                return true;
            } catch (Exception e) {
                log.debug("在节点 {} 停止容器 {} 失败: {}", node.getName(), instanceId, e.getMessage());
            }
        }

        log.error("无法在任何 Docker 节点找到并停止容器 {}", instanceId);
        return false;
    }

    @Override
    @CircuitBreakerProtect(
            name = "docker-api-restartInstance",
            timeoutMs = 30000,
            maxRetries = 1,
            fallbackMethod = "operationFallback"
    )
    public boolean restartInstance(String instanceId, String region) {
        log.info("调用 Docker API，重启容器 ID 为 {}", instanceId);

        for (DockerNodeProperties.NodeConfig node : dockerNodeProperties.getNodes()) {
            try {
                DockerClient dockerClient = getDockerClient(node);
                dockerClient.restartContainerCmd(instanceId).exec();
                log.info("容器 {} 已在节点 {} 重启", instanceId, node.getName());
                return true;
            } catch (Exception e) {
                log.debug("在节点 {} 重启容器 {} 失败: {}", node.getName(), instanceId, e.getMessage());
            }
        }

        log.error("无法在任何 Docker 节点找到并重启容器 {}", instanceId);
        return false;
    }

    public boolean operationFallback(String instanceId, String region, Throwable cause) {
        log.warn("Docker API 操作断路器触发，使用 fallback 方法。实例: {}, 原因: {}", instanceId, cause.getMessage());
        return false;
    }

    @PreDestroy
    public void destroy() {
        dockerClientCache.forEach((name, client) -> {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 Docker 客户端 {} 失败: {}", name, e.getMessage());
            }
        });
        dockerClientCache.clear();
    }

    private DockerClient getDockerClient(DockerNodeProperties.NodeConfig node) {
        return dockerClientCache.computeIfAbsent(node.getName(), name -> {
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(node.getDockerHost())
                    .withDockerTlsVerify(node.isTlsEnabled())
                    .build();

            OkDockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .build();

            return DockerClientBuilder.getInstance(config)
                    .withDockerHttpClient(httpClient)
                    .build();
        });
    }

    @Override
    public boolean terminateInstance(String instanceId, String region) {
        log.info("调用 Docker API，删除容器 ID 为 {}", instanceId);

        for (DockerNodeProperties.NodeConfig node : dockerNodeProperties.getNodes()) {
            try {
                DockerClient dockerClient = getDockerClient(node);
                dockerClient.removeContainerCmd(instanceId).withForce(true).exec();
                log.info("容器 {} 已在节点 {} 删除", instanceId, node.getName());
                return true;
            } catch (Exception e) {
                log.debug("在节点 {} 删除容器 {} 失败: {}", node.getName(), instanceId, e.getMessage());
            }
        }

        log.error("无法在任何 Docker 节点找到并删除容器 {}", instanceId);
        return false;
    }

    private CloudInstance convertToCloudInstance(Container container, DockerNodeProperties.NodeConfig node) {
        String containerId = container.getId();
        String shortId = containerId.substring(0, 12);

        String containerName = "unknown";
        if (container.getNames() != null && container.getNames().length > 0) {
            containerName = container.getNames()[0];
            if (containerName.startsWith("/")) {
                containerName = containerName.substring(1);
            }
        }

        String imageName = container.getImage();
        String state = container.getState();

        InstanceStatus instanceStatus = mapDockerStateToInstanceStatus(state);

        CloudInstance instance = CloudInstance.create(
                containerId,
                CloudProvider.DOCKER,
                node.getName(),
                node.getHost(),
                "docker-container",
                instanceStatus
        );

        instance.rename(containerName);

        if (container.getStatus() != null) {
            instance.putTag("full_status", container.getStatus());
        }
        instance.putTag("short_id", shortId);
        instance.putTag("image", imageName);
        instance.putTag("node_name", node.getName());
        instance.putTag("node_host", node.getHost());
        instance.putTag("docker_state", state);

        if (container.getPorts() != null) {
            StringBuilder ports = new StringBuilder();
            for (com.github.dockerjava.api.model.ContainerPort port : container.getPorts()) {
                if (ports.length() > 0) {
                    ports.append(",");
                }
                if (port.getPublicPort() != null) {
                    ports.append(port.getPublicPort()).append("->").append(port.getPrivatePort());
                } else {
                    ports.append(port.getPrivatePort());
                }
            }
            if (ports.length() > 0) {
                instance.putTag("ports", ports.toString());
            }
        }

        if (container.getCreated() != null) {
            instance.putTag("created", String.valueOf(container.getCreated()));
        }

        return instance;
    }

    private InstanceStatus mapDockerStateToInstanceStatus(String dockerState) {
        if (dockerState == null) {
            return InstanceStatus.STOPPED;
        }

        return switch (dockerState.toLowerCase()) {
            case "running" -> InstanceStatus.RUNNING;
            case "paused" -> InstanceStatus.STOPPED;
            case "restarting" -> InstanceStatus.RESTARTING;
            case "removing" -> InstanceStatus.TERMINATING;
            case "exited" -> InstanceStatus.STOPPED;
            case "dead" -> InstanceStatus.TERMINATED;
            case "created" -> InstanceStatus.PENDING;
            default -> InstanceStatus.STOPPED;
        };
    }
}
