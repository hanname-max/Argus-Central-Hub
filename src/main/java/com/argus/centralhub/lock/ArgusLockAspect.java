package com.argus.centralhub.lock;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class ArgusLockAspect {

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(com.argus.centralhub.lock.ArgusLock)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        ArgusLock argusLock = method.getAnnotation(ArgusLock.class);
        
        String lockKey = generateLockKey(point, argusLock);
        RLock lock = getLock(lockKey, argusLock.lockType());
        
        boolean acquired = false;
        try {
            log.debug("尝试获取分布式锁: {}", lockKey);
            acquired = lock.tryLock(argusLock.waitTime(), argusLock.leaseTime(), argusLock.timeUnit());
            
            if (acquired) {
                log.debug("成功获取分布式锁: {}", lockKey);
                return point.proceed();
            } else {
                log.warn("获取分布式锁失败: {}", lockKey);
                throw new RuntimeException("获取分布式锁失败，请稍后重试");
            }
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断: {}", lockKey, e);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                log.debug("释放分布式锁: {}", lockKey);
                lock.unlock();
            }
        }
    }

    private String generateLockKey(ProceedingJoinPoint point, ArgusLock argusLock) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        
        String key = argusLock.value();
        String prefix = argusLock.prefix();
        
        if (!StringUtils.hasText(key)) {
            String className = point.getTarget().getClass().getSimpleName();
            String methodName = method.getName();
            key = className + ":" + methodName;
        }
        
        return prefix + key;
    }

    private RLock getLock(String lockKey, ArgusLock.LockType lockType) {
        return switch (lockType) {
            case FAIR -> redissonClient.getFairLock(lockKey);
            case READ -> redissonClient.getReadWriteLock(lockKey).readLock();
            case WRITE -> redissonClient.getReadWriteLock(lockKey).writeLock();
            default -> redissonClient.getLock(lockKey);
        };
    }
}
