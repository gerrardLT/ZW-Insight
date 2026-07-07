package com.zwinsight.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizPurchaseContractMapper extends BaseMapper<BizPurchaseContract> {
}
