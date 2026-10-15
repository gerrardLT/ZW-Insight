package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizInvoiceApply;
import org.apache.ibatis.annotations.Mapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizInvoiceApplyMapper extends BaseMapper<BizInvoiceApply> {
    @org.apache.ibatis.annotations.Update("UPDATE biz_invoice_apply SET status = 'APPROVED' " +
            "WHERE id = #{id} AND deleted = 0 AND status IN ('SUBMITTED', 'REJECTED')")
    int approvePending(@org.apache.ibatis.annotations.Param("id") Long id);

    @org.apache.ibatis.annotations.Update("UPDATE biz_invoice_apply SET status = #{status} " +
            "WHERE id = #{id} AND deleted = 0 AND status = 'SUBMITTED'")
    int transitionSubmitted(@org.apache.ibatis.annotations.Param("id") Long id,
                            @org.apache.ibatis.annotations.Param("status") String status);
}
