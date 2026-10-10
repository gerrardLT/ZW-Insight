package com.zwinsight.tender.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.tender.domain.BizTenderRegister;
import org.apache.ibatis.annotations.Mapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizTenderRegisterMapper extends BaseMapper<BizTenderRegister> {
}
