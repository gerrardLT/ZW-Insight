package com.zwinsight.machine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.machine.domain.BizMachineWorkLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizMachineWorkLogMapper extends BaseMapper<BizMachineWorkLog> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM biz_machine_work_log WHERE id = #{id} "
            + "AND tenant_id = #{tenantId} AND deleted = 0 FOR UPDATE")
    BizMachineWorkLog lockById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM biz_machine_work_settlement_detail d "
            + "JOIN biz_machine_work_settlement s ON s.id = d.settlement_id AND s.tenant_id = d.tenant_id "
            + "WHERE d.tenant_id = #{tenantId} AND s.deleted = 0 AND s.status IN (0,1,2) "
            + "AND JSON_CONTAINS(d.work_log_ids, CAST(#{id} AS JSON), '$')")
    int countOccupied(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 批量更新工作日志的结算状态
     *
     * @param ids    工作日志ID列表
     * @param status 目标状态（UNSETTLED/SETTLED）
     * @return 影响行数
     */
    int batchUpdateSettlementStatus(@Param("ids") List<Long> ids, @Param("status") String status);
}
