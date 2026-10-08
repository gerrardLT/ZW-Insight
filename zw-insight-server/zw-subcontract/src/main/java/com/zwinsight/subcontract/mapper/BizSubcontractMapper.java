package com.zwinsight.subcontract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.subcontract.domain.BizSubcontract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizSubcontractMapper extends BaseMapper<BizSubcontract> {

    /**
     * 原子累加合同累计结算金额。
     * <p>支持负数冲销（用于删除 APPROVED 结算单时的对称回滚）。</p>
     */
    @Update("UPDATE biz_subcontract SET cumulative_settlement = COALESCE(cumulative_settlement, 0) + #{amount} WHERE id = #{id} AND deleted = 0")
    int addSettlement(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount);

    /**
     * 带上限的原子累加累计结算：{@code 0 <= 累计+amount <= contract_amount} 才更新。
     * <p>UPDATE 持行锁，两张结算单并发提交时后到者读到最新累计，越限返回 0 行，
     * 取代原"读取后覆盖整行"导致的丢失更新与双单越限。</p>
     */
    @Update("UPDATE biz_subcontract SET cumulative_settlement = COALESCE(cumulative_settlement, 0) + #{amount} "
            + "WHERE id = #{id} AND deleted = 0 "
            + "AND COALESCE(cumulative_settlement, 0) + #{amount} >= 0 "
            + "AND COALESCE(cumulative_settlement, 0) + #{amount} <= contract_amount")
    int addSettlementWithinLimit(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount);

    /** 原子累加累计产值（与累计结算分离；原实现读后覆盖整行，并发提交会丢增量） */
    @Update("UPDATE biz_subcontract SET cumulative_output = COALESCE(cumulative_output, 0) + #{amount} "
            + "WHERE id = #{id} AND deleted = 0")
    int addOutput(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount);
}
