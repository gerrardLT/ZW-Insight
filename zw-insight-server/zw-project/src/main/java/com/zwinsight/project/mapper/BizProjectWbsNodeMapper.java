package com.zwinsight.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.project.domain.BizProjectWbsNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * WBS 节点 Mapper
 */
@Mapper
public interface BizProjectWbsNodeMapper extends BaseMapper<BizProjectWbsNode> {

    /**
     * 按项目聚合各 WBS 节点下挂接进度计划任务的平均进度与任务数量（X3 横向联动补齐）。
     * <p>遵循 AGENTS.md §10「只读查询采用直接跨表 SQL 避免 zw-project 反向依赖 zw-site」惯例。</p>
     */
    @Select("SELECT wbs_node_id AS wbsNodeId, " +
            "ROUND(AVG(COALESCE(progress, 0)), 2) AS avgProgress, " +
            "COUNT(*) AS taskCount " +
            "FROM biz_schedule_plan " +
            "WHERE project_id = #{projectId} AND wbs_node_id IS NOT NULL AND deleted = 0 " +
            "GROUP BY wbs_node_id")
    List<Map<String, Object>> aggregateScheduleProgressByProject(@Param("projectId") Long projectId);

    /**
     * 按项目ID逻辑删除全部 WBS 节点（项目删除级联，R7-02）。
     * <p>不走 {@code ProjectWbsNodeService.delete} 的单节点删除，因为那个方法
     * 对「存在子节点」会抛异常阻断；项目整体删除时整棵 WBS 树一起作废，
     * 无需逐层校验，直接批量标记更高效且不会因树形结构失败。</p>
     *
     * @param projectId 项目ID
     * @return 影响行数
     */
    @Update("UPDATE biz_project_wbs_node SET deleted = 1 WHERE project_id = #{projectId} AND deleted = 0")
    int logicDeleteByProjectId(@Param("projectId") Long projectId);
}
