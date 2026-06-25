package com.zwinsight.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.project.domain.BizProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizProjectMapper extends BaseMapper<BizProject> {

    /**
     * 更新项目预算金额
     *
     * @param projectId    项目ID
     * @param budgetAmount 新的预算金额
     * @return 影响行数
     */
    @Update("UPDATE biz_project SET budget_amount = #{budgetAmount} WHERE id = #{projectId} AND deleted = 0")
    int updateBudgetAmount(@Param("projectId") Long projectId, @Param("budgetAmount") BigDecimal budgetAmount);

    /**
     * 更新项目状态
     *
     * @param projectId 项目ID
     * @param status    新状态
     * @return 影响行数
     */
    @Update("UPDATE biz_project SET status = #{status} WHERE id = #{projectId} AND deleted = 0")
    int updateStatus(@Param("projectId") Long projectId, @Param("status") String status);
}
