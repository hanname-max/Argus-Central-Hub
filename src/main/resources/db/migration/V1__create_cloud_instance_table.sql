-- 云实例表
-- 用于统一管理不同云厂商的云实例资源
-- 采用DDD领域模型设计

CREATE TABLE IF NOT EXISTS `cloud_instance` (
    `instance_id` VARCHAR(64) NOT NULL COMMENT '实例ID，唯一标识',
    `cloud_provider` VARCHAR(32) NOT NULL COMMENT '云厂商',
    `region` VARCHAR(64) NOT NULL COMMENT '区域',
    `private_ip` VARCHAR(45) DEFAULT NULL COMMENT '内网IP地址（支持IPv6）',
    `public_ip` VARCHAR(45) DEFAULT NULL COMMENT '外网IP地址（支持IPv6）',
    `status` VARCHAR(32) NOT NULL COMMENT '实例状态',
    `instance_name` VARCHAR(255) DEFAULT NULL COMMENT '实例名称',
    `instance_type` VARCHAR(64) DEFAULT NULL COMMENT '实例规格类型',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '描述信息',
    `version` BIGINT DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`instance_id`),
    
    -- 复合索引：按云厂商+区域查询
    -- 场景：查询指定云厂商指定区域的所有实例
    INDEX `idx_cloud_provider_region` (`cloud_provider`, `region`),
    
    -- 单值索引：按状态查询
    -- 场景：查询所有运行中的实例、查询所有已停止的实例
    INDEX `idx_status` (`status`),
    
    -- 单值索引：按内网IP查询
    -- 场景：通过内网IP查找对应的实例
    INDEX `idx_private_ip` (`private_ip`),
    
    -- 单值索引：按外网IP查询
    -- 场景：通过外网IP查找对应的实例
    INDEX `idx_public_ip` (`public_ip`),
    
    -- 单值索引：按创建时间查询
    -- 场景：按时间范围查询实例
    INDEX `idx_created_at` (`created_at`),
    
    -- 单值索引：按更新时间查询
    -- 场景：查询最近更新的实例
    INDEX `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='云实例表 - 统一管理不同云厂商的云实例资源';
