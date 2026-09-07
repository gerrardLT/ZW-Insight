package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.contract.domain.BizChangeEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 变更事件 Mapper。
 * <p>
 * 注意：接口内不得声明 private 嵌套类（Java 语言限制），
 * 复杂条件一律用 default 方法 + LambdaQueryWrapper 表达。
 * </p>
 */
@Mapper
public interface BizChangeEventMapper extends BaseMapper<BizChangeEvent> {

    /**
     * 分页查询变更事件（项目/状态/来源/类别/关键字多条件组合）。
     *
     * @param page       分页参数
     * @param projectId  项目ID（可空=全租户范围，受租户拦截器约束）
     * @param status     业务状态（可空）
     * @param sourceType 来源类型（可空）
     * @param category   影响类别（可空）
     * @param keyword    编号/标题模糊匹配（可空）
     */
    default IPage<BizChangeEvent> selectChangeEventPage(Page<BizChangeEvent> page,
                                                        Long projectId,
                                                        String status,
                                                        String sourceType,
                                                        String category,
                                                        String keyword) {
        LambdaQueryWrapper<BizChangeEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizChangeEvent::getProjectId, projectId)
                .eq(isNotBlank(status), BizChangeEvent::getStatus, status)
                .eq(isNotBlank(sourceType), BizChangeEvent::getSourceType, sourceType)
                .eq(isNotBlank(category), BizChangeEvent::getCategory, category)
                .and(isNotBlank(keyword), w -> w
                        .like(BizChangeEvent::getEventNumber, keyword)
                        .or().like(BizChangeEvent::getTitle, keyword))
                .orderByDesc(BizChangeEvent::getCreatedAt)
                .orderByDesc(BizChangeEvent::getId);
        return selectPage(page, wrapper);
    }

    /**
     * 按来源引用查重：同一 sourceType + sourceRef 只允许登记一次变更事件，
     * 保证「业务事实唯一来源」，防止同一张签证被重复录入成多个变更。
     *
     * @param projectId  项目ID
     * @param sourceType 来源类型
     * @param sourceRef  来源引用
     * @param excludeId  排除的事件ID（更新场景传自身ID，新增传 null）
     * @return 已存在的事件（不存在返回 null）
     */
    default BizChangeEvent selectBySourceRef(Long projectId, String sourceType, String sourceRef, Long excludeId) {
        LambdaQueryWrapper<BizChangeEvent> wrapper = new LambdaQueryWrapper<BizChangeEvent>()
                .eq(BizChangeEvent::getProjectId, projectId)
                .eq(BizChangeEvent::getSourceType, sourceType)
                .eq(BizChangeEvent::getSourceRef, sourceRef)
                .ne(excludeId != null, BizChangeEvent::getId, excludeId)
                .ne(BizChangeEvent::getStatus, "CANCELLED")
                .last("LIMIT 1");
        return selectOne(wrapper);
    }

    /**
     * 统计项目下待处理（非终态）的变更事件数，用于工作台「待办/风险」提示。
     */
    @Select("SELECT COUNT(*) FROM biz_change_event "
            + "WHERE project_id = #{projectId} AND deleted = 0 "
            + "AND status IN ('DRAFT','ASSESSING','APPROVING')")
    Long countOpenByProject(@Param("projectId") Long projectId);

    /**
     * 统计项目下已批准变更的累计成本影响（用于「累计变更额」看板指标）。
     */
    @Select("SELECT COALESCE(SUM(cost_delta), 0) FROM biz_change_event "
            + "WHERE project_id = #{projectId} AND deleted = 0 AND status = 'APPROVED'")
    BigDecimal sumApprovedCostDelta(@Param("projectId") Long projectId);

    /**
     * 查询指定项目下待投递影响的事件（审批通过后尚未传导完成的），供补偿任务扫描。
     */
    default List<BizChangeEvent> selectApprovedByProject(Long projectId) {
        return selectList(new LambdaQueryWrapper<BizChangeEvent>()
                .eq(BizChangeEvent::getProjectId, projectId)
                .eq(BizChangeEvent::getStatus, "APPROVED")
                .orderByDesc(BizChangeEvent::getApprovedAt));
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
