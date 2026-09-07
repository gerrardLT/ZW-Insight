package com.zwinsight.budget.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizCostAccountTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CBS 成本流水 Mapper。
 */
@Mapper
public interface BizCostAccountTxnMapper extends BaseMapper<BizCostAccountTxn> {

    /**
     * 幂等探针：判断某业务事实是否已在指定账户的指定金额维度上记账。
     * <p>
     * Outbox 为「至少一次」投递，处理器必须自行去重；金额累加天然非幂等，
     * 因此以 (sourceType, sourceId, accountId, amountType) 为幂等键，
     * 与表上的唯一索引 {@code uk_txn_source} 双保险。
     * </p>
     *
     * @param sourceType 来源类型
     * @param sourceId   来源业务ID
     * @param accountId  成本账户ID
     * @param amountType 金额维度
     * @return 已存在的流水条数（>0 表示已记账，应跳过）
     */
    default long countBySource(@Param("sourceType") String sourceType,
                               @Param("sourceId") String sourceId,
                               @Param("accountId") Long accountId,
                               @Param("amountType") String amountType) {
        Long count = selectCount(new LambdaQueryWrapper<BizCostAccountTxn>()
                .eq(BizCostAccountTxn::getSourceType, sourceType)
                .eq(BizCostAccountTxn::getSourceId, sourceId)
                .eq(BizCostAccountTxn::getAccountId, accountId)
                .eq(BizCostAccountTxn::getAmountType, amountType));
        return count != null ? count : 0L;
    }

    /**
     * 查询某账户的流水（成本下钻：这个数字是怎么来的）。
     *
     * @param accountId  成本账户ID
     * @param amountType 金额维度（可空=全部维度）
     * @param from       业务发生时间起（可空）
     * @param to         业务发生时间止（可空）
     */
    default List<BizCostAccountTxn> listByAccount(Long accountId, String amountType,
                                                  LocalDateTime from, LocalDateTime to) {
        return selectList(new LambdaQueryWrapper<BizCostAccountTxn>()
                .eq(BizCostAccountTxn::getAccountId, accountId)
                .eq(amountType != null && !amountType.isBlank(), BizCostAccountTxn::getAmountType, amountType)
                .ge(from != null, BizCostAccountTxn::getOccurredAt, from)
                .le(to != null, BizCostAccountTxn::getOccurredAt, to)
                .orderByDesc(BizCostAccountTxn::getOccurredAt)
                .orderByDesc(BizCostAccountTxn::getId));
    }

    /**
     * 分页查询账户流水。
     */
    default IPage<BizCostAccountTxn> pageByAccount(Page<BizCostAccountTxn> page, Long accountId,
                                                   String amountType, String sourceType) {
        LambdaQueryWrapper<BizCostAccountTxn> wrapper = new LambdaQueryWrapper<BizCostAccountTxn>()
                .eq(accountId != null, BizCostAccountTxn::getAccountId, accountId)
                .eq(amountType != null && !amountType.isBlank(), BizCostAccountTxn::getAmountType, amountType)
                .eq(sourceType != null && !sourceType.isBlank(), BizCostAccountTxn::getSourceType, sourceType)
                .orderByDesc(BizCostAccountTxn::getOccurredAt)
                .orderByDesc(BizCostAccountTxn::getId);
        return selectPage(page, wrapper);
    }

    /**
     * 按来源汇总某项目的流水（成本主线看板「变更累计」等指标）。
     *
     * @param projectId  项目ID
     * @param sourceType 来源类型
     * @param amountType 金额维度
     * @return 增量合计（无记录返回 0）
     */
    @Select("SELECT COALESCE(SUM(delta_amount), 0) FROM biz_cost_account_txn "
            + "WHERE project_id = #{projectId} AND deleted = 0 "
            + "AND source_type = #{sourceType} AND amount_type = #{amountType}")
    BigDecimal sumDeltaBySource(@Param("projectId") Long projectId,
                                @Param("sourceType") String sourceType,
                                @Param("amountType") String amountType);
}
