package com.zwinsight.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizPurchaseContractMapper extends BaseMapper<BizPurchaseContract> {

    /**
     * 原子累加合同累计结算金额。
     * <p>支持负数冲销（用于删除 APPROVED 结算单时的对称回滚）。</p>
     */
    @Update("UPDATE biz_purchase_contract SET cumulative_settlement = COALESCE(cumulative_settlement, 0) + #{amount} WHERE id = #{id} AND deleted = 0")
    int addSettlement(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount);
}
