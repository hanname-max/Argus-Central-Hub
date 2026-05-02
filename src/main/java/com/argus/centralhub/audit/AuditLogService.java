package com.argus.centralhub.audit;

import com.argus.centralhub.domain.enums.CloudProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAuditLog(OperationType operationType,
                              CloudProvider cloudProvider,
                              String instanceId,
                              String region,
                              long durationMs,
                              boolean success,
                              String errorMessage) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .operationType(operationType)
                    .cloudProvider(cloudProvider)
                    .instanceId(instanceId)
                    .region(region)
                    .durationMs(durationMs)
                    .success(success)
                    .errorMessage(trimErrorMessage(errorMessage))
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("审计日志已保存: {} - {} - {} - {}",
                    operationType, cloudProvider, instanceId, success ? "成功" : "失败");
        } catch (Exception e) {
            log.error("保存审计日志失败: {} - {} - {}", operationType, cloudProvider, instanceId, e);
        }
    }

    private String trimErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }
}
