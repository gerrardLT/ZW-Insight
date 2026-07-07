package com.zwinsight.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizBudgetMapper extends BaseMapper<BizBudget> {
}
