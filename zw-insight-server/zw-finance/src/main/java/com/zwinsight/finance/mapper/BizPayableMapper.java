package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.finance.domain.BizPayable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 应付台账 Mapper（V2026_115，对称 {@link BizReceivableMapper}）
 */
@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizPayableMapper extends BaseMapper<BizPayable> {

    /**
     * 原子增减已核销金额并按结果同步状态（核销/反冲共用）。
     * <p>依赖 MySQL SET 子句从左到右求值：status 表达式中的 paid_amount 已是累加后的新值；
     * GREATEST(0, ...) 防反冲超额外余额为负。方法级空 {@code @DataPermission} 豁免行级过滤
     * （对称 BizReceivableMapper 注释，2026-10-10 F2 审阅结论）。</p>
     *
     * @param id    应付台账ID
     * @param delta 增减额（正=核销，负=反冲）
     * @return 影响行数
     */
    @DataPermission({})
    @Update("UPDATE biz_payable SET paid_amount = GREATEST(0, paid_amount + #{delta}), "
            + "status = IF(paid_amount >= payable_amount, 'CLOSED', 'OPEN') "
            + "WHERE id = #{id} AND deleted = 0")
    int addPaidAmount(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * 结算冲减应付金额（结算单删除/作废时按原额冲减，SQL 层 GREATEST 截断至 0）。
     * <p>状态按冲减后余额重判：余额 ≤ 已核销 → CLOSED。</p>
     *
     * @param id    应付台账ID
     * @param delta 冲减额（正数）
     * @return 影响行数
     */
    @DataPermission({})
    @Update("UPDATE biz_payable SET payable_amount = GREATEST(0, payable_amount - #{delta}), "
            + "status = IF(payable_amount <= paid_amount, 'CLOSED', 'OPEN') "
            + "WHERE id = #{id} AND deleted = 0")
    int reducePayableAmount(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
