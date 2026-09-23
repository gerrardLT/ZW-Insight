package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizBankFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 银行流水 Mapper
 */
@Mapper
public interface BizBankFlowMapper extends BaseMapper<BizBankFlow> {

    /**
     * 全部账户最新余额快照合计（驾驶舱「账户资金」卡数据源，V2026_59）。
     * <p>单 SQL 取每账户 snapshot_date 最新一条余额求和，避免逐账户 N+1 查询；
     * 口径为手工登记的余额快照（与资金日报头寸同源）。</p>
     *
     * @return 余额合计（无任何登记时为 NULL，调用方按 0 处理并如实展示）
     */
    @Select("SELECT SUM(b.balance) FROM biz_bank_balance b " +
            "JOIN (SELECT account_id, MAX(snapshot_date) AS max_date FROM biz_bank_balance " +
            "WHERE deleted = 0 GROUP BY account_id) t " +
            "ON t.account_id = b.account_id AND t.max_date = b.snapshot_date " +
            "WHERE b.deleted = 0")
    BigDecimal sumLatestBalances();

    /**
     * 按付款申请聚合已勾稽金额（预测付款侧按「剩余未付额」计入的数据源，V2026_64）。
     * <p>口径与 {@code BankFlowService.sumMatchedAmount} 一致：{@code match_amount} 为空时
     * 取流水整笔 {@code amount}（兼容存量整笔勾稽）。仅统计已勾稽（reconciled=1）
     * 且匹配类型为付款申请的流水。</p>
     * <p>一次查出全部后在内存做差，避免逐单 N+1 查询（滚动预测需逐笔算剩余未付）。</p>
     *
     * @return [{matchedId, matched}]，无勾稽记录时返回空列表
     */
    @Select("SELECT matched_id AS matchedId, SUM(COALESCE(match_amount, amount)) AS matched "
            + "FROM biz_bank_flow "
            + "WHERE deleted = 0 AND reconciled = 1 AND matched_type = 'PAYMENT_APPLY' "
            + "  AND matched_id IS NOT NULL "
            + "GROUP BY matched_id")
    List<Map<String, Object>> sumMatchedGroupByPaymentApply();

    /**
     * 已勾稽金额映射（{付款申请ID: 已勾稽合计}）——供「剩余未付额」计算复用。
     * <p>放在 Mapper 的 default 方法而非 BankFlowService，是为了避免循环依赖：
     * BankFlowService → PaymentApplyService → FundPlanService，若后者再依赖
     * BankFlowService 则成环。同时使 zw-dashboard 的 DashboardService 也能直接复用
     * 同一口径（部分支付时只计已勾稽额，不按全额虚增支出）。</p>
     *
     * @return 无勾稽记录时返回空 Map（不伪造）
     */
    default Map<Long, BigDecimal> matchedAmountByPaymentApply() {
        Map<Long, BigDecimal> result = new java.util.HashMap<>();
        List<Map<String, Object>> rows = sumMatchedGroupByPaymentApply();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Object idObj = row.get("matchedId");
            Object amtObj = row.get("matched");
            if (idObj == null) {
                continue;
            }
            Long applyId = ((Number) idObj).longValue();
            BigDecimal matched;
            if (amtObj instanceof BigDecimal b) {
                matched = b;
            } else if (amtObj == null) {
                matched = BigDecimal.ZERO;
            } else {
                matched = new BigDecimal(String.valueOf(amtObj));
            }
            result.put(applyId, matched);
        }
        return result;
    }
}
