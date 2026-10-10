package com.zwinsight.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.finance.domain.BizSecurityBond;
import org.apache.ibatis.annotations.Mapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;

/**
 * 保证金台账 Mapper
 */
@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizSecurityBondMapper extends BaseMapper<BizSecurityBond> {
}
