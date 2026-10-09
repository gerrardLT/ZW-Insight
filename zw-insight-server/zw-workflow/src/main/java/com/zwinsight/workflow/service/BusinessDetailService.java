package com.zwinsight.workflow.service;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批待办的业务单据详情（只读）。
 * <p>审批抽屉此前只有「业务类型 / ID」，审批人看不到单据内容。本服务按业务类型从白名单表里取固定列，
 * 不拼接任何外部输入到 SQL；强制带租户与未删除条件；身份证、手机号、银行账号脱敏。</p>
 * <p>付款申请由前端沿用专用详情接口，这里不重复提供。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDetailService {

    private final JdbcTemplate jdbc;
    private final ApprovalService approvalService;

    static String sourceTable(String type) {
        if ("PAYMENT_APPLY".equals(type)) return "biz_payment_apply";
        Spec spec = SPECS.get(type);
        return spec == null ? null : spec.table();
    }

    /** 字段：列名、中文标签、类型（T 文本 M 金额 D 日期 P 百分比 B 是否 MASK 脱敏，以及外键名称解析 REF_*） */
    private record F(String col, String label, String kind) {}

    private record Spec(String typeName, String table, List<F> fields) {}

    private static final Map<String, String[]> REFS = new HashMap<>();
    private static final Map<String, Spec> SPECS = new HashMap<>();

    private static F f(String col, String label, String kind) {
        return new F(col, label, kind);
    }

    private static void spec(String type, String typeName, String table, F... fields) {
        SPECS.put(type, new Spec(typeName, table, List.of(fields)));
    }

    static {
        // 外键 → {表, 名称列}
        REFS.put("REF_PROJECT", new String[]{"biz_project", "project_name"});
        REFS.put("REF_CC", new String[]{"biz_construction_contract", "contract_code"});
        REFS.put("REF_LC", new String[]{"biz_labor_contract", "contract_code"});
        REFS.put("REF_PC", new String[]{"biz_purchase_contract", "contract_code"});
        REFS.put("REF_ORG", new String[]{"sys_org", "org_name"});
        REFS.put("REF_POST", new String[]{"sys_post", "post_name"});
        REFS.put("REF_TEAM", new String[]{"biz_team", "team_name"});

        spec("CONSTRUCTION_CONTRACT", "施工合同", "biz_construction_contract",
                f("contract_code", "合同编号", "T"), f("project_name", "所属项目", "T"),
                f("contract_type", "合同类型", "T"), f("party_a_name", "甲方", "T"),
                f("contract_amount", "合同金额", "M"), f("tax_rate", "税率", "P"),
                f("amount_without_tax", "不含税金额", "M"), f("tax_amount", "税额", "M"),
                f("signing_date", "签订日期", "D"), f("start_date", "开工日期", "D"), f("end_date", "竣工日期", "D"));
        spec("CHANGE_VISA", "变更签证", "biz_change_visa",
                f("project_id", "所属项目", "REF_PROJECT"), f("contract_id", "施工合同", "REF_CC"),
                f("change_type", "变更类型", "T"), f("change_amount", "变更金额", "M"),
                f("change_reason", "变更原因", "T"), f("change_content", "变更内容", "T"));
        spec("FINAL_SETTLEMENT", "竣工结算", "biz_final_settlement",
                f("project_id", "所属项目", "REF_PROJECT"), f("contract_id", "施工合同", "REF_CC"),
                f("settlement_amount", "结算金额", "M"), f("settlement_date", "结算日期", "D"));
        spec("OUTPUT_REPORT", "产值报告", "biz_output_report",
                f("project_id", "所属项目", "REF_PROJECT"), f("contract_id", "施工合同", "REF_CC"),
                f("report_period", "报告期", "T"), f("current_output", "本期产值", "M"),
                f("cumulative_output", "累计产值", "M"), f("confirm_date", "确认日期", "D"));
        spec("BUDGET_CHANGE", "预算变更", "biz_budget_change",
                f("change_code", "变更编号", "T"), f("project_id", "所属项目", "REF_PROJECT"),
                f("total_adjust_amount", "调整总额", "M"), f("change_reason", "变更原因", "T"));
        spec("INVOICE_APPLY", "开票申请", "biz_invoice_apply",
                f("project_id", "所属项目", "REF_PROJECT"), f("contract_id", "施工合同", "REF_CC"),
                f("invoice_type", "发票类型", "T"), f("invoice_amount", "开票金额", "M"),
                f("tax_rate", "税率", "P"), f("invoice_title", "发票抬头", "T"),
                f("taxpayer_id", "纳税人识别号", "T"), f("bank_name", "开户银行", "T"),
                f("bank_account", "银行账号", "MASK"), f("apply_date", "申请日期", "D"));
        spec("PROJECT_REIMBURSEMENT", "项目报销", "biz_project_reimbursement",
                f("project_id", "所属项目", "REF_PROJECT"), f("total_amount", "报销总额", "M"),
                f("reimbursement_date", "报销日期", "D"), f("offset_reserve", "冲抵备用金", "B"),
                f("offset_amount", "冲抵金额", "M"));
        spec("PERSONAL_REIMBURSEMENT", "个人报销", "biz_personal_reimbursement",
                f("total_amount", "报销总额", "M"), f("reimbursement_date", "报销日期", "D"),
                f("remark", "备注", "T"));
        spec("RETENTION_RETURN", "质保金退还", "biz_retention_return",
                f("return_amount", "退还金额", "M"), f("return_date", "退还日期", "D"));
        spec("RESERVE_FUND_APPLY", "备用金申请", "biz_reserve_fund_apply",
                f("project_id", "所属项目", "REF_PROJECT"), f("applicant", "申请人", "T"),
                f("apply_amount", "申请金额", "M"), f("apply_date", "申请日期", "D"),
                f("returned_amount", "已退还", "M"), f("offset_amount", "已冲抵", "M"));
        spec("FUND_TRANSFER", "资金调拨", "biz_fund_transfer",
                f("transfer_code", "调拨编号", "T"), f("from_project_id", "调出项目", "REF_PROJECT"),
                f("to_project_id", "调入项目", "REF_PROJECT"), f("transfer_amount", "调拨金额", "M"),
                f("transfer_date", "调拨日期", "D"), f("transfer_reason", "调拨原因", "T"));
        spec("PROJECT_SETTLEMENT", "项目结算", "biz_project_settlement",
                f("settlement_code", "结算编号", "T"), f("project_id", "所属项目", "REF_PROJECT"),
                f("construction_contract_amount", "施工合同额", "M"), f("cumulative_output", "累计产值", "M"),
                f("total_income", "总收入", "M"), f("total_expenditure", "总支出", "M"),
                f("final_settlement_amount", "最终结算额", "M"), f("profit", "利润", "M"),
                f("profit_rate", "利润率", "P"));
        spec("PURCHASE_CONTRACT", "采购合同", "biz_purchase_contract",
                f("contract_code", "合同编号", "T"), f("contract_name", "合同名称", "T"),
                f("project_id", "所属项目", "REF_PROJECT"), f("party_b_name", "供应商", "T"),
                f("contract_amount", "合同金额", "M"), f("signing_date", "签订日期", "D"),
                f("payment_terms", "付款条款", "T"));
        spec("PURCHASE_SETTLEMENT", "采购结算", "biz_purchase_settlement",
                f("settlement_no", "结算单号", "T"), f("project_id", "所属项目", "REF_PROJECT"),
                f("contract_id", "采购合同", "REF_PC"), f("settlement_amount", "结算金额", "M"),
                f("settlement_date", "结算日期", "D"), f("remark", "备注", "T"));
        spec("LABOR_CONTRACT", "劳务合同", "biz_labor_contract",
                f("contract_code", "合同编号", "T"), f("contract_name", "合同名称", "T"),
                f("project_id", "所属项目", "REF_PROJECT"), f("team_name", "劳务班组", "T"),
                f("contract_amount", "合同金额", "M"), f("signing_date", "签订日期", "D"),
                f("start_date", "开始日期", "D"), f("end_date", "结束日期", "D"),
                f("payment_terms", "付款条款", "T"));
        spec("LABOR_OUTPUT", "劳务产值", "biz_labor_output_report",
                f("project_id", "所属项目", "REF_PROJECT"), f("contract_id", "劳务合同", "REF_LC"),
                f("current_output", "本期产值", "M"), f("cumulative_output", "累计产值", "M"));
        spec("LABOR_SETTLEMENT", "劳务结算", "biz_labor_settlement",
                f("project_id", "所属项目", "REF_PROJECT"), f("contract_id", "劳务合同", "REF_LC"),
                f("settlement_amount", "结算金额", "M"), f("cumulative_settlement", "累计结算", "M"));
        spec("LABOR_PAYROLL", "劳务工资", "biz_labor_payroll",
                f("project_id", "所属项目", "REF_PROJECT"), f("team_id", "劳务班组", "REF_TEAM"),
                f("period_start", "工资期起", "D"), f("period_end", "工资期止", "D"),
                f("total_settlement", "应发合计", "M"), f("total_paid", "已发", "M"), f("unpaid", "未发", "M"));
        spec("LABOR_REWARD_PUNISH", "劳务奖惩", "biz_labor_reward_punish",
                f("project_id", "所属项目", "REF_PROJECT"), f("contract_id", "劳务合同", "REF_LC"),
                f("rp_type", "奖惩类型", "T"), f("amount", "金额", "M"), f("reason", "原因", "T"));
        spec("MACHINE_CONTRACT", "机械合同", "biz_machine_contract",
                f("contract_code", "合同编号", "T"), f("contract_name", "合同名称", "T"),
                f("project_id", "所属项目", "REF_PROJECT"), f("supplier_name", "出租方", "T"),
                f("machine_name", "机械名称", "T"), f("rental_type", "计价方式", "T"),
                f("unit_price", "单价", "M"), f("contract_amount", "合同金额", "M"),
                f("start_date", "开始日期", "D"), f("end_date", "结束日期", "D"));
        spec("machine_settlement", "机械结算", "biz_machine_work_settlement",
                f("settlement_code", "结算编号", "T"), f("project_id", "所属项目", "REF_PROJECT"),
                f("period_start", "结算期起", "D"), f("period_end", "结算期止", "D"),
                f("total_amount", "结算总额", "M"));
        spec("MATERIAL_TRANSFER", "材料调拨", "biz_material_transfer",
                f("from_project_id", "调出项目", "REF_PROJECT"), f("to_project_id", "调入项目", "REF_PROJECT"),
                f("transfer_date", "调拨日期", "D"));
        spec("MATERIAL_REFUND", "材料退库", "biz_material_refund",
                f("refund_code", "退库编号", "T"), f("project_id", "所属项目", "REF_PROJECT"),
                f("refund_amount", "退库金额", "M"), f("refund_reason", "退库原因", "T"));
        spec("COMPLETION_ACCEPTANCE", "竣工验收", "biz_completion_acceptance",
                f("project_id", "所属项目", "REF_PROJECT"), f("acceptance_date", "验收日期", "D"),
                f("acceptance_report", "验收报告", "T"));
        spec("DEPOSIT_APPLY", "保证金申请", "biz_deposit_apply",
                f("project_id", "所属项目", "REF_PROJECT"), f("deposit_amount", "保证金金额", "M"),
                f("payment_date", "缴纳日期", "D"));
        spec("PROJECT_CLOSE", "项目结项", "biz_project", projectFields());
        spec("PROJECT_FILING", "项目报备", "biz_project", projectFields());
        spec("PROJECT_TERMINATE", "项目终止", "biz_project", projectFields());
        spec("ENTRY_APPLY", "入职申请", "biz_entry_apply",
                f("real_name", "姓名", "T"), f("username", "登录账号", "T"), f("gender", "性别", "T"),
                f("birth_date", "出生日期", "D"), f("id_card", "身份证号", "MASK"), f("phone", "手机号", "MASK"),
                f("entry_date", "入职日期", "D"), f("org_id", "所属机构", "REF_ORG"), f("post_id", "岗位", "REF_POST"));
        spec("REGULAR_APPLY", "转正申请", "biz_regular_apply",
                f("user_name", "员工", "T"), f("trial_end_date", "试用期截止", "D"), f("regular_date", "转正日期", "D"));
        spec("TRANSFER_APPLY", "调动申请", "biz_transfer_apply",
                f("user_name", "员工", "T"), f("transfer_date", "调动日期", "D"),
                f("from_org_id", "原机构", "REF_ORG"), f("to_org_id", "新机构", "REF_ORG"),
                f("from_post_id", "原岗位", "REF_POST"), f("to_post_id", "新岗位", "REF_POST"), f("remark", "备注", "T"));
        spec("RESIGN_APPLY", "离职申请", "biz_resign_apply",
                f("user_name", "员工", "T"), f("resign_date", "离职日期", "D"),
                f("handover_person", "交接人", "T"), f("is_handover", "已交接", "B"));
        spec("SEAL_APPLY", "用印申请", "biz_seal_apply",
                f("applicant", "申请人", "T"), f("seal_type", "印章类型", "T"),
                f("is_carry_out", "是否携带外出", "B"), f("use_time", "用印时间", "D"), f("reason", "事由", "T"));
        spec("VEHICLE_APPLY", "车辆申请", "biz_vehicle_apply",
                f("plate_number", "车牌号", "T"), f("use_time", "用车时间", "D"),
                f("expected_return_time", "预计还车", "D"), f("purpose", "用途", "T"));
    }

    private static F[] projectFields() {
        return new F[]{f("project_code", "项目编号", "T"), f("project_name", "项目名称", "T"),
                f("contract_amount", "合同金额", "M"), f("budget_amount", "预算金额", "M"), f("status", "项目状态", "T")};
    }

    /**
     * 取任务对应的业务单据详情。
     *
     * @param taskId 审批任务ID（运行中或已办均可）
     */
    public Map<String, Object> getForTask(String taskId) {
        Map<String, Object> detail = approvalService.getTaskDetail(taskId);
        Object typeObj = detail.get("businessType");
        String type = typeObj == null ? null : String.valueOf(typeObj);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessType", type);
        Spec spec = type == null ? null : SPECS.get(type);
        if (spec == null) {
            result.put("supported", false);
            result.put("fields", List.of());
            return result;
        }
        String idStr = String.valueOf(detail.get("businessId"));
        Long tenantId = SecurityContextHolder.getTenantId();
        if (!idStr.matches("[1-9]\\d{0,18}") || tenantId == null) {
            throw new BusinessException(403, "业务单据标识无效");
        }
        Long id;
        try {
            id = Long.valueOf(idStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(403, "业务单据标识无效");
        }

        result.put("supported", true);
        result.put("typeName", spec.typeName());
        List<Map<String, Object>> fields = new ArrayList<>();
        result.put("fields", fields);
        try {
            StringBuilder cols = new StringBuilder();
            for (F fd : spec.fields()) {
                if (cols.length() > 0) cols.append(", ");
                cols.append(fd.col());
            }
            // 表名/列名全部来自上面的静态白名单；id 与 tenantId 走参数绑定
            List<Map<String, Object>> rows = query(
                    "SELECT " + cols + " FROM " + spec.table() + " WHERE id = ? AND tenant_id = ? AND deleted = 0",
                    id, tenantId);
            if (rows.isEmpty()) {
                result.put("found", false);
                return result;
            }
            result.put("found", true);
            Map<String, Object> row = rows.get(0);
            for (F fd : spec.fields()) {
                Object raw = row.get(fd.col());
                String kind = fd.kind();
                String[] ref = REFS.get(kind);
                if (ref != null) {
                    raw = raw == null ? null : lookupName(ref[0], ref[1], raw, tenantId);
                    kind = "T";
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("label", fd.label());
                item.put("kind", "MASK".equals(kind) ? "T" : kind);
                item.put("value", format(kind, raw));
                fields.add(item);
            }
        } catch (RuntimeException e) {
            // 业务详情是辅助信息，查询失败不应阻断审批，前端会如实提示
            log.warn("审批业务详情查询失败 type={} id={}: {}", type, idStr, e.toString());
            fields.clear();
            result.put("error", "业务详情加载失败");
        }
        return result;
    }

    /** 固定两个绑定参数（单据ID、租户ID），便于单测打桩 */
    protected List<Map<String, Object>> query(String sql, Long id, Long tenantId) {
        return jdbc.queryForList(sql, id, tenantId);
    }

    private Object lookupName(String table, String nameCol, Object id, Long tenantId) {
        String idStr = String.valueOf(id);
        if (!idStr.matches("[1-9]\\d{0,18}")) return idStr;
        List<Map<String, Object>> rows = query(
                "SELECT " + nameCol + " FROM " + table + " WHERE id = ? AND tenant_id = ? AND deleted = 0",
                Long.valueOf(idStr), tenantId);
        if (rows.isEmpty() || rows.get(0).get(nameCol) == null) return idStr;
        return rows.get(0).get(nameCol);
    }

    static String format(String kind, Object v) {
        if (v == null) return null;
        if ("B".equals(kind)) {
            String s = String.valueOf(v);
            return "1".equals(s) || "true".equalsIgnoreCase(s) ? "是" : "否";
        }
        if ("MASK".equals(kind)) {
            String s = String.valueOf(v);
            int n = s.length();
            if (n >= 8) return s.substring(0, 3) + "****" + s.substring(n - 4);
            return n <= 1 ? "*" : s.substring(0, 1) + "***";
        }
        if (v instanceof BigDecimal bd) return bd.toPlainString();
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toString().replace('T', ' ');
        if (v instanceof LocalDateTime ldt) return ldt.toString().replace('T', ' ');
        if (v instanceof LocalDate || v instanceof java.sql.Date) return v.toString();
        return String.valueOf(v);
    }

}
