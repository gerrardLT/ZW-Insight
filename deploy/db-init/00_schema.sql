-- ============================================
-- ZW-Insight 系统管理模块建表脚本
-- ============================================

-- 租户表
CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_code VARCHAR(50) NOT NULL COMMENT '租户编码',
    tenant_name VARCHAR(100) NOT NULL COMMENT '租户名称',
    contact_name VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(200) COMMENT '地址',
    tenant_type_id BIGINT COMMENT '租户类型ID',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    expire_date DATE COMMENT '到期日期',
    secret_key VARCHAR(100) COMMENT '密钥',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户表';

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(200) NOT NULL COMMENT '密码',
    real_name VARCHAR(50) COMMENT '真实姓名',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    avatar VARCHAR(500) COMMENT '头像URL',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    org_id BIGINT COMMENT '所属机构ID',
    post_id BIGINT COMMENT '岗位ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- 机构表
CREATE TABLE IF NOT EXISTS sys_org (
    id BIGINT NOT NULL COMMENT '主键ID',
    org_name VARCHAR(100) NOT NULL COMMENT '机构名称',
    org_code VARCHAR(50) COMMENT '机构编码',
    org_type VARCHAR(20) DEFAULT 'DEPARTMENT' COMMENT '机构类型（COMPANY-公司 DEPARTMENT-部门）',
    parent_id BIGINT DEFAULT 0 COMMENT '父机构ID',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    ancestors VARCHAR(500) DEFAULT '0' COMMENT '祖先路径',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机构表';

-- 岗位表
CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT NOT NULL COMMENT '主键ID',
    post_name VARCHAR(100) NOT NULL COMMENT '岗位名称',
    post_code VARCHAR(50) COMMENT '岗位编码',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL COMMENT '主键ID',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    remark VARCHAR(500) COMMENT '备注',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';

-- 菜单表
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL COMMENT '主键ID',
    menu_name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    menu_type VARCHAR(20) NOT NULL COMMENT '菜单类型（DIR-目录 MENU-菜单 BUTTON-按钮）',
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',
    path VARCHAR(200) COMMENT '路由路径',
    component VARCHAR(200) COMMENT '组件路径',
    icon VARCHAR(100) COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    permission VARCHAR(100) COMMENT '权限标识',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    hidden INT DEFAULT 0 COMMENT '是否隐藏（1-隐藏 0-显示）',
    weight VARCHAR(20) DEFAULT 'NORMAL' COMMENT '权重（PLATFORM-平台级 NORMAL-普通）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单表';


-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT NOT NULL COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (id),
    KEY idx_role_id (role_id),
    KEY idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色菜单关联表';

-- 数据字典表
CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGINT NOT NULL COMMENT '主键ID',
    dict_name VARCHAR(100) NOT NULL COMMENT '字典名称',
    dict_code VARCHAR(50) NOT NULL COMMENT '字典编码',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_code (dict_code),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据字典表';

-- 字典值表
CREATE TABLE IF NOT EXISTS sys_dict_item (
    id BIGINT NOT NULL COMMENT '主键ID',
    dict_id BIGINT NOT NULL COMMENT '字典ID',
    parent_id BIGINT DEFAULT 0 COMMENT '父ID（支持树形）',
    label VARCHAR(100) NOT NULL COMMENT '标签',
    value VARCHAR(100) NOT NULL COMMENT '值',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_dict_id (dict_id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典值表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id BIGINT NOT NULL COMMENT '主键ID',
    module VARCHAR(100) COMMENT '模块名称',
    oper_type VARCHAR(20) COMMENT '操作类型（INSERT/UPDATE/DELETE）',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    oper_name VARCHAR(50) COMMENT '操作人姓名',
    oper_account VARCHAR(50) COMMENT '操作人账号',
    oper_time DATETIME COMMENT '操作时间',
    method_name VARCHAR(200) COMMENT '方法名称',
    description VARCHAR(500) COMMENT '操作描述',
    params TEXT COMMENT '请求参数',
    result TEXT COMMENT '返回结果',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_oper_time (oper_time),
    KEY idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';

-- 登录日志表
CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT NOT NULL COMMENT '主键ID',
    login_name VARCHAR(50) COMMENT '登录人姓名',
    login_account VARCHAR(50) COMMENT '登录账号',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    login_time DATETIME COMMENT '登录时间',
    tenant_id BIGINT COMMENT '租户ID',
    PRIMARY KEY (id),
    KEY idx_login_time (login_time),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='登录日志表';

-- ============================================
-- 租户管理扩展表
-- ============================================

-- 租户类型表
CREATE TABLE IF NOT EXISTS sys_tenant_type (
    id BIGINT NOT NULL COMMENT '主键ID',
    type_name VARCHAR(50) NOT NULL COMMENT '类型名称',
    duration_days INT NOT NULL COMMENT '有效期天数',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户类型表';

-- 租户菜单权限关联表
CREATE TABLE IF NOT EXISTS sys_tenant_menu (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户菜单权限表';


-- ============================================
-- 流程引擎模块建表脚本
-- ============================================

-- 业务类型表
CREATE TABLE IF NOT EXISTS wf_business_type (
    id BIGINT NOT NULL COMMENT '主键ID',
    type_name VARCHAR(100) NOT NULL COMMENT '业务名称',
    type_code VARCHAR(50) NOT NULL COMMENT '业务标识',
    parent_id BIGINT DEFAULT 0 COMMENT '父ID',
    sort_order INT DEFAULT 0 COMMENT '排序',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务类型表';

-- 流程定义扩展表
CREATE TABLE IF NOT EXISTS wf_process_def (
    id BIGINT NOT NULL COMMENT '主键ID',
    process_key VARCHAR(100) NOT NULL COMMENT '流程标识',
    process_name VARCHAR(200) NOT NULL COMMENT '流程名称',
    business_type_id BIGINT COMMENT '业务类型ID',
    resource_name VARCHAR(200) COMMENT '资源文件名',
    deployment_id VARCHAR(64) COMMENT 'Flowable部署ID',
    process_definition_id VARCHAR(64) COMMENT 'Flowable流程定义ID',
    version_num INT DEFAULT 1 COMMENT '版本号',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_process_key (process_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义扩展表';

-- 审批记录表
CREATE TABLE IF NOT EXISTS wf_approval_record (
    id BIGINT NOT NULL COMMENT '主键ID',
    process_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    task_id VARCHAR(64) COMMENT '任务ID',
    task_name VARCHAR(200) COMMENT '任务名称',
    assignee VARCHAR(50) COMMENT '处理人',
    assignee_name VARCHAR(50) COMMENT '处理人姓名',
    operation_type VARCHAR(20) COMMENT '操作类型（APPROVE/REJECT/REJECT_TO_START/TERMINATE/TRANSFER/DELEGATE）',
    comment VARCHAR(500) COMMENT '审批意见',
    oper_time DATETIME COMMENT '操作时间',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_process_instance (process_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批记录表';

-- 审批回滚注册表
CREATE TABLE IF NOT EXISTS wf_rollback_action (
    id BIGINT NOT NULL COMMENT '主键ID',
    process_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    target_table VARCHAR(100) NOT NULL COMMENT '目标表名',
    target_field VARCHAR(100) NOT NULL COMMENT '目标字段',
    target_id BIGINT NOT NULL COMMENT '目标记录ID',
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型（ADD/SUBTRACT/SET）',
    oper_value DECIMAL(18,2) COMMENT '操作值',
    original_value DECIMAL(18,2) COMMENT '原始值',
    executed INT DEFAULT 0 COMMENT '是否已回滚（0-未回滚 1-已回滚）',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_process_instance (process_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批回滚注册表';

-- ============ 消息通知模块 ============

CREATE TABLE IF NOT EXISTS msg_announcement (
    id BIGINT NOT NULL COMMENT '主键ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT COMMENT '内容',
    publish_scope VARCHAR(20) DEFAULT 'ALL' COMMENT '发布范围（ALL-全部/DEPT-部门/USER-指定用户）',
    scope_ids VARCHAR(2000) COMMENT '范围ID集合',
    is_top INT DEFAULT 0 COMMENT '是否置顶（0-否 1-是）',
    effective_start DATE COMMENT '生效开始日期',
    effective_end DATE COMMENT '生效结束日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/PUBLISHED-已发布/REVOKED-已撤回）',
    publish_time DATETIME COMMENT '发布时间',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公告表';

CREATE TABLE IF NOT EXISTS msg_notice (
    id BIGINT NOT NULL COMMENT '主键ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT COMMENT '内容',
    target_user_ids VARCHAR(2000) COMMENT '目标用户ID集合',
    notice_type VARCHAR(20) DEFAULT 'SYSTEM' COMMENT '通知类型（SYSTEM-系统通知/BUSINESS-业务通知）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/PUBLISHED-已发布）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知表';

CREATE TABLE IF NOT EXISTS msg_message (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    title VARCHAR(200) COMMENT '标题',
    content VARCHAR(500) COMMENT '内容',
    message_type VARCHAR(20) COMMENT '消息类型（APPROVAL-审批/WARNING-预警/SYSTEM-系统）',
    business_type VARCHAR(50) COMMENT '业务类型',
    business_id BIGINT COMMENT '业务ID',
    is_read INT DEFAULT 0 COMMENT '是否已读（0-未读 1-已读）',
    read_time DATETIME COMMENT '阅读时间',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_user_read (user_id, is_read),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内消息表';

CREATE TABLE IF NOT EXISTS msg_template (
    id BIGINT NOT NULL COMMENT '主键ID',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    business_type VARCHAR(50) COMMENT '业务类型',
    content TEXT COMMENT '模板内容',
    channel_types VARCHAR(100) COMMENT '渠道类型',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息模板表';

CREATE TABLE IF NOT EXISTS msg_user_shortcut (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    menu_id BIGINT COMMENT '菜单ID',
    menu_name VARCHAR(100) COMMENT '菜单名称',
    menu_path VARCHAR(200) COMMENT '菜单路径',
    menu_icon VARCHAR(100) COMMENT '菜单图标',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户快捷入口表';

-- 推送渠道配置表
CREATE TABLE IF NOT EXISTS msg_push_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型编码',
    business_type_name VARCHAR(100) NOT NULL COMMENT '业务类型名称',
    enable_in_app TINYINT(1) DEFAULT 1 COMMENT '是否启用站内信（0-否 1-是）',
    enable_sms TINYINT(1) DEFAULT 0 COMMENT '是否启用短信（0-否 1-是）',
    enable_email TINYINT(1) DEFAULT 0 COMMENT '是否启用邮件（0-否 1-是）',
    enable_app_push TINYINT(1) DEFAULT 0 COMMENT '是否启用APP推送（0-否 1-是）',
    in_app_template_id BIGINT COMMENT '站内信模板ID',
    sms_template_id BIGINT COMMENT '短信模板ID',
    email_template_id BIGINT COMMENT '邮件模板ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_type_tenant (business_type, tenant_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='推送渠道配置表';

-- ============ 基础数据模块 ============

CREATE TABLE IF NOT EXISTS bd_material_category (
    id BIGINT NOT NULL COMMENT '主键ID',
    category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父分类ID',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_parent (parent_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料分类表';

CREATE TABLE IF NOT EXISTS bd_material (
    id BIGINT NOT NULL COMMENT '主键ID',
    material_name VARCHAR(200) NOT NULL COMMENT '材料名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    category_id BIGINT COMMENT '分类ID',
    category_name VARCHAR(100) COMMENT '分类名称',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_category (category_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料字典表';

CREATE TABLE IF NOT EXISTS bd_company (
    id BIGINT NOT NULL COMMENT '主键ID',
    company_name VARCHAR(200) NOT NULL COMMENT '公司名称',
    company_code VARCHAR(50) COMMENT '公司编码',
    legal_person VARCHAR(50) COMMENT '法人',
    registered_capital VARCHAR(50) COMMENT '注册资本',
    address VARCHAR(300) COMMENT '地址',
    contact_name VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    bank_name VARCHAR(100) COMMENT '开户行',
    bank_account VARCHAR(50) COMMENT '银行账号',
    tax_number VARCHAR(50) COMMENT '税号',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='自持公司表';

CREATE TABLE IF NOT EXISTS bd_supplier (
    id BIGINT NOT NULL COMMENT '主键ID',
    supplier_name VARCHAR(200) NOT NULL COMMENT '供应商名称',
    supplier_code VARCHAR(50) COMMENT '供应商编码',
    supplier_type VARCHAR(20) COMMENT '供应商类型（MATERIAL-材料/LABOR-劳务/MACHINE-机械/SUBCONTRACT-分包）',
    contact_name VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(300) COMMENT '地址',
    bank_name VARCHAR(100) COMMENT '开户行',
    bank_account VARCHAR(50) COMMENT '银行账号',
    tax_number VARCHAR(50) COMMENT '税号',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_type (supplier_type),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商表';

CREATE TABLE IF NOT EXISTS bd_owner (
    id BIGINT NOT NULL COMMENT '主键ID',
    owner_name VARCHAR(200) NOT NULL COMMENT '甲方名称',
    owner_code VARCHAR(50) COMMENT '甲方编码',
    contact_name VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(300) COMMENT '地址',
    invoice_title VARCHAR(200) COMMENT '发票抬头',
    taxpayer_no VARCHAR(50) COMMENT '纳税人识别号',
    bank_name VARCHAR(100) COMMENT '开户行',
    bank_account VARCHAR(50) COMMENT '银行账号',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='甲方单位表';

CREATE TABLE IF NOT EXISTS bd_inspection_scheme (
    id BIGINT NOT NULL COMMENT '主键ID',
    scheme_name VARCHAR(200) NOT NULL COMMENT '方案名称',
    scheme_type VARCHAR(20) NOT NULL COMMENT '方案类型（QUALITY-质量/SAFETY-安全）',
    content TEXT COMMENT '检查项内容（JSON格式）',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_type (scheme_type),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检查方案表';


-- ============================================
-- 文件与编号模块建表脚本
-- ============================================

-- 文件信息表
CREATE TABLE IF NOT EXISTS file_info (
    id BIGINT NOT NULL COMMENT '主键ID',
    original_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
    file_name VARCHAR(200) NOT NULL COMMENT '存储文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '文件路径',
    file_size BIGINT COMMENT '文件大小（字节）',
    file_type VARCHAR(100) COMMENT '文件类型（MIME类型）',
    storage_type VARCHAR(20) DEFAULT 'MINIO' COMMENT '存储类型（MINIO/LOCAL）',
    business_type VARCHAR(50) COMMENT '业务类型',
    business_id BIGINT COMMENT '业务ID',
    project_id BIGINT COMMENT '项目ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_business (business_type, business_id),
    KEY idx_project (project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件信息表';

-- 编号规则表
CREATE TABLE IF NOT EXISTS serial_number_rule (
    id BIGINT NOT NULL COMMENT '主键ID',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型',
    rule_prefix VARCHAR(20) NOT NULL COMMENT '规则前缀',
    date_format VARCHAR(20) DEFAULT 'yyyyMMdd' COMMENT '日期格式',
    seq_length INT DEFAULT 4 COMMENT '序号长度',
    reset_period VARCHAR(10) DEFAULT 'MONTH' COMMENT '重置周期（MONTH/YEAR）',
    description VARCHAR(200) COMMENT '描述',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_tenant (business_type, tenant_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='编号规则表';

-- ============================================
-- 项目报备模块建表脚本
-- ============================================

-- 项目表
CREATE TABLE IF NOT EXISTS biz_project (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_code VARCHAR(50) NOT NULL COMMENT '项目编号',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    project_nature VARCHAR(50) COMMENT '项目性质',
    project_type VARCHAR(50) COMMENT '项目类型',
    owner_company_id BIGINT COMMENT '业主单位ID',
    owner_company_name VARCHAR(200) COMMENT '业主单位名称',
    signing_company_id BIGINT COMMENT '签约公司ID',
    signing_company_name VARCHAR(200) COMMENT '签约公司名称',
    project_overview TEXT COMMENT '项目概述',
    project_address VARCHAR(500) COMMENT '项目地址',
    contact_name VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    need_tender INT DEFAULT 0 COMMENT '是否需要招标（1-是 0-否）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/FILED/TENDERING/WON/CONSTRUCTION/COMPLETED/CLOSING/CLOSED）',
    workflow_instance_id VARCHAR(64) DEFAULT NULL COMMENT '结项审批流程实例ID',
    budget_amount DECIMAL(18,2) DEFAULT 0 COMMENT '预算金额',
    contract_amount DECIMAL(18,2) DEFAULT 0 COMMENT '合同金额',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    settlement_amount DECIMAL(18,2) DEFAULT 0 COMMENT '结算金额',
    total_income DECIMAL(18,2) DEFAULT 0 COMMENT '总收入',
    total_expense DECIMAL(18,2) DEFAULT 0 COMMENT '总支出',
    total_other_payment DECIMAL(18,2) DEFAULT 0 COMMENT '其他总支付',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (project_code),
    KEY idx_status (status),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目表';

-- 项目成员表
CREATE TABLE IF NOT EXISTS biz_project_member (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    user_name VARCHAR(50) COMMENT '用户名称',
    role_type VARCHAR(50) COMMENT '角色类型',
    join_date DATE COMMENT '加入日期',
    status INT DEFAULT 1 COMMENT '状态（1-在岗 0-离岗）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_user (user_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目成员表';

-- ============================================
-- 施工合同模块建表脚本
-- ============================================

-- 施工合同表
CREATE TABLE IF NOT EXISTS biz_construction_contract (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    project_name VARCHAR(200) COMMENT '项目名称',
    contract_code VARCHAR(50) NOT NULL COMMENT '合同编号',
    contract_type VARCHAR(20) DEFAULT 'REGISTER' COMMENT '合同类型（REGISTER-登记/CHANGE-变更/SUPPLEMENT-补充）',
    parent_contract_id BIGINT COMMENT '父合同ID',
    party_a_name VARCHAR(200) COMMENT '甲方名称',
    party_a_id BIGINT COMMENT '甲方ID',
    signing_date DATE COMMENT '签订日期',
    start_date DATE COMMENT '开工日期',
    end_date DATE COMMENT '竣工日期',
    contract_amount DECIMAL(18,2) COMMENT '合同金额',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    amount_without_tax DECIMAL(18,2) COMMENT '不含税金额',
    tax_amount DECIMAL(18,2) COMMENT '税额',
    cumulative_change_amount DECIMAL(18,2) DEFAULT 0 COMMENT '累计变更金额',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    cumulative_invoice_amount DECIMAL(18,2) DEFAULT 0 COMMENT '累计开票金额',
    cumulative_received_amount DECIMAL(18,2) DEFAULT 0 COMMENT '累计收款金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/EFFECTIVE/SETTLED/CLOSED）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_code (contract_code),
    KEY idx_project (project_id),
    KEY idx_status (status),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='施工合同表';

-- 合同明细表
CREATE TABLE IF NOT EXISTS biz_contract_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    contract_table VARCHAR(50) COMMENT '合同表名（区分合同来源）',
    item_name VARCHAR(200) COMMENT '项目名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) COMMENT '数量',
    unit_price DECIMAL(18,4) COMMENT '单价',
    total_price DECIMAL(18,2) COMMENT '合计金额',
    remark VARCHAR(500) COMMENT '备注',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同明细表';

-- 变更签证表
CREATE TABLE IF NOT EXISTS biz_change_visa (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    change_type VARCHAR(30) COMMENT '变更类型（DESIGN_CHANGE-设计变更/SITE_VISA-现场签证/QUANTITY_CHANGE-工程量变更）',
    change_reason TEXT COMMENT '变更原因',
    change_content TEXT COMMENT '变更内容',
    change_amount DECIMAL(18,2) COMMENT '变更金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='变更签证表';

-- 其他合同表
CREATE TABLE IF NOT EXISTS biz_other_contract (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_name VARCHAR(200) NOT NULL COMMENT '合同名称',
    contract_category VARCHAR(20) COMMENT '合同分类（OTHER_INCOME-其他收入/OTHER_EXPENSE-其他支出）',
    party_a_name VARCHAR(200) COMMENT '甲方名称',
    party_b_name VARCHAR(200) COMMENT '乙方名称',
    contract_amount DECIMAL(18,2) COMMENT '合同金额',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    amount_without_tax DECIMAL(18,2) COMMENT '不含税金额',
    tax_amount DECIMAL(18,2) COMMENT '税额',
    signing_date DATE COMMENT '签订日期',
    payment_terms VARCHAR(500) COMMENT '付款条款',
    cooperation_content TEXT COMMENT '合作内容',
    cumulative_invoice DECIMAL(18,2) DEFAULT 0 COMMENT '累计开票',
    cumulative_received DECIMAL(18,2) DEFAULT 0 COMMENT '累计收款',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算',
    cumulative_paid DECIMAL(18,2) DEFAULT 0 COMMENT '累计付款',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/EFFECTIVE）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_category (contract_category),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='其他合同表';


-- ============================================
-- 产值与结算模块建表脚本
-- ============================================

-- 产值报告表
CREATE TABLE IF NOT EXISTS biz_output_report (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    report_period VARCHAR(20) COMMENT '报告期间（如2024-01）',
    current_output DECIMAL(18,2) DEFAULT 0 COMMENT '本期产值',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    confirm_date DATE COMMENT '确认日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产值报告表';

-- 工程量清单表
CREATE TABLE IF NOT EXISTS biz_quantity_list (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    item_name VARCHAR(200) COMMENT '项目名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) COMMENT '数量',
    unit_price DECIMAL(18,4) COMMENT '单价',
    amount DECIMAL(18,2) COMMENT '金额',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工程量清单表';

-- 竣工结算表
CREATE TABLE IF NOT EXISTS biz_final_settlement (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    settlement_amount DECIMAL(18,2) COMMENT '结算金额',
    settlement_date DATE COMMENT '结算日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='竣工结算表';

-- ============================================
-- 预算管理模块建表脚本
-- ============================================

-- 预算表
CREATE TABLE IF NOT EXISTS biz_budget (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    budget_type VARCHAR(20) DEFAULT 'ORIGINAL' COMMENT '预算类型（ORIGINAL-原始预算/CHANGE-变更预算）',
    change_seq INT DEFAULT 0 COMMENT '变更序号（原始预算为0）',
    total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '预算总金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_type (budget_type),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预算表';

-- 预算明细表
CREATE TABLE IF NOT EXISTS biz_budget_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    budget_id BIGINT NOT NULL COMMENT '预算ID',
    cost_category VARCHAR(30) COMMENT '费用类别（MATERIAL/LABOR/MACHINE/SUBCONTRACT/INDIRECT/OTHER）',
    cost_subcategory VARCHAR(100) COMMENT '费用子类',
    item_name VARCHAR(200) COMMENT '项目名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    budget_quantity DECIMAL(18,4) COMMENT '预算数量',
    budget_unit_price DECIMAL(18,4) COMMENT '预算单价',
    budget_total_price DECIMAL(18,2) COMMENT '预算合计',
    remark VARCHAR(500) COMMENT '备注',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_budget (budget_id),
    KEY idx_category (cost_category),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预算明细表';

-- 预算管控配置表
CREATE TABLE IF NOT EXISTS biz_budget_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT COMMENT '项目ID（null表示全局配置）',
    control_mode VARCHAR(20) DEFAULT 'WARN' COMMENT '管控模式（FORBID-禁止超支/WARN-警告超支）',
    description VARCHAR(500) COMMENT '描述',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预算管控配置表';

-- 费用子类表
CREATE TABLE IF NOT EXISTS biz_cost_subcategory (
    id BIGINT NOT NULL COMMENT '主键ID',
    cost_category VARCHAR(30) NOT NULL COMMENT '费用类别（MATERIAL/LABOR/MACHINE/SUBCONTRACT/INDIRECT/OTHER）',
    subcategory_name VARCHAR(100) NOT NULL COMMENT '子类名称',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_category (cost_category),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='费用子类表';

-- ============================================
-- 采购管理模块建表脚本
-- ============================================

-- 采购合同表
CREATE TABLE IF NOT EXISTS biz_purchase_contract (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_code VARCHAR(50) NOT NULL COMMENT '合同编号',
    party_a_id BIGINT COMMENT '甲方ID',
    party_a_name VARCHAR(200) COMMENT '甲方名称',
    party_b_id BIGINT COMMENT '乙方ID（供应商）',
    party_b_name VARCHAR(200) COMMENT '乙方名称',
    signing_date DATE COMMENT '签订日期',
    budget_id BIGINT COMMENT '关联预算ID',
    contract_amount DECIMAL(18,2) COMMENT '合同金额',
    payment_terms VARCHAR(500) COMMENT '付款条款',
    cumulative_inbound DECIMAL(18,2) DEFAULT 0 COMMENT '累计入库金额',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算金额',
    cumulative_paid DECIMAL(18,2) DEFAULT 0 COMMENT '累计付款金额',
    cumulative_invoice_received DECIMAL(18,2) DEFAULT 0 COMMENT '累计收票金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/EFFECTIVE-生效）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_code (contract_code),
    KEY idx_project (project_id),
    KEY idx_status (status),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购合同表';

-- 采购合同明细表
CREATE TABLE IF NOT EXISTS biz_purchase_contract_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    material_name VARCHAR(200) COMMENT '材料名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) COMMENT '数量',
    contract_quantity DECIMAL(18,4) COMMENT '合同数量',
    contract_price DECIMAL(18,4) COMMENT '合同单价',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购合同明细表';

-- 采购结算表
CREATE TABLE IF NOT EXISTS biz_purchase_settlement (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    settlement_amount DECIMAL(18,2) COMMENT '本次结算金额',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购结算表';

-- ============================================
-- 三方比价模块建表脚本
-- ============================================

-- 询价单表
CREATE TABLE IF NOT EXISTS biz_inquiry (
    id BIGINT NOT NULL COMMENT '主键ID',
    title VARCHAR(200) NOT NULL COMMENT '询价标题',
    invite_mode VARCHAR(20) DEFAULT 'PUBLIC' COMMENT '邀请模式（PUBLIC-公开/DIRECTED-定向）',
    bid_mode VARCHAR(20) DEFAULT 'LOWEST' COMMENT '定标模式（LOWEST-最低价/COMPREHENSIVE-综合评审）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/PUBLISHED/QUOTED/AWARDED/ANNOUNCED）',
    publish_time DATETIME COMMENT '发布时间',
    deadline DATETIME DEFAULT NULL COMMENT '报价截止时间',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='询价单表';

-- 询价供应商关联表
CREATE TABLE IF NOT EXISTS biz_inquiry_supplier (
    id BIGINT NOT NULL COMMENT '主键ID',
    inquiry_id BIGINT NOT NULL COMMENT '询价单ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_inquiry (inquiry_id),
    KEY idx_supplier (supplier_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='询价供应商关联表';

-- 询价物料明细表
CREATE TABLE IF NOT EXISTS biz_inquiry_item (
    id BIGINT NOT NULL COMMENT '主键ID',
    inquiry_id BIGINT NOT NULL COMMENT '询价单ID',
    material_name VARCHAR(200) COMMENT '材料名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) COMMENT '数量',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_inquiry (inquiry_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='询价物料明细表';

-- 报价单表
CREATE TABLE IF NOT EXISTS biz_quotation (
    id BIGINT NOT NULL COMMENT '主键ID',
    inquiry_id BIGINT NOT NULL COMMENT '询价单ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    total_amount DECIMAL(18,2) COMMENT '报价总额',
    submit_time DATETIME COMMENT '提交时间',
    status VARCHAR(20) DEFAULT 'SUBMITTED' COMMENT '状态（SUBMITTED-已提交）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_inquiry (inquiry_id),
    KEY idx_supplier (supplier_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报价单表';

-- 报价明细表
CREATE TABLE IF NOT EXISTS biz_quotation_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    quotation_id BIGINT NOT NULL COMMENT '报价单ID',
    inquiry_item_id BIGINT COMMENT '询价物料ID',
    unit_price DECIMAL(18,4) COMMENT '单价',
    total_price DECIMAL(18,2) COMMENT '合计金额',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_quotation (quotation_id),
    KEY idx_inquiry_item (inquiry_item_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报价明细表';

-- 定标结果表
CREATE TABLE IF NOT EXISTS biz_bid_result (
    id BIGINT NOT NULL COMMENT '主键ID',
    inquiry_id BIGINT NOT NULL COMMENT '询价单ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    ranking INT COMMENT '排名',
    total_amount DECIMAL(18,2) COMMENT '报价总额',
    is_winner INT DEFAULT 0 COMMENT '是否中标（0-否 1-是）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_inquiry (inquiry_id),
    KEY idx_supplier (supplier_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定标结果表';


-- ============================================
-- 劳务管理模块建表脚本
-- ============================================

-- 劳务合同表
CREATE TABLE IF NOT EXISTS biz_labor_contract (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_code VARCHAR(50) COMMENT '合同编号',
    party_a_name VARCHAR(200) COMMENT '甲方名称',
    party_b_id BIGINT COMMENT '乙方ID（供应商）',
    party_b_name VARCHAR(200) COMMENT '乙方名称',
    signing_date DATE COMMENT '签订日期',
    budget_id BIGINT COMMENT '关联预算ID',
    contract_amount DECIMAL(18,2) COMMENT '合同金额',
    payment_terms VARCHAR(500) COMMENT '付款条款',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算金额',
    cumulative_paid DECIMAL(18,2) DEFAULT 0 COMMENT '累计付款金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/EFFECTIVE-生效）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_status (status),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='劳务合同表';

-- 劳务产值报告表
CREATE TABLE IF NOT EXISTS biz_labor_output_report (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    current_output DECIMAL(18,2) DEFAULT 0 COMMENT '本期产值',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='劳务产值报告表';

-- 劳务结算表
CREATE TABLE IF NOT EXISTS biz_labor_settlement (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    settlement_amount DECIMAL(18,2) COMMENT '本次结算金额',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='劳务结算表';

-- 劳务奖罚表
CREATE TABLE IF NOT EXISTS biz_labor_reward_punish (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    rp_type VARCHAR(20) COMMENT '奖罚类型（REWARD-奖励/PUNISH-处罚）',
    amount DECIMAL(18,2) COMMENT '金额',
    reason VARCHAR(500) COMMENT '原因',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='劳务奖罚表';

-- 班组表
CREATE TABLE IF NOT EXISTS biz_team (
    id BIGINT NOT NULL COMMENT '主键ID',
    team_name VARCHAR(100) NOT NULL COMMENT '班组名称',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    leader_name VARCHAR(50) COMMENT '班组长姓名',
    leader_phone VARCHAR(20) COMMENT '班组长电话',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='班组表';

-- 劳务花名册表
CREATE TABLE IF NOT EXISTS biz_labor_roster (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    team_id BIGINT NOT NULL COMMENT '班组ID',
    worker_name VARCHAR(50) NOT NULL COMMENT '工人姓名',
    id_card VARCHAR(20) COMMENT '身份证号',
    phone VARCHAR(20) COMMENT '联系电话',
    worker_type VARCHAR(20) COMMENT '用工类型（FIXED-固定/TEMPORARY-临时）',
    status INT DEFAULT 1 COMMENT '状态（1-在岗 0-离岗）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_team (team_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='劳务花名册表';

-- 派工单表
CREATE TABLE IF NOT EXISTS biz_work_order (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    team_id BIGINT COMMENT '班组ID',
    worker_id BIGINT COMMENT '工人ID',
    worker_name VARCHAR(50) COMMENT '工人姓名',
    work_date DATE COMMENT '工作日期',
    hours DECIMAL(10,2) COMMENT '工时',
    hourly_rate DECIMAL(10,2) COMMENT '时薪',
    overtime DECIMAL(10,2) COMMENT '加班工时',
    overtime_rate DECIMAL(10,2) COMMENT '加班费率',
    total_amount DECIMAL(18,2) COMMENT '合计金额',
    order_type VARCHAR(20) COMMENT '用工类型（FIXED-固定/TEMPORARY-临时）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_team (team_id),
    KEY idx_worker (worker_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='派工单表';

-- 劳务工资单表
CREATE TABLE IF NOT EXISTS biz_labor_payroll (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    team_id BIGINT COMMENT '班组ID',
    period_start DATE COMMENT '周期开始日期',
    period_end DATE COMMENT '周期结束日期',
    total_settlement DECIMAL(18,2) COMMENT '结算总额',
    total_paid DECIMAL(18,2) DEFAULT 0 COMMENT '已付总额',
    unpaid DECIMAL(18,2) COMMENT '未付金额',
    order_type VARCHAR(20) COMMENT '用工类型（FIXED-固定/TEMPORARY-临时）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批/SETTLED-已结算）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_team (team_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='劳务工资单表';


-- ============================================
-- 材料库存模块建表脚本
-- ============================================

-- 材料入库单表
CREATE TABLE IF NOT EXISTS biz_material_inbound (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT COMMENT '采购合同ID',
    inbound_code VARCHAR(50) COMMENT '入库单号',
    inbound_date DATE COMMENT '入库日期',
    total_amount DECIMAL(18,2) COMMENT '入库总金额',
    direct_outbound INT DEFAULT 0 COMMENT '直接出库（0-否 1-是）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料入库单表';

-- 材料入库明细表
CREATE TABLE IF NOT EXISTS biz_material_inbound_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    inbound_id BIGINT NOT NULL COMMENT '入库单ID',
    material_name VARCHAR(200) COMMENT '材料名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    unit_price DECIMAL(18,4) COMMENT '单价',
    quantity DECIMAL(18,4) COMMENT '数量',
    total_price DECIMAL(18,2) COMMENT '金额',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_inbound (inbound_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料入库明细表';

-- 材料出库单表
CREATE TABLE IF NOT EXISTS biz_material_outbound (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    outbound_type VARCHAR(20) COMMENT '出库类型（PICK-领料/RETURN-退货）',
    outbound_date DATE COMMENT '出库日期',
    operator_name VARCHAR(50) COMMENT '操作人姓名',
    contract_id BIGINT COMMENT '合同ID（退货时关联）',
    return_type VARCHAR(20) COMMENT '退货类型（RETURN_ONLY-仅退货/RETURN_REFUND-退货退款）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料出库单表';

-- 材料出库明细表
CREATE TABLE IF NOT EXISTS biz_material_outbound_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    outbound_id BIGINT NOT NULL COMMENT '出库单ID',
    material_name VARCHAR(200) COMMENT '材料名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) COMMENT '数量',
    unit_price DECIMAL(18,4) COMMENT '单价',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_outbound (outbound_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料出库明细表';

-- 材料调拨单表
CREATE TABLE IF NOT EXISTS biz_material_transfer (
    id BIGINT NOT NULL COMMENT '主键ID',
    from_project_id BIGINT NOT NULL COMMENT '调出项目ID',
    to_project_id BIGINT NOT NULL COMMENT '调入项目ID',
    transfer_date DATE COMMENT '调拨日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_from_project (from_project_id),
    KEY idx_to_project (to_project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料调拨单表';

-- 材料调拨明细表
CREATE TABLE IF NOT EXISTS biz_material_transfer_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    transfer_id BIGINT NOT NULL COMMENT '调拨单ID',
    material_name VARCHAR(200) COMMENT '材料名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) COMMENT '数量',
    unit_price DECIMAL(18,4) COMMENT '单价',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_transfer (transfer_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料调拨明细表';

-- 材料盘点单表
CREATE TABLE IF NOT EXISTS biz_material_inventory (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    inventory_date DATE COMMENT '盘点日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料盘点单表';

-- 材料盘点明细表（盘盈亏差异流水）
CREATE TABLE IF NOT EXISTS biz_material_inventory_detail (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT COMMENT '租户ID',
    inventory_id BIGINT NOT NULL COMMENT '盘点单ID',
    stock_id BIGINT NOT NULL COMMENT '库存ID',
    material_name VARCHAR(200) COMMENT '材料名称（快照）',
    specification VARCHAR(100) COMMENT '规格（快照）',
    unit VARCHAR(20) COMMENT '单位（快照）',
    book_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '账面数量（登记时库存快照）',
    actual_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '实盘数量',
    diff_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '差异数量（实盘-账面，正数盘盈/负数盘亏）',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_inventory (inventory_id),
    KEY idx_stock (stock_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料盘点明细表（盘盈亏差异流水）';

-- 项目材料库存表
CREATE TABLE IF NOT EXISTS biz_project_material_stock (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    material_name VARCHAR(200) COMMENT '材料名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    stock_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '库存数量',
    avg_unit_price DECIMAL(18,4) COMMENT '加权平均单价',
    total_inbound DECIMAL(18,4) DEFAULT 0 COMMENT '累计入库数量',
    total_outbound DECIMAL(18,4) DEFAULT 0 COMMENT '累计出库数量',
    total_return DECIMAL(18,4) DEFAULT 0 COMMENT '累计退货数量',
    total_transfer_in DECIMAL(18,4) DEFAULT 0 COMMENT '累计调入数量',
    total_transfer_out DECIMAL(18,4) DEFAULT 0 COMMENT '累计调出数量',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_material (material_name, specification),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目材料库存表';


-- ============================================
-- 机械管理模块建表脚本
-- ============================================

-- 机械合同表
CREATE TABLE IF NOT EXISTS biz_machine_contract (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_code VARCHAR(50) COMMENT '合同编号',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    signing_date DATE COMMENT '签订日期',
    budget_id BIGINT COMMENT '关联预算ID',
    contract_amount DECIMAL(18,2) COMMENT '合同金额',
    payment_terms VARCHAR(500) COMMENT '付款条款',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算金额',
    cumulative_paid DECIMAL(18,2) DEFAULT 0 COMMENT '累计付款金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/EFFECTIVE-生效）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_status (status),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械合同表';

-- 机械使用记录表
CREATE TABLE IF NOT EXISTS biz_machine_usage_record (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT COMMENT '合同ID',
    usage_quantity DECIMAL(18,2) COMMENT '使用量',
    oil_amount DECIMAL(18,2) COMMENT '油耗金额',
    record_date DATE COMMENT '记录日期',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械使用记录表';

-- 机械结算表
CREATE TABLE IF NOT EXISTS biz_machine_settlement (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    settlement_amount DECIMAL(18,2) COMMENT '本次结算金额',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械结算表';

-- 机械台账表
CREATE TABLE IF NOT EXISTS biz_machine_ledger (
    id BIGINT NOT NULL COMMENT '主键ID',
    machine_name VARCHAR(200) NOT NULL COMMENT '机械名称',
    machine_code VARCHAR(50) COMMENT '机械编号',
    machine_type VARCHAR(50) COMMENT '机械类型',
    model VARCHAR(100) COMMENT '规格型号',
    purchase_date DATE COMMENT '购置日期',
    status VARCHAR(20) DEFAULT 'REGISTERED' COMMENT '状态（REGISTERED-已登记/IN_FIELD-在场/OUT_FIELD-退场）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械台账表';

-- 机械进退场记录表
CREATE TABLE IF NOT EXISTS biz_machine_entry (
    id BIGINT NOT NULL COMMENT '主键ID',
    machine_id BIGINT NOT NULL COMMENT '机械ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    entry_date DATE COMMENT '进退场日期',
    entry_type VARCHAR(10) COMMENT '进退场类型（IN-进场/OUT-退场）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_machine (machine_id),
    KEY idx_project (project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械进退场记录表';

-- 机械工作日志表
CREATE TABLE IF NOT EXISTS biz_machine_work_log (
    id BIGINT NOT NULL COMMENT '主键ID',
    machine_id BIGINT NOT NULL COMMENT '机械ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    work_date DATE COMMENT '工作日期',
    shift_count DECIMAL(10,2) COMMENT '台班数',
    work_quantity DECIMAL(18,2) COMMENT '工作量',
    oil_consumption DECIMAL(18,2) COMMENT '油耗',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/SETTLED-已结算）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_machine (machine_id),
    KEY idx_project (project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械工作日志表';

-- 机械加油记录表
CREATE TABLE IF NOT EXISTS biz_machine_oil_record (
    id BIGINT NOT NULL COMMENT '主键ID',
    machine_id BIGINT NOT NULL COMMENT '机械ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    oil_date DATE COMMENT '加油日期',
    oil_quantity DECIMAL(18,2) COMMENT '加油量',
    oil_amount DECIMAL(18,2) COMMENT '加油金额',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_machine (machine_id),
    KEY idx_project (project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械加油记录表';

-- 机械维修记录表
CREATE TABLE IF NOT EXISTS biz_machine_repair (
    id BIGINT NOT NULL COMMENT '主键ID',
    machine_id BIGINT NOT NULL COMMENT '机械ID',
    project_id BIGINT COMMENT '项目ID',
    fault_description VARCHAR(500) COMMENT '故障描述',
    report_date DATE COMMENT '报修日期',
    repair_person VARCHAR(50) COMMENT '维修人',
    repair_date DATE COMMENT '维修日期',
    repair_cost DECIMAL(18,2) COMMENT '维修费用',
    repair_status VARCHAR(20) DEFAULT 'REPORTED' COMMENT '维修状态（REPORTED-已报修/DISPATCHED-已派工/REPAIRING-维修中/COMPLETED-已完成）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_machine (machine_id),
    KEY idx_project (project_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械维修记录表';


-- ============================================
-- 分包管理模块建表脚本
-- ============================================

-- 分包合同表
CREATE TABLE IF NOT EXISTS biz_subcontract (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_code VARCHAR(50) COMMENT '合同编号',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    signing_date DATE COMMENT '签订日期',
    budget_id BIGINT COMMENT '关联预算ID',
    contract_amount DECIMAL(18,2) COMMENT '合同金额',
    payment_terms VARCHAR(500) COMMENT '付款条款',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算金额',
    cumulative_paid DECIMAL(18,2) DEFAULT 0 COMMENT '累计付款金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/EFFECTIVE-生效）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_status (status),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分包合同表';

-- 分包产值报告表
CREATE TABLE IF NOT EXISTS biz_subcontract_output_report (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    current_output DECIMAL(18,2) DEFAULT 0 COMMENT '本期产值',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分包产值报告表';

-- 分包结算表
CREATE TABLE IF NOT EXISTS biz_subcontract_settlement (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    settlement_amount DECIMAL(18,2) COMMENT '本次结算金额',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分包结算表';

-- 分包奖罚表
CREATE TABLE IF NOT EXISTS biz_subcontract_reward_punish (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    rp_type VARCHAR(20) COMMENT '奖罚类型（REWARD-奖励/PUNISH-处罚）',
    amount DECIMAL(18,2) COMMENT '金额',
    reason VARCHAR(500) COMMENT '原因',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分包奖罚表';


-- ============================================
-- Phase 9: 现场管理模块建表脚本
-- ============================================

-- 进度计划表
CREATE TABLE IF NOT EXISTS biz_schedule_plan (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    task_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父任务ID（0表示顶级）',
    plan_start_date DATE COMMENT '计划开始日期',
    plan_end_date DATE COMMENT '计划结束日期',
    actual_start_date DATE COMMENT '实际开始日期',
    actual_end_date DATE COMMENT '实际结束日期',
    progress DECIMAL(5,2) DEFAULT 0 COMMENT '进度（百分比）',
    task_status VARCHAR(20) DEFAULT 'NOT_STARTED' COMMENT '任务状态（NOT_STARTED/IN_PROGRESS/COMPLETED/DELAYED）',
    task_detail TEXT COMMENT '任务详情',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='进度计划表';

-- 进度反馈表
CREATE TABLE IF NOT EXISTS biz_schedule_feedback (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    plan_id BIGINT NOT NULL COMMENT '计划任务ID',
    actual_start_date DATE COMMENT '实际开始日期',
    actual_end_date DATE COMMENT '实际结束日期',
    task_status VARCHAR(20) COMMENT '任务状态',
    progress DECIMAL(5,2) COMMENT '完成进度',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='进度反馈表';

-- 施工日志表
CREATE TABLE IF NOT EXISTS biz_construction_log (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    log_date DATE NOT NULL COMMENT '日志日期',
    weather VARCHAR(50) COMMENT '天气',
    temperature VARCHAR(50) COMMENT '温度',
    wind VARCHAR(50) COMMENT '风力',
    worker_count INT COMMENT '施工人数',
    production_record TEXT COMMENT '生产记录',
    technical_record TEXT COMMENT '技术记录',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_log_date (log_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='施工日志表';

-- 质量安全检查表
CREATE TABLE IF NOT EXISTS biz_inspection (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    inspection_type VARCHAR(20) NOT NULL COMMENT '检查类型（QUALITY/SAFETY）',
    scheme_id BIGINT COMMENT '检查方案ID',
    inspection_content TEXT COMMENT '检查内容',
    has_problem INT DEFAULT 0 COMMENT '是否存在问题（0-无 1-有）',
    problem_description TEXT COMMENT '问题描述',
    responsible_person_id BIGINT COMMENT '整改责任人ID',
    rectification_deadline DATE COMMENT '整改期限',
    rectification_date DATE COMMENT '整改完成日期',
    rectification_status VARCHAR(20) COMMENT '整改状态（PENDING/SUBMITTED/APPROVED/REJECTED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_inspection_type (inspection_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质量安全检查表';

-- 整改记录表
CREATE TABLE IF NOT EXISTS biz_rectification (
    id BIGINT NOT NULL COMMENT '主键ID',
    inspection_id BIGINT NOT NULL COMMENT '检查记录ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    rectification_content TEXT COMMENT '整改内容',
    status VARCHAR(20) DEFAULT 'SUBMITTED' COMMENT '状态（SUBMITTED/APPROVED/REJECTED）',
    workflow_instance_id VARCHAR(100) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_inspection_id (inspection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='整改记录表';

-- 竣工验收表
CREATE TABLE IF NOT EXISTS biz_completion_acceptance (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    acceptance_date DATE COMMENT '验收日期',
    acceptance_report TEXT COMMENT '验收报告',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    workflow_instance_id VARCHAR(100) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='竣工验收表';


-- ============================================
-- Phase 9: 财务管理模块建表脚本
-- ============================================

-- 开票申请表
CREATE TABLE IF NOT EXISTS biz_invoice_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    invoice_type VARCHAR(20) COMMENT '发票类型（SPECIAL/NORMAL）',
    invoice_amount DECIMAL(18,2) NOT NULL COMMENT '开票金额',
    invoice_title VARCHAR(200) COMMENT '发票抬头',
    taxpayer_id VARCHAR(50) COMMENT '纳税人识别号',
    bank_account VARCHAR(50) COMMENT '银行账号',
    bank_name VARCHAR(100) COMMENT '银行名称',
    contract_amount_snapshot DECIMAL(18,2) COMMENT '合同金额快照',
    settlement_amount_snapshot DECIMAL(18,2) COMMENT '结算金额快照',
    historical_invoiced_snapshot DECIMAL(18,2) COMMENT '历史已开票金额快照',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    workflow_instance_id VARCHAR(100) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_contract_id (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='开票申请表';

-- 收款登记表
CREATE TABLE IF NOT EXISTS biz_payment_received (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT COMMENT '合同ID',
    receive_date DATE COMMENT '收款日期',
    receive_amount DECIMAL(18,2) NOT NULL COMMENT '收款金额',
    receiver VARCHAR(100) COMMENT '收款人',
    receive_type VARCHAR(50) COMMENT '收款方式',
    receive_bank_account VARCHAR(50) COMMENT '收款银行账号',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收款登记表';

-- 收票登记表
CREATE TABLE IF NOT EXISTS biz_invoice_received (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT COMMENT '合同ID',
    contract_category VARCHAR(50) COMMENT '合同分类',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    invoice_amount DECIMAL(18,2) NOT NULL COMMENT '发票金额',
    invoice_date DATE COMMENT '收票日期（业务日期，用于封账校验）',
    status VARCHAR(20) DEFAULT 'APPROVED' COMMENT '状态（APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收票登记表';

-- 付款申请表
CREATE TABLE IF NOT EXISTS biz_payment_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    contract_category VARCHAR(50) COMMENT '合同分类',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    payment_amount DECIMAL(18,2) NOT NULL COMMENT '付款金额',
    payment_date DATE COMMENT '付款日期',
    cumulative_settlement_snapshot DECIMAL(18,2) COMMENT '累计结算金额快照',
    unpaid_amount_snapshot DECIMAL(18,2) COMMENT '未付金额快照',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    workflow_instance_id VARCHAR(100) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_contract_id (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='付款申请表';

-- 其他支付表
CREATE TABLE IF NOT EXISTS biz_other_payment (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    payer_name VARCHAR(100) COMMENT '付款人',
    payment_date DATE COMMENT '付款日期',
    payment_amount DECIMAL(18,2) NOT NULL COMMENT '付款金额',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='其他支付表';

-- 项目报销表
CREATE TABLE IF NOT EXISTS biz_project_reimbursement (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    total_amount DECIMAL(18,2) NOT NULL COMMENT '报销总金额',
    reimbursement_date DATE COMMENT '报销日期（业务日期，用于封账校验）',
    offset_reserve INT DEFAULT 0 COMMENT '是否冲抵备用金（0-否 1-是）',
    reserve_apply_id BIGINT COMMENT '备用金申请ID',
    offset_amount DECIMAL(18,2) COMMENT '冲抵金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    workflow_instance_id VARCHAR(100) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目报销表';

-- 报销明细表
CREATE TABLE IF NOT EXISTS biz_reimbursement_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    reimbursement_id BIGINT NOT NULL COMMENT '报销单ID',
    expense_type VARCHAR(50) COMMENT '费用类型',
    amount DECIMAL(18,2) NOT NULL COMMENT '金额',
    remark VARCHAR(500) COMMENT '备注',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_reimbursement_id (reimbursement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报销明细表';

-- 备用金申请表
CREATE TABLE IF NOT EXISTS biz_reserve_fund_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    applicant VARCHAR(100) COMMENT '申请人',
    apply_date DATE COMMENT '申请日期',
    apply_amount DECIMAL(18,2) NOT NULL COMMENT '申请金额',
    returned_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已归还金额',
    offset_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已冲抵金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    workflow_instance_id VARCHAR(100) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='备用金申请表';

-- 备用金归还表
CREATE TABLE IF NOT EXISTS biz_reserve_fund_return (
    id BIGINT NOT NULL COMMENT '主键ID',
    reserve_apply_id BIGINT NOT NULL COMMENT '备用金申请ID',
    return_amount DECIMAL(18,2) NOT NULL COMMENT '归还金额',
    return_date DATE COMMENT '归还日期',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_reserve_apply_id (reserve_apply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='备用金归还表';

-- 个人报销表
CREATE TABLE IF NOT EXISTS biz_personal_reimbursement (
    id BIGINT NOT NULL COMMENT '主键ID',
    total_amount DECIMAL(18,2) NOT NULL COMMENT '报销总金额',
    reimbursement_date DATE COMMENT '报销日期（业务日期，用于封账校验）',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    workflow_instance_id VARCHAR(100) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='个人报销表';


-- ============================================
-- Phase 10: 投标管理模块建表脚本
-- ============================================

-- 投标登记表
CREATE TABLE IF NOT EXISTS biz_tender_register (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    owner_company VARCHAR(200) COMMENT '业主单位',
    bid_method VARCHAR(50) COMMENT '招标方式',
    register_method VARCHAR(50) COMMENT '报名方式',
    register_date DATE COMMENT '报名日期',
    open_date DATE COMMENT '开标日期',
    tender_method VARCHAR(50) COMMENT '投标方式',
    deposit_amount DECIMAL(18,2) COMMENT '保证金金额',
    status VARCHAR(20) DEFAULT 'REGISTERED' COMMENT '状态（REGISTERED/WON/LOST）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='投标登记表';

-- 投标任务表
CREATE TABLE IF NOT EXISTS biz_tender_task (
    id BIGINT NOT NULL COMMENT '主键ID',
    register_id BIGINT NOT NULL COMMENT '投标登记ID',
    task_type VARCHAR(20) COMMENT '任务类型（COMMERCIAL/TECHNICAL/ECONOMIC/SEAL）',
    responsible_person VARCHAR(100) COMMENT '负责人',
    deadline DATE COMMENT '截止日期',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态（PENDING/COMPLETED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_register_id (register_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='投标任务表';

-- 投标费用表
CREATE TABLE IF NOT EXISTS biz_tender_fee (
    id BIGINT NOT NULL COMMENT '主键ID',
    register_id BIGINT NOT NULL COMMENT '投标登记ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    fee_type VARCHAR(50) COMMENT '费用类型',
    fee_amount DECIMAL(18,2) NOT NULL COMMENT '费用金额',
    payment_date DATE COMMENT '付款日期',
    receipt_file VARCHAR(500) COMMENT '回单文件',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/PAID）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_register_id (register_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='投标费用表';

-- 保证金申请表
CREATE TABLE IF NOT EXISTS biz_deposit_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    register_id BIGINT NOT NULL COMMENT '投标登记ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    deposit_amount DECIMAL(18,2) NOT NULL COMMENT '保证金金额',
    payment_date DATE COMMENT '付款日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/PAID）',
    workflow_instance_id VARCHAR(100) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_register_id (register_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='保证金申请表';

-- 开标记录表
CREATE TABLE IF NOT EXISTS biz_open_bid_record (
    id BIGINT NOT NULL COMMENT '主键ID',
    register_id BIGINT NOT NULL COMMENT '投标登记ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    is_won INT DEFAULT 0 COMMENT '是否中标（0-未中标 1-中标）',
    win_info VARCHAR(500) COMMENT '中标信息',
    status VARCHAR(20) COMMENT '状态',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_register_id (register_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='开标记录表';

-- 保证金退还表
CREATE TABLE IF NOT EXISTS biz_deposit_return (
    id BIGINT NOT NULL COMMENT '主键ID',
    deposit_apply_id BIGINT NOT NULL COMMENT '保证金申请ID',
    return_amount DECIMAL(18,2) NOT NULL COMMENT '退还金额',
    return_date DATE COMMENT '退还日期',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_deposit_apply_id (deposit_apply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='保证金退还表';

-- 人员证书表
CREATE TABLE IF NOT EXISTS biz_person_certificate (
    id BIGINT NOT NULL COMMENT '主键ID',
    person_name VARCHAR(100) COMMENT '人员姓名',
    certificate_type VARCHAR(50) COMMENT '证书类型',
    certificate_no VARCHAR(100) COMMENT '证书编号',
    issue_date DATE COMMENT '发证日期',
    expire_date DATE COMMENT '到期日期',
    status INT DEFAULT 1 COMMENT '状态（1-有效 0-无效）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='人员证书表';

-- 企业证书表
CREATE TABLE IF NOT EXISTS biz_company_certificate (
    id BIGINT NOT NULL COMMENT '主键ID',
    certificate_name VARCHAR(200) COMMENT '证书名称',
    certificate_type VARCHAR(50) COMMENT '证书类型',
    certificate_no VARCHAR(100) COMMENT '证书编号',
    issue_date DATE COMMENT '发证日期',
    expire_date DATE COMMENT '到期日期',
    status INT DEFAULT 1 COMMENT '状态（1-有效 0-无效）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业证书表';


-- ============================================
-- Phase 10: 行政人事模块建表脚本
-- ============================================

-- 入职申请表
CREATE TABLE IF NOT EXISTS biz_entry_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    username VARCHAR(50) COMMENT '用户名',
    real_name VARCHAR(50) COMMENT '真实姓名',
    gender VARCHAR(10) COMMENT '性别',
    birth_date DATE COMMENT '出生日期',
    id_card VARCHAR(20) COMMENT '身份证号',
    phone VARCHAR(20) COMMENT '手机号',
    entry_date DATE COMMENT '入职日期',
    org_id BIGINT COMMENT '部门ID',
    post_id BIGINT COMMENT '岗位ID',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    workflow_instance_id VARCHAR(100) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='入职申请表';

-- 转正申请表
CREATE TABLE IF NOT EXISTS biz_regular_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT COMMENT '用户ID',
    user_name VARCHAR(50) COMMENT '用户姓名',
    trial_end_date DATE COMMENT '试用期结束日期',
    regular_date DATE COMMENT '转正日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='转正申请表';

-- 调动申请表
CREATE TABLE IF NOT EXISTS biz_transfer_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT COMMENT '用户ID',
    user_name VARCHAR(50) COMMENT '用户姓名',
    transfer_date DATE COMMENT '调动日期',
    from_org_id BIGINT COMMENT '原部门ID',
    from_post_id BIGINT COMMENT '原岗位ID',
    to_org_id BIGINT COMMENT '新部门ID',
    to_post_id BIGINT COMMENT '新岗位ID',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='调动申请表';

-- 离职申请表
CREATE TABLE IF NOT EXISTS biz_resign_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT COMMENT '用户ID',
    user_name VARCHAR(50) COMMENT '用户姓名',
    resign_date DATE COMMENT '离职日期',
    handover_person VARCHAR(50) COMMENT '交接人',
    is_handover INT DEFAULT 0 COMMENT '是否已交接（0-否 1-是）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='离职申请表';

-- 用印申请表
CREATE TABLE IF NOT EXISTS biz_seal_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    applicant VARCHAR(100) COMMENT '申请人',
    seal_type VARCHAR(50) COMMENT '印章类型',
    is_carry_out INT DEFAULT 0 COMMENT '是否外带（0-否 1-是）',
    use_time DATETIME COMMENT '使用时间',
    reason VARCHAR(500) COMMENT '事由',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用印申请表';

-- 办公用品表
CREATE TABLE IF NOT EXISTS biz_office_supply (
    id BIGINT NOT NULL COMMENT '主键ID',
    category_name VARCHAR(100) COMMENT '分类名称',
    supply_name VARCHAR(200) NOT NULL COMMENT '用品名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    image_url VARCHAR(500) COMMENT '图片URL',
    stock_quantity INT DEFAULT 0 COMMENT '库存数量',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='办公用品表';

-- 办公用品出入库表
CREATE TABLE IF NOT EXISTS biz_office_supply_in_out (
    id BIGINT NOT NULL COMMENT '主键ID',
    supply_id BIGINT NOT NULL COMMENT '用品ID',
    io_type VARCHAR(10) NOT NULL COMMENT '出入库类型（IN/OUT）',
    quantity INT NOT NULL COMMENT '数量',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_supply_id (supply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='办公用品出入库表';

-- 车辆表
CREATE TABLE IF NOT EXISTS biz_vehicle (
    id BIGINT NOT NULL COMMENT '主键ID',
    plate_number VARCHAR(20) NOT NULL COMMENT '车牌号',
    vehicle_type VARCHAR(50) COMMENT '车辆类型',
    vehicle_status VARCHAR(20) DEFAULT 'IDLE' COMMENT '车辆状态（IDLE/IN_USE）',
    image_url VARCHAR(500) COMMENT '图片URL',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车辆表';

-- 车辆申请表
CREATE TABLE IF NOT EXISTS biz_vehicle_apply (
    id BIGINT NOT NULL COMMENT '主键ID',
    vehicle_id BIGINT NOT NULL COMMENT '车辆ID',
    plate_number VARCHAR(20) COMMENT '车牌号',
    use_time DATETIME COMMENT '使用时间',
    purpose VARCHAR(500) COMMENT '用途',
    expected_return_time DATETIME COMMENT '预计归还时间',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_vehicle_id (vehicle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车辆申请表';

-- 车辆维保表
CREATE TABLE IF NOT EXISTS biz_vehicle_maintenance (
    id BIGINT NOT NULL COMMENT '主键ID',
    vehicle_id BIGINT NOT NULL COMMENT '车辆ID',
    plate_number VARCHAR(20) COMMENT '车牌号',
    maint_type VARCHAR(20) COMMENT '维保类型（REPAIR/INSURANCE）',
    maint_date DATE COMMENT '维保日期',
    maint_cost DECIMAL(18,2) COMMENT '维保费用',
    content TEXT COMMENT '维保内容',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_vehicle_id (vehicle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车辆维保表';

-- ============================================
-- Task 26: 质保金管理建表脚本
-- ============================================

-- 质保金表
CREATE TABLE IF NOT EXISTS biz_retention_money (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT COMMENT '项目ID',
    contract_id BIGINT COMMENT '合同ID',
    retention_rate DECIMAL(8,4) COMMENT '质保金比例',
    retention_amount DECIMAL(18,2) NOT NULL COMMENT '质保金金额',
    retention_period INT COMMENT '质保期（月）',
    start_date DATE COMMENT '质保开始日期',
    expire_date DATE COMMENT '质保到期日期',
    returned_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已返还金额',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE-有效/EXPIRED-到期/RETURNED-已返还）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project (project_id),
    KEY idx_contract (contract_id),
    KEY idx_status (status),
    KEY idx_expire_date (expire_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质保金表';

-- 质保金返还表
CREATE TABLE IF NOT EXISTS biz_retention_return (
    id BIGINT NOT NULL COMMENT '主键ID',
    retention_id BIGINT NOT NULL COMMENT '质保金ID',
    return_amount DECIMAL(18,2) NOT NULL COMMENT '返还金额',
    return_date DATE COMMENT '返还日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT-草稿/APPROVED-已审批）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_retention (retention_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质保金返还表';

-- ============================================
-- Task 28: 供应商评价建表脚本
-- ============================================

-- 供应商评价表
CREATE TABLE IF NOT EXISTS biz_supplier_evaluation (
    id BIGINT NOT NULL COMMENT '主键ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    evaluation_date DATE COMMENT '评价日期',
    quality_score INT COMMENT '质量评分（1-5）',
    timeliness_score INT COMMENT '及时性评分（1-5）',
    price_score INT COMMENT '价格评分（1-5）',
    service_score INT COMMENT '服务评分（1-5）',
    cooperation_score INT COMMENT '合作评分（1-5）',
    total_score DECIMAL(4,2) COMMENT '综合评分',
    remark VARCHAR(500) COMMENT '备注',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商评价表';

-- 供应商黑名单表
CREATE TABLE IF NOT EXISTS biz_supplier_blacklist (
    id BIGINT NOT NULL COMMENT '主键ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    reason VARCHAR(500) COMMENT '拉黑原因',
    blacklist_date DATE COMMENT '拉黑日期',
    status INT DEFAULT 1 COMMENT '状态（1-黑名单/0-已移出）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_supplier (supplier_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商黑名单表';

-- ============================================
-- Task 29: 银行账户建表脚本
-- ============================================

-- 银行账户表
CREATE TABLE IF NOT EXISTS biz_bank_account (
    id BIGINT NOT NULL COMMENT '主键ID',
    account_name VARCHAR(200) NOT NULL COMMENT '账户名称',
    bank_name VARCHAR(200) COMMENT '开户行',
    bank_account VARCHAR(50) COMMENT '银行账号',
    account_type VARCHAR(20) COMMENT '账户类型（BASIC-基本户/GENERAL-一般户/SPECIAL-专用户）',
    project_id BIGINT COMMENT '项目ID（可选）',
    status INT DEFAULT 1 COMMENT '状态（1-启用/0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_account_type (account_type),
    KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='银行账户表';

-- ============================================
-- Task 30: 审计日志建表脚本
-- ============================================

-- 审计日志表
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGINT NOT NULL COMMENT '主键ID',
    table_name VARCHAR(100) COMMENT '表名',
    record_id BIGINT COMMENT '记录ID',
    field_name VARCHAR(100) COMMENT '字段名',
    old_value VARCHAR(2000) COMMENT '旧值',
    new_value VARCHAR(2000) COMMENT '新值',
    oper_user_id BIGINT COMMENT '操作人ID',
    oper_user_name VARCHAR(50) COMMENT '操作人姓名',
    oper_time DATETIME COMMENT '操作时间',
    tenant_id BIGINT COMMENT '租户ID',
    PRIMARY KEY (id),
    KEY idx_table_record (table_name, record_id),
    KEY idx_oper_time (oper_time),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审计日志表';


-- ============================================
-- 流程催办模块建表脚本
-- ============================================

-- 催办配置表
CREATE TABLE IF NOT EXISTS wf_urge_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    timeout_hours INT DEFAULT 24 COMMENT '催办超时时间（小时），超过此时间未处理则触发催办',
    interval_hours INT DEFAULT 4 COMMENT '催办间隔（小时），两次催办之间的最小间隔',
    max_urge_count INT DEFAULT 3 COMMENT '最大催办次数',
    auto_urge_enabled INT DEFAULT 1 COMMENT '是否启用自动催办（0-禁用 1-启用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='催办配置表';

-- 催办记录表
CREATE TABLE IF NOT EXISTS wf_urge_record (
    id BIGINT NOT NULL COMMENT '主键ID',
    process_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    task_name VARCHAR(200) COMMENT '任务名称',
    assignee VARCHAR(50) COMMENT '被催办人用户ID',
    urge_by VARCHAR(50) COMMENT '催办人（SYSTEM-系统自动/用户ID-手动催办）',
    urge_type VARCHAR(20) NOT NULL COMMENT '催办类型（AUTO-自动催办/MANUAL-手动催办）',
    urge_message VARCHAR(500) COMMENT '催办消息内容',
    urge_time DATETIME NOT NULL COMMENT '催办时间',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_process_instance (process_instance_id),
    KEY idx_assignee (assignee),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='催办记录表';

-- 审批委托配置表
CREATE TABLE IF NOT EXISTS wf_delegate_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    delegator_id BIGINT NOT NULL COMMENT '委托人用户ID',
    delegate_id BIGINT NOT NULL COMMENT '代理人用户ID',
    start_time DATETIME NOT NULL COMMENT '委托开始时间',
    end_time DATETIME NOT NULL COMMENT '委托结束时间',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE-生效中 EXPIRED-已过期 CANCELLED-已取消）',
    reason VARCHAR(500) COMMENT '委托原因/备注',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_delegator_id (delegator_id),
    KEY idx_delegate_id (delegate_id),
    KEY idx_status (status),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批委托配置表';
