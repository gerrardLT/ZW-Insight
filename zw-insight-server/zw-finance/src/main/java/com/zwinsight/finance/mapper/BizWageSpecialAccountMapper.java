package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizWageSpecialAccount;
import org.apache.ibatis.annotations.Mapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;

/**
 * 农民工工资专用账户 Mapper
 */
@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizWageSpecialAccountMapper extends BaseMapper<BizWageSpecialAccount> {
}
