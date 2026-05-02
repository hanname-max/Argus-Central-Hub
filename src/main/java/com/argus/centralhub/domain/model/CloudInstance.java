package com.argus.centralhub.domain.model;

import com.argus.centralhub.common.BaseEntity;
import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.enums.InstanceStatus;
import com.argus.centralhub.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cloud_instance", indexes = {
        @Index(name = "idx_provider", columnList = "cloud_provider"),
        @Index(name = "idx_region", columnList = "region"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_provider_region", columnList = "cloud_provider, region"),
        @Index(name = "idx_provider_status", columnList = "cloud_provider, status"),
        @Index(name = "idx_private_ip", columnList = "private_ip"),
        @Index(name = "idx_public_ip", columnList = "public_ip")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_instance_provider", columnNames = {"instance_id", "cloud_provider"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CloudInstance extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String instanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CloudProvider cloudProvider;

    @Column(length = 255)
    private String instanceName;

    @Column(length = 128)
    private String instanceType;

    @Column(nullable = false, length = 64)
    private String region;

    @Column(length = 64)
    private String zone;

    @Column(length = 45)
    private String privateIp;

    @Column(length = 45)
    private String publicIp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstanceStatus status;

    @Column(length = 128)
    private String projectId;

    // --- 工厂方法 ---

    public static CloudInstance create(String instanceId, CloudProvider provider,
                                       String region, String zone, String instanceType) {
        CloudInstance instance = new CloudInstance();
        instance.instanceId = instanceId;
        instance.cloudProvider = provider;
        instance.region = region;
        instance.zone = zone;
        instance.instanceType = instanceType;
        instance.status = InstanceStatus.PENDING;
        return instance;
    }

    // --- 领域行为 ---

    public void assignIp(String privateIp, String publicIp) {
        this.privateIp = privateIp;
        this.publicIp = publicIp;
    }

    public void start() {
        if (this.status == InstanceStatus.TERMINATED) {
            throw new BusinessException("已销毁的实例无法启动");
        }
        this.status = InstanceStatus.RUNNING;
    }

    public void stop() {
        if (this.status != InstanceStatus.RUNNING) {
            throw new BusinessException("仅运行中的实例可停止");
        }
        this.status = InstanceStatus.STOPPED;
    }

    public void terminate() {
        if (this.status == InstanceStatus.TERMINATED) {
            throw new BusinessException("实例已销毁");
        }
        this.status = InstanceStatus.TERMINATED;
    }

    public void rename(String newName) {
        this.instanceName = newName;
    }
}
