-- 审计日志表
-- 用于记录对云主机的高危操作（启动、停止等）
-- 采用AOP切面进行操作审计

CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `operation_type` VARCHAR(20) NOT NULL COMMENT '操作类型: START/STOP/RESTART/TERMINATE',
    `cloud_provider` VARCHAR(20) NOT NULL COMMENT '云厂商',
    `instance_id` VARCHAR(128) NOT NULL COMMENT '实例ID',
    `region` VARCHAR(64) DEFAULT NULL COMMENT '区域',
    `duration_ms` BIGINT NOT NULL COMMENT '操作耗时（毫秒）',
    `success` TINYINT(1) NOT NULL COMMENT '是否执行成功: 1-成功, 0-失败',
    `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息（失败时）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    
    INDEX `idx_operation_type` (`operation_type`),
    INDEX `idx_cloud_provider` (`cloud_provider`),
    INDEX `idx_instance_id` (`instance_id`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_provider_instance` (`cloud_provider`, `instance_id`),
    INDEX `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表 - 记录云主机高危操作';
