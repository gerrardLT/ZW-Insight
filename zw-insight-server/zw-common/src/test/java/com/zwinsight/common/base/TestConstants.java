package com.zwinsight.common.base;

import java.util.List;

/**
 * 测试基础设施常量类
 * <p>
 * 定义所有测试层级共用的常量值，包括测试租户信息、认证凭据、Redis 前缀
 * 以及受影响表的拓扑删除顺序（外键依赖逆序：子表在前、父表在后）。
 * </p>
 */
public final class TestConstants {

    private TestConstants() {
        // 防止实例化
    }

    // ==================== 测试租户与认证 ====================

    /** 自动化测试专用租户 ID */
    public static final Long TEST_TENANT_ID = 9999L;

    /** 测试租户名称 */
    public static final String TEST_TENANT_NAME = "自动化测试租户";

    /** 测试用户名（与 verify-base.sh / lifecycle-sim.sh 保持一致） */
    public static final String TEST_USER = "admin";

    /** 测试用户密码 */
    public static final String TEST_PASS = "123456";

    // ==================== Redis 前缀 ====================

    /** 测试数据 Redis 键前缀 */
    public static final String REDIS_TEST_PREFIX = "test:t9999:";

    /** Redis 验证码键前缀 */
    public static final String REDIS_CAPTCHA_PREFIX = "captcha:";

    // ==================== 服务器连接 ====================

    /** 服务器地址 */
    public static final String SERVER_HOST = "129.204.3.200";

    /** 后端 API 端口 */
    public static final int API_PORT = 18080;

    /** 后端 API 基础 URL */
    public static final String API_BASE_URL = "http://" + SERVER_HOST + ":" + API_PORT;

    /** 登录接口路径 */
    public static final String LOGIN_PATH = "/api/v1/auth/login";

    // ==================== 表拓扑删除顺序 ====================

