package com.argus.centralhub.webhook;

import com.argus.centralhub.circuitbreaker.annotation.CircuitBreakerProtect;
import com.argus.centralhub.config.DockerNodeProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class DockerDeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DockerDeploymentService.class);

    private final DockerNodeProperties dockerNodeProperties;
    private final Map<String, DockerClient> dockerClientCache = new ConcurrentHashMap<>();

    public DockerDeploymentService(DockerNodeProperties dockerNodeProperties) {
        this.dockerNodeProperties = dockerNodeProperties;
    }

    @CircuitBreakerProtect(
            name = "docker-api-pullImage",
            timeoutMs = 300000,
            maxRetries = 2,
            fallbackMethod = "pullImageFallback"
    )
    public boolean pullImage(String nodeName, String imageName, String registryUsername, String registryPassword) {
        return doPullImage(nodeName, imageName, registryUsername, registryPassword);
    }

    public boolean pullImageFallback(String nodeName, String imageName, String registryUsername, 
                                      String registryPassword, Throwable cause) {
        log.warn("Docker API pullImage 断路器触发，使用 fallback 方法。节点: {}, 镜像: {}, 原因: {}", 
                nodeName, imageName, cause.getMessage());
        return false;
    }

    @CircuitBreakerProtect(
            name = "docker-api-restartContainer",
            timeoutMs = 60000,
            maxRetries = 1,
            fallbackMethod = "restartContainerFallback"
    )
    public boolean restartContainer(String nodeName, String containerIdOrName) {
        return doRestartContainer(nodeName, containerIdOrName);
    }

    public boolean restartContainerFallback(String nodeName, String containerIdOrName, Throwable cause) {
        log.warn("Docker API restartContainer 断路器触发，使用 fallback 方法。节点: {}, 容器: {}, 原因: {}", 
                nodeName, containerIdOrName, cause.getMessage());
        return false;
    }

    @CircuitBreakerProtect(
            name = "docker-api-deployService",
            timeoutMs = 300000,
            maxRetries = 1,
            fallbackMethod = "deployServiceFallback"
    )
    public DeploymentResult deployService(String nodeName, String imageName, String containerName, 
                                           String registryUsername, String registryPassword) {
        log.info("开始部署服务: 节点={}, 镜像={}, 容器={}", nodeName, imageName, containerName);

        DockerNodeProperties.NodeConfig node = findNodeByName(nodeName);
        if (node == null) {
            return DeploymentResult.failure("未找到 Docker 节点: " + nodeName);
        }

        boolean pullSuccess = doPullImage(nodeName, imageName, registryUsername, registryPassword);
        if (!pullSuccess) {
            return DeploymentResult.failure("拉取镜像失败: " + imageName);
        }

        boolean restartSuccess = doRestartContainer(nodeName, containerName);
        if (!restartSuccess) {
            return DeploymentResult.failure("重启容器失败: " + containerName);
        }

        return DeploymentResult.success("服务部署成功: " + containerName);
    }

    public DeploymentResult deployServiceFallback(String nodeName, String imageName, String containerName,
                                                   String registryUsername, String registryPassword, Throwable cause) {
        log.warn("Docker API deployService 断路器触发，使用 fallback 方法。节点: {}, 镜像: {}, 容器: {}, 原因: {}", 
                nodeName, imageName, containerName, cause.getMessage());
        return DeploymentResult.failure("服务部署失败: " + cause.getMessage());
    }

    private boolean doPullImage(String nodeName, String imageName, String registryUsername, String registryPassword) {
        log.info("开始从节点 {} 拉取镜像: {}", nodeName, imageName);

        DockerNodeProperties.NodeConfig node = findNodeByName(nodeName);
        if (node == null) {
            log.error("未找到 Docker 节点: {}", nodeName);
            return false;
        }

        DockerClient dockerClient = getDockerClient(node);

        try {
            String image = imageName;
            String tag = "latest";
            
            if (imageName.contains(":")) {
                int lastColonIndex = imageName.lastIndexOf(":");
                int lastSlashIndex = imageName.lastIndexOf("/");
                
                if (lastColonIndex > lastSlashIndex) {
                    image = imageName.substring(0, lastColonIndex);
                    tag = imageName.substring(lastColonIndex + 1);
                }
            }

            AuthConfig authConfig = null;
            if (registryUsername != null && !registryUsername.isEmpty() && registryPassword != null) {
                authConfig = new AuthConfig()
                        .withUsername(registryUsername)
                        .withPassword(registryPassword);
            }

            log.info("正在拉取镜像: {}:{}", image, tag);
            
            PullImageResultCallback callback = new PullImageResultCallback() {
                @Override
                public void onNext(com.github.dockerjava.api.model.PullResponseItem item) {
                    if (item.getStatus() != null) {
                        log.debug("拉取进度: {} - {}", item.getStatus(), item.getId());
                    }
                    super.onNext(item);
                }
            };

            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image)
                    .withTag(tag);

            if (authConfig != null) {
                pullImageCmd.withAuthConfig(authConfig);
            }

            pullImageCmd.exec(callback)
                    .awaitCompletion(5, TimeUnit.MINUTES);

            log.info("镜像拉取成功: {}:{}", image, tag);
            return true;

        } catch (Exception e) {
            log.error("拉取镜像失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean doRestartContainer(String nodeName, String containerIdOrName) {
        log.info("开始在节点 {} 上重启容器: {}", nodeName, containerIdOrName);

        DockerNodeProperties.NodeConfig node = findNodeByName(nodeName);
        if (node == null) {
            log.error("未找到 Docker 节点: {}", nodeName);
            return false;
        }

        DockerClient dockerClient = getDockerClient(node);

        try {
            String containerId = findContainerId(dockerClient, containerIdOrName);
            if (containerId == null) {
                log.error("未找到容器: {} 在节点: {}", containerIdOrName, nodeName);
                return false;
            }

            log.info("重启容器: {}", containerId);
            dockerClient.restartContainerCmd(containerId).exec();
            log.info("容器重启成功: {}", containerId);
            return true;

        } catch (Exception e) {
            log.error("重启容器失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private DockerNodeProperties.NodeConfig findNodeByName(String nodeName) {
        return dockerNodeProperties.getNodes().stream()
                .filter(node -> node.getName().equals(nodeName))
                .findFirst()
                .orElse(null);
    }

    private String findContainerId(DockerClient dockerClient, String containerIdOrName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(List.of(containerIdOrName))
                    .exec();

            if (!containers.isEmpty()) {
                return containers.get(0).getId();
            }

            containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withIdFilter(List.of(containerIdOrName))
                    .exec();

            if (!containers.isEmpty()) {
                return containers.get(0).getId();
            }

            containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();

            for (Container container : containers) {
                if (container.getId().startsWith(containerIdOrName) ||
                        (container.getNames() != null && 
                         java.util.Arrays.stream(container.getNames())
                                 .anyMatch(name -> name.equals("/" + containerIdOrName) || 
                                                  name.equals(containerIdOrName)))) {
                    return container.getId();
                }
            }

            return null;

        } catch (Exception e) {
            log.error("查找容器 ID 失败: {}", e.getMessage());
            return null;
        }
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

    public record DeploymentResult(boolean success, String message) {
        public static DeploymentResult success(String message) {
            return new DeploymentResult(true, message);
        }

        public static DeploymentResult failure(String message) {
            return new DeploymentResult(false, message);
        }
    }
}
