package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * CBS 成本账户实体（Cost Breakdown Structure，成本分解结构）
 * <p>
 * 成本主线的<b>唯一归集口径</b>。六个金额维度构成完整的成本控制闭环：
 * </p>
 * <pre>
 *   baselineAmount    目标成本   —— 首次批准预算，作为考核基准，不随变更漂移
 *   currentAmount     当前预算   —— baseline + 已批准变更（变更事件驱动，禁止手工乱改）
 *   commitmentAmount  已承诺     —— 合同/采购订单签订即占用（未付款也已锁定额度）
 *   actualAmount      实际成本   —— 结算/发票/付款已发生的真实支出
 *   forecastAmount    完工预测   —— EAC = actual + 剩余工作估算
 *   variance          偏差       —— current - forecast（正=节约，负=超支预警）
 * </pre>
 * <p>
 * 通过 {@code wbsNodeId} 与 WBS 工作包挂钩，使成本维度与范围/进度维度共享同一套
 * 分解结构，避免「成本按科目、进度按任务、合同按分包」三套口径无法对齐的经典断点。
 * </p>
 * <p>列名使用 account_ 前缀（account_code/account_name），规避 SQL 保留字。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_cost_account")
public class BizCostAccount extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 父账户ID（NULL=根账户） */
    private Long parentId;

    /** 关联WBS节点ID（可空） */
    private Long wbsNodeId;

    /** 账户编码（如 01.02.03），租户+项目内唯一 */
    private String accountCode;

    /** 账户名称 */
    private String accountName;

    /** 费用类别（MATERIAL/LABOR/MACHINE/SUBCONTRACT/INDIRECT/OTHER） */
    private String costCategory;

    /** 费用子类（对应 biz_cost_subcategory） */
    private String costSubcategory;

    /** 目标成本（原始批准预算） */
    private BigDecimal baselineAmount;

    /** 当前预算（含已批准变更） */
    private BigDecimal currentAmount;

    /** 已承诺金额（合同/采购占用） */
    private BigDecimal commitmentAmount;

    /** 实际成本（结算/发票/付款） */
    private BigDecimal actualAmount;

    /** 完工预测 EAC */
    private BigDecimal forecastAmount;

    /** 状态（ACTIVE-活跃/LOCKED-锁定/CLOSED-已关闭） */
    private String status;

    /** 备注 */
    private String remark;

    /** 项目名称（冗余展示字段，不持久化） */
    @TableField(exist = false)
    private String projectName;

    /** WBS 节点编号（联查展示，不持久化） */
    @TableField(exist = false)
    private String wbsNodeCode;

    /** WBS 节点名称（联查展示，不持久化） */
    @TableField(exist = false)
    private String wbsNodeName;

    /** 子账户（树形查询时内存装配，不持久化） */
    @TableField(exist = false)
    private List<BizCostAccount> children = new ArrayList<>();

    // ==================== 派生指标（不持久化，统一口径避免前端各算各的） ====================

    /** 剩余预算 = 当前预算 - 实际成本 */
    public BigDecimal getRemainingAmount() {
        return nvl(currentAmount).subtract(nvl(actualAmount));
    }

    /** 偏差金额 = 当前预算 - 完工预测（正=节约，负=超支） */
    public BigDecimal getVarianceAmount() {
        return nvl(currentAmount).subtract(nvl(forecastAmount));
    }

    /** 预算变更额 = 当前预算 - 目标成本（反映累计变更净影响） */
    public BigDecimal getChangeAmount() {
        return nvl(currentAmount).subtract(nvl(baselineAmount));
    }

    /** 未付款承诺 = 已承诺 - 实际成本（不小于 0） */
    public BigDecimal getUnbilledCommitment() {
        BigDecimal diff = nvl(commitmentAmount).subtract(nvl(actualAmount));
        return diff.signum() < 0 ? BigDecimal.ZERO : diff;
    }

    /** 预算使用率(%) = 实际成本 / 当前预算 */
    public BigDecimal getUsageRate() {
        return rate(nvl(actualAmount), nvl(currentAmount));
    }

    /** 承诺率(%) = 已承诺 / 当前预算 */
    public BigDecimal getCommitmentRate() {
        return rate(nvl(commitmentAmount), nvl(currentAmount));
    }

    /** 是否超支（实际成本已超过当前预算） */
    public boolean isOverrun() {
        return nvl(actualAmount).compareTo(nvl(currentAmount)) > 0;
    }

    /** 是否预测超支（EAC 已超过当前预算） */
    public boolean isForecastOverrun() {
        return nvl(forecastAmount).compareTo(nvl(currentAmount)) > 0;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
