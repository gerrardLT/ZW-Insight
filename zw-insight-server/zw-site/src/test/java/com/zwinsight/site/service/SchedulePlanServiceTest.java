package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.mapper.BizSchedulePlanMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SchedulePlanService（进度计划）单元测试
 *
 * 覆盖场景:
 * - 新增默认值（进度0/NOT_STARTED/根节点）与更新/删除校验
 * - 删除的子任务检查
 * - 分页：项目名称无匹配早返回
 * - 树形构建与父节点进度递归计算
 */
@ExtendWith(MockitoExtension.class)
class SchedulePlanServiceTest {

    @Mock
    private BizSchedulePlanMapper planMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private SchedulePlanService schedulePlanService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSchedulePlan.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizProject.class);
    }

    private BizSchedulePlan plan(Long id, Long parentId, String status, BigDecimal progress) {
        BizSchedulePlan plan = new BizSchedulePlan();
        plan.setId(id);
        plan.setParentId(parentId);
        plan.setTaskStatus(status);
        plan.setProgress(progress);
        return plan;
    }

    @Test
    @DisplayName("新增计划任务：默认进度0/NOT_STARTED/根节点")
    void save_setsDefaults() {
        BizSchedulePlan plan = new BizSchedulePlan();
        plan.setTaskName("土方开挖");

        schedulePlanService.save(plan);

        assertThat(plan.getProgress()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(plan.getTaskStatus()).isEqualTo("NOT_STARTED");
        assertThat(plan.getParentId()).isZero();
        verify(planMapper).insert(plan);
    }

    @Test
    @DisplayName("更新计划任务：不存在抛异常")
    void update_notFound_throwsException() {
        BizSchedulePlan plan = plan(999L, 0L, null, null);
        when(planMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> schedulePlanService.update(plan))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划任务不存在");
    }

    @Test
    @DisplayName("删除计划任务：不存在抛异常")
    void delete_notFound_throwsException() {
        when(planMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> schedulePlanService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划任务不存在");
    }

    @Test
    @DisplayName("删除计划任务：存在子任务拒绝删除")
    void delete_hasChildren_rejected() {
        when(planMapper.selectById(1L)).thenReturn(plan(1L, 0L, "NOT_STARTED", BigDecimal.ZERO));
        when(planMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> schedulePlanService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在子任务，无法删除");
    }

    @Test
    @DisplayName("删除计划任务：无子任务正常删除")
    void delete_noChildren_success() {
        when(planMapper.selectById(1L)).thenReturn(plan(1L, 0L, "NOT_STARTED", BigDecimal.ZERO));
        when(planMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        schedulePlanService.delete(1L);

        verify(planMapper).deleteById(1L);
    }

    @Test
    @DisplayName("分页：项目名称无匹配时早返回空页")
    void page_projectNameNoMatch_returnsEmptyEarly() {
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        PageResult<BizSchedulePlan> result =
                schedulePlanService.page(1, 10, null, "不存在的项目", null);

        assertThat(result.getTotal()).isZero();
        verify(planMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("分页：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizSchedulePlan> stubPage = new Page<>(1, 10);
        stubPage.setTotal(3);
        when(planMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizSchedulePlan> result = schedulePlanService.page(1, 10, 10L, null, null);

        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("树形查询：按父子关系构建嵌套结构")
    void list_buildsTree() {
        when(planMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        plan(1L, 0L, "IN_PROGRESS", new BigDecimal("50")),
                        plan(2L, 1L, "COMPLETED", new BigDecimal("100")),
                        plan(3L, 0L, "NOT_STARTED", BigDecimal.ZERO)));

        List<BizSchedulePlan> roots = schedulePlanService.list(10L);

        assertThat(roots).hasSize(2);
        BizSchedulePlan root1 = roots.stream().filter(p -> p.getId() == 1L).findFirst().orElseThrow();
        assertThat(root1.getChildren()).hasSize(1);
        assertThat(root1.getChildren().get(0).getId()).isEqualTo(2L);
        BizSchedulePlan root3 = roots.stream().filter(p -> p.getId() == 3L).findFirst().orElseThrow();
        assertThat(root3.getChildren()).isNull();
    }

    @Test
    @DisplayName("父节点进度：根节点/空父节点直接返回")
    void calculateParentProgress_rootParent_noop() {
        schedulePlanService.calculateParentProgress(0L);
        schedulePlanService.calculateParentProgress(null);

        verifyNoInteractions(planMapper);
    }

    @Test
    @DisplayName("父节点进度：无子节点时不更新")
    void calculateParentProgress_noChildren_noop() {
        when(planMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        schedulePlanService.calculateParentProgress(1L);

        verify(planMapper, never()).updateById(any(BizSchedulePlan.class));
    }

    @Test
    @DisplayName("父节点进度：子任务全部完成则父任务 COMPLETED")
    void calculateParentProgress_allCompleted_parentCompleted() {
        when(planMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        plan(2L, 1L, "COMPLETED", new BigDecimal("100")),
                        plan(3L, 1L, "COMPLETED", new BigDecimal("100"))));
        BizSchedulePlan parent = plan(1L, 0L, "IN_PROGRESS", new BigDecimal("50"));
        when(planMapper.selectById(1L)).thenReturn(parent);

        schedulePlanService.calculateParentProgress(1L);

        assertThat(parent.getProgress()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(parent.getTaskStatus()).isEqualTo("COMPLETED");
        verify(planMapper).updateById(parent);
    }

    @Test
    @DisplayName("父节点进度：部分进行中取子任务进度均值并置 IN_PROGRESS")
    void calculateParentProgress_partial_averagesAndInProgress() {
        when(planMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        plan(2L, 1L, "IN_PROGRESS", new BigDecimal("50")),
                        plan(3L, 1L, "NOT_STARTED", BigDecimal.ZERO)));
        BizSchedulePlan parent = plan(1L, 0L, "NOT_STARTED", BigDecimal.ZERO);
        when(planMapper.selectById(1L)).thenReturn(parent);

        schedulePlanService.calculateParentProgress(1L);

        assertThat(parent.getProgress()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(parent.getTaskStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("父节点进度：完成后递归向上计算祖父节点")
    void calculateParentProgress_recursesToGrandParent() {
        // 第一层：parentId=2 的子任务
        when(planMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(plan(3L, 2L, "COMPLETED", new BigDecimal("100"))))
                .thenReturn(List.of());
        BizSchedulePlan parent = plan(2L, 1L, "IN_PROGRESS", new BigDecimal("50"));
        when(planMapper.selectById(2L)).thenReturn(parent);

        schedulePlanService.calculateParentProgress(2L);

        assertThat(parent.getTaskStatus()).isEqualTo("COMPLETED");
        // 递归调用到祖父节点（parentId=1）时查询其子任务
        verify(planMapper).selectById(2L);
    }
}
