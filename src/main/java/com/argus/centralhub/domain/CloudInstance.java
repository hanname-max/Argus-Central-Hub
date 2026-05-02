package com.argus.centralhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 云实例实体
 * 采用DDD中的实体思想，具有唯一标识（instanceId），统一管理不同云厂商的资源
 */
@Entity
@Table(name = "cloud_instance", indexes = {
    @Index(name = "idx_cloud_provider_region", columnList = "cloud_provider, region"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_private_ip", columnList = "private_ip"),
    @Index(name = "idx_public_ip", columnList = "public_ip"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_updated_at", columnList = "updated_at")
})
@Getter
@Setter
public class CloudInstance {
    
    @Id
    @Column(name = "instance_id", length = 64, unique = true, nullable = false)
    private String instanceId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "cloud_provider", length = 32, nullable = false)
    private CloudProvider cloudProvider;
    
    @Column(name = "region", length = 64, nullable = false)
    private String region;
    
    @Column(name = "private_ip", length = 45)
    private String privateIp;
    
    @Column(name = "public_ip", length = 45)
    private String publicIp;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private InstanceStatus status;
    
    @Column(name = "instance_name", length = 255)
    private String instanceName;
    
    @Column(name = "instance_type", length = 64)
    private String instanceType;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * 领域方法：启动实例
     */
    public void start() {
        if (this.status == InstanceStatus.STOPPED) {
            this.status = InstanceStatus.STARTING;
        } else if (this.status != InstanceStatus.RUNNING && this.status != InstanceStatus.STARTING) {
            throw new IllegalStateException("Cannot start instance with status: " + this.status.getCode());
        }
    }
    
    /**
     * 领域方法：停止实例
     */
    public void stop() {
        if (this.status == InstanceStatus.RUNNING) {
            this.status = InstanceStatus.STOPPING;
        } else if (this.status != InstanceStatus.STOPPED && this.status != InstanceStatus.STOPPING) {
            throw new IllegalStateException("Cannot stop instance with status: " + this.status.getCode());
        }
    }
    
    /**
     * 领域方法：重启实例
     */
    public void reboot() {
        if (this.status == InstanceStatus.RUNNING) {
            this.status = InstanceStatus.REBOOTING;
        } else {
            throw new IllegalStateException("Cannot reboot instance with status: " + this.status.getCode());
        }
    }
    
    /**
     * 领域方法：销毁实例
     */
    public void terminate() {
        if (this.status != InstanceStatus.TERMINATED && this.status != InstanceStatus.TERMINATING) {
            this.status = InstanceStatus.TERMINATING;
        } else {
            throw new IllegalStateException("Cannot terminate instance with status: " + this.status.getCode());
        }
    }
    
    /**
     * 领域方法：更新实例状态
     */
    public void updateStatus(InstanceStatus newStatus) {
        this.status = newStatus;
    }
}
