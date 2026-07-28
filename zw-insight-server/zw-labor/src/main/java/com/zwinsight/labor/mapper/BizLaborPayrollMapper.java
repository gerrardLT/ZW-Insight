package com.zwinsight.labor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.labor.domain.BizLaborPayroll;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizLaborPayrollMapper extends BaseMapper<BizLaborPayroll> {
}
