-- ============================================================
-- P2 高级功能 - 数据库迁移脚本
-- 版本: V2026_15
-- 功能: 打印模板增强、用户安全增强（登录设备）、数据运维（备份/版本）
-- 说明: 本文件与 zw-insight-server/zw-app/src/main/resources/db/migration/V2026_15__p2_advanced.sql 同步
-- ============================================================

-- ============ 1. 打印模板：sys_template 表新增字段 ============

ALTER TABLE sys_template
    ADD COLUMN engine_type VARCHAR(20) DEFAULT 'SIMPLE' COMMENT '渲染引擎：SIMPLE(占位符) / THYMELEAF';

ALTER TABLE sys_template
    ADD COLUMN business_type VARCHAR(50) COMMENT '关联业务类型：CONTRACT / BUDGET / MATERIAL 等';

ALTER TABLE sys_template
    ADD COLUMN data_query_config TEXT COMMENT '数据查询配置JSON(数据源SQL或服务方法)';

-- sys_template 补齐 BaseEntity 映射字段（逻辑删除/乐观锁/创建人）
ALTER TABLE sys_template
    ADD COLUMN created_by BIGINT COMMENT '创建人ID';

ALTER TABLE sys_template
    ADD COLUMN deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除标记（0-未删除 1-已删除）';

ALTER TABLE sys_template
    ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号';

ALTER TABLE sys_template
    ADD INDEX idx_business_type (business_type);

-- ============ 2. 用户安全：登录设备表 ============

CREATE TABLE IF NOT EXISTS sys_login_device (
    id               BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT NOT NULL COMMENT '用户ID',
    device_id        VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
    device_name      VARCHAR(100) COMMENT '设备名称(如 iPhone 15 Pro)',
    os               VARCHAR(50) COMMENT '操作系统(iOS/Android/Windows/MacOS)',
    ip_address       VARCHAR(45) COMMENT '登录IP',
    location         VARCHAR(100) COMMENT 'IP归属地(省份|城市)',
    token            VARCHAR(500) NOT NULL COMMENT '登录Token',
    login_time       DATETIME NOT NULL COMMENT '登录时间',
    last_active_time DATETIME COMMENT '最后活跃时间',
    status           TINYINT DEFAULT 1 COMMENT '状态: 1=活跃 0=已注销',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_token (token(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='登录设备表';

-- ============ 3. 数据运维：备份记录表 ============

CREATE TABLE IF NOT EXISTS sys_backup_record (
    id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    file_name     VARCHAR(200) NOT NULL COMMENT '备份文件名',
    file_size     BIGINT COMMENT '文件大小(bytes)',
    duration_ms   BIGINT COMMENT '备份耗时(毫秒)',
    storage_path  VARCHAR(500) NOT NULL COMMENT 'MinIO存储路径',
    backup_type   VARCHAR(20) DEFAULT 'MANUAL' COMMENT '备份类型: MANUAL/SCHEDULED',
    status        VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS/FAILED',
    error_message TEXT COMMENT '错误信息',
    operator_id   BIGINT COMMENT '操作人ID',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据库备份记录表';

-- 备份恢复操作日志
CREATE TABLE IF NOT EXISTS sys_backup_restore_log (
    id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    backup_id     BIGINT NOT NULL COMMENT '源备份记录ID',
    operator_id   BIGINT NOT NULL COMMENT '操作人ID',
    restore_time  DATETIME NOT NULL COMMENT '恢复时间',
    result        VARCHAR(20) NOT NULL COMMENT '恢复结果: SUCCESS/FAILED',
    error_message TEXT COMMENT '错误信息',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='备份恢复操作日志';

-- 系统版本记录表
CREATE TABLE IF NOT EXISTS sys_version (
    id           BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    version_no   VARCHAR(20) NOT NULL COMMENT '版本号(语义化: x.y.z)',
    release_date DATE NOT NULL COMMENT '发布日期',
    changelog    TEXT COMMENT '更新日志(Markdown格式)',
    operator_id  BIGINT COMMENT '操作人ID',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_no (version_no),
    INDEX idx_release_date (release_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统版本记录表';
