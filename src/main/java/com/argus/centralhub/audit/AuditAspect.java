package com.argus.centralhub.audit;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.strategy.CloudProviderStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("execution(* com.argus.centralhub.strategy..*.startInstance(..)) || " +
            "execution(* com.argus.centralhub.strategy..*.stopInstance(..))")
    public Object auditOperation(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        Object result = null;

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();

        Object target = point.getTarget();
        if (!(target instanceof CloudProviderStrategy strategy)) {
            log.warn("非 CloudProviderStrategy 目标，跳过审计: {}", target.getClass().getName());
            return point.proceed();
        }

        CloudProvider cloudProvider = strategy.getProviderType();
        Object[] args = point.getArgs();
        String instanceId = (String) args[0];
        String region = (String) args[1];

        OperationType operationType = determineOperationType(methodName);

        log.info("开始审计操作: {} - {} - {} - {}",
                operationType.getDescription(), cloudProvider.getLabel(), instanceId, region);

        try {
            result = point.proceed();
            if (result instanceof Boolean booleanResult) {
                success = booleanResult;
                if (!success) {
                    errorMessage = "API 返回操作失败";
                }
            } else {
                success = true;
            }
        } catch (Throwable throwable) {
            success = false;
            errorMessage = throwable.getClass().getName() + ": " + throwable.getMessage();
            log.error("操作执行异常: {} - {} - {}", operationType, cloudProvider, instanceId, throwable);
            throw throwable;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("操作执行完成: {} - {} - {} - 耗时: {}ms - 结果: {}",
                    operationType.getDescription(), cloudProvider.getLabel(), instanceId,
                    durationMs, success ? "成功" : "失败");

            auditLogService.saveAuditLog(
                    operationType,
                    cloudProvider,
                    instanceId,
                    region,
                    durationMs,
                    success,
                    errorMessage
            );
        }

        return result;
    }

    private OperationType determineOperationType(String methodName) {
        return switch (methodName) {
            case "startInstance" -> OperationType.START;
            case "stopInstance" -> OperationType.STOP;
            case "restartInstance" -> OperationType.RESTART;
            case "terminateInstance" -> OperationType.TERMINATE;
            default -> throw new IllegalArgumentException("未知操作类型: " + methodName);
        };
    }
}
