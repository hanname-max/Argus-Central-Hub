package com.argus.centralhub.audit;

import com.argus.centralhub.domain.enums.CloudProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_operation_type", columnList = "operation_type"),
        @Index(name = "idx_cloud_provider", columnList = "cloud_provider"),
        @Index(name = "idx_instance_id", columnList = "instance_id"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_provider_instance", columnList = "cloud_provider, instance_id"),
        @Index(name = "idx_success", columnList = "success")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CloudProvider cloudProvider;

    @Column(nullable = false, length = 128)
    private String instanceId;

    @Column(length = 64)
    private String region;

    @Column(nullable = false)
    private Long durationMs;

    @Column(nullable = false)
    private Boolean success;

    @Column(length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AuditLog(OperationType operationType,
                    CloudProvider cloudProvider,
                    String instanceId,
                    String region,
                    Long durationMs,
                    Boolean success,
                    String errorMessage) {
        this.operationType = operationType;
        this.cloudProvider = cloudProvider;
        this.instanceId = instanceId;
        this.region = region;
        this.durationMs = durationMs;
        this.success = success;
        this.errorMessage = errorMessage;
    }
}
