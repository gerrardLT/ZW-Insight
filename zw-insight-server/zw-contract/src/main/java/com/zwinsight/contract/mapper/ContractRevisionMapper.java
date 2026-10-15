package com.zwinsight.contract.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.ContractRevision;
@org.apache.ibatis.annotations.Mapper
@com.zwinsight.common.datapermission.DataPermission({
    @com.zwinsight.common.datapermission.DataColumn(projectColumn = "project_id", userColumn = "created_by")
})
public interface ContractRevisionMapper extends BaseMapper<ContractRevision> {}