    /**
     * 受影响表的拓扑删除顺序（按外键依赖逆序排列）。
     * <p>
     * 规则：子表在前，父表在后。清理时按此顺序依次 DELETE WHERE tenant_id=9999，
     * 可避免外键约束冲突。
     * </p>
     * <p>
     * 层级划分：
     * <ul>
     *   <li>Level 3+ — 最深子表（引用 Level 2 表）</li>
     *   <li>Level 2  — 中间子表（引用 Level 1 表）</li>
     *   <li>Level 1  — 直接引用项目等根表的业务表</li>
     *   <li>Level 0  — 根表（项目、系统基础表）</li>
     * </ul>
     * </p>
     */
    public static final List<String> TABLE_DELETE_ORDER = List.of(
            // ===== Level 3+: 最深子表 =====
            // 报价明细 → 报价单 → 询价单
            "biz_quotation_detail",
            // 保证金退还 → 保证金申请 → 投标登记
            "biz_deposit_return",
            // 质保金返还 → 质保金
            "biz_retention_return",
            // 备用金归还 → 备用金申请
            "biz_reserve_fund_return",
            // 报销明细 → 项目报销
            "biz_reimbursement_detail",

            // ===== Level 2: 中间子表 =====
            // 合同明细 → 施工合同
            "biz_contract_detail",
            // 预算明细 → 预算
            "biz_budget_detail",
            // 采购合同明细 → 采购合同
            "biz_purchase_contract_detail",
            // 材料入库明细 → 材料入库单
            "biz_material_inbound_detail",
            // 材料出库明细 → 材料出库单
            "biz_material_outbound_detail",
            // 材料调拨明细 → 材料调拨单
            "biz_material_transfer_detail",
            // 询价供应商 → 询价单
            "biz_inquiry_supplier",
            // 询价物料 → 询价单
            "biz_inquiry_item",
            // 报价单 → 询价单
            "biz_quotation",
            // 定标结果 → 询价单
            "biz_bid_result",
            // 投标任务 → 投标登记
            "biz_tender_task",
            // 投标费用 → 投标登记
            "biz_tender_fee",
            // 保证金申请 → 投标登记
            "biz_deposit_apply",
            // 开标记录 → 投标登记
            "biz_open_bid_record",
            // 劳务花名册 → 班组
            "biz_labor_roster",
            // 派工单 → 班组
            "biz_work_order",
            // 劳务工资单 → 班组
            "biz_labor_payroll",
            // 劳务产值报告 → 劳务合同
            "biz_labor_output_report",
            // 劳务结算 → 劳务合同
            "biz_labor_settlement",
            // 劳务奖罚 → 劳务合同
            "biz_labor_reward_punish",
            // 分包产值报告 → 分包合同
            "biz_subcontract_output_report",
            // 分包结算 → 分包合同
            "biz_subcontract_settlement",
            // 分包奖罚 → 分包合同
            "biz_subcontract_reward_punish",
            // 机械使用记录 → 机械合同
            "biz_machine_usage_record",
            // 机械结算 → 机械合同
            "biz_machine_settlement",
            // 机械进退场 → 机械台账
            "biz_machine_entry",
            // 机械工作日志 → 机械台账
            "biz_machine_work_log",
            // 机械加油记录 → 机械台账
            "biz_machine_oil_record",
            // 机械维修 → 机械台账
            "biz_machine_repair",
            // 进度反馈 → 进度计划
            "biz_schedule_feedback",
            // 整改记录 → 质量安全检查
            "biz_rectification",
            // 办公用品出入库 → 办公用品
            "biz_office_supply_in_out",
            // 车辆申请 → 车辆
            "biz_vehicle_apply",
            // 车辆维保 → 车辆
            "biz_vehicle_maintenance",

            // ===== Level 1: 引用项目等根表的业务表 =====
            // 项目相关
            "biz_project_member",
            "biz_change_visa",
            "biz_other_contract",
            "biz_output_report",
            "biz_quantity_list",
            "biz_final_settlement",
            "biz_construction_contract",
            // 预算
            "biz_budget_config",
            "biz_cost_subcategory",
            "biz_budget",
            // 采购
            "biz_purchase_settlement",
            "biz_purchase_contract",
            // 询价
            "biz_inquiry",
            // 劳务
            "biz_team",
            "biz_labor_contract",
            // 材料
            "biz_material_inbound",
            "biz_material_outbound",
            "biz_material_transfer",
            "biz_material_inventory",
            "biz_project_material_stock",
            // 机械
            "biz_machine_contract",
            "biz_machine_ledger",
            // 分包
            "biz_subcontract",
            // 现场管理
            "biz_schedule_plan",
            "biz_construction_log",
            "biz_inspection",
            "biz_completion_acceptance",
            // 财务
            "biz_invoice_apply",
            "biz_payment_received",
            "biz_invoice_received",
            "biz_payment_apply",
            "biz_other_payment",
            "biz_project_reimbursement",
            "biz_reserve_fund_apply",
            "biz_personal_reimbursement",
            // 质保金
            "biz_retention_money",
            // 投标
            "biz_tender_register",
            // 人事行政
            "biz_entry_apply",
            "biz_regular_apply",
            "biz_transfer_apply",
            "biz_resign_apply",
            "biz_seal_apply",
            "biz_office_supply",
            "biz_vehicle",
            // 供应商
            "biz_supplier_evaluation",
            "biz_supplier_blacklist",
            // 银行账户
            "biz_bank_account",
            // 人员/企业证书
            "biz_person_certificate",
            "biz_company_certificate",

            // ===== Level 0: 根表 =====
            // 项目（所有业务表的根）
            "biz_project",
            // 工作流
            "wf_urge_record",
            "wf_delegate_config",
            "wf_urge_config",
            "wf_approval_record",
            "wf_rollback_action",
            "wf_process_def",
            "wf_business_type",
            // 消息
            "msg_message",
            "msg_user_shortcut",
            "msg_push_config",
            "msg_announcement",
            "msg_notice",
            "msg_template",
            // 文件与编号
            "file_info",
            "serial_number_rule",
            // 基础数据
            "bd_material",
            "bd_material_category",
            "bd_supplier",
            "bd_owner",
            "bd_company",
            "bd_inspection_scheme",
            // 系统管理
            "sys_audit_log",
            "sys_login_log",
            "sys_oper_log",
            "sys_user_role",
            "sys_role_menu",
            "sys_tenant_menu",
            "sys_dict_item",
            "sys_dict",
            "sys_user",
            "sys_role",
            "sys_menu",
            "sys_org",
            "sys_post",
            "sys_tenant"
    );

    /**
     * 无 tenant_id 列的表集合（清理时需跳过或使用其他条件）。
     * <p>
     * 部分系统表（如 sys_tenant、sys_tenant_type）和关联表不包含 tenant_id 字段，
     * 清理时需要特殊处理（如通过 id 或关联条件删除）。
     * </p>
     */
    public static final List<String> TABLES_WITHOUT_TENANT_ID = List.of(
            "sys_tenant",
            "sys_tenant_type",
            "sys_user_role",
            "sys_role_menu"
    );
}
