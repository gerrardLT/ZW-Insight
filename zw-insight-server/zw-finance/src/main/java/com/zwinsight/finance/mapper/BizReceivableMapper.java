package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizReceivable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 应收台账 Mapper
 */
@Mapper
public interface BizReceivableMapper extends BaseMapper<BizReceivable> {

    /**
     * 原子增减已核销金额并按结果同步状态（核销/反冲共用）。
     * <p><b>为何不用 updateById</b>：本仓未注册 MyBatis-Plus 的 OptimisticLockerInnerInterceptor，
     * {@code updateById} 不会携带 version 条件，属 read-modify-write，并发核销同一批应收时会丢失更新
     * （2026-09-23 审查发现）。改用单条原子 SQL，把「累加 + 状态切换」收敛到数据库层。</p>
     * <p><b>依赖 MySQL SET 子句从左到右求值</b>：status 表达式中的 written_off_amount
     * 已是本次累加后的新值，因此不能再写成 {@code written_off_amount + #{delta}}（会重复加一次）。</p>
     * <p>GREATEST(0, ...) 为防御：反冲额若超过当前已核销额（并发或人工改库），
     * 保证余额不为负；调用方需按实际可反冲额维护项目应收单值（见 ReceivableService.reverseWriteOff）。</p>
     *
     * @param id    应收台账ID
     * @param delta 增减额（正=核销，负=反冲）
     * @return 影响行数（0 表示记录不存在或已逻辑删除）
     */
    @Update("UPDATE biz_receivable SET written_off_amount = GREATEST(0, written_off_amount + #{delta}), "
            + "status = IF(written_off_amount >= receivable_amount, 'CLOSED', 'OPEN') "
            + "WHERE id = #{id} AND deleted = 0")
    int addWrittenOffAmount(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
