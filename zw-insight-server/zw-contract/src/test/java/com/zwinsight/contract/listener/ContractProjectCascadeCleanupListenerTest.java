package com.zwinsight.contract.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.contract.mapper.BizChangeEventMapper;
import com.zwinsight.contract.mapper.BizChangeVisaMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizExpenseContractMapper;
import com.zwinsight.contract.mapper.BizFinalSettlementMapper;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.contract.mapper.BizQuantityListMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ContractProjectCascadeCleanupListener} 单元测试（R7-02）。
 * <p>
 * 本监听器是 11 个模块级联清理监听器的规范样板，故在此覆盖两条核心契约：
 * </p>
 * <ol>
 *   <li><b>清理范围正确</b>：8 张表逐一按 {@code project_id} 过滤，不误伤其他项目的数据</li>
 *   <li><b>异常不吞</b>：任一表清理失败必须向上抛出，让 {@code ProjectService.delete}
 *       的整体事务回滚——否则会产生「项目已删、合同仍在」的半成品状态，
 *       正是 R7-02 要修的那类缺陷</li>
 * </ol>
 * <p>
 * 断言用 {@code getSqlSegment()} + {@code getParamNameValuePairs()}（范式同
 * {@code SysUserServiceTest}）。因监听器刻意使用字符串列名的 {@code QueryWrapper}
 * 而非 {@code LambdaQueryWrapper}，列名无需 {@code TableInfo} 即可解析，
 * 故本测试<b>不需要</b> {@code TableInfoHelper.initTableInfo} 预热
 * （对比 {@code ReserveFundApplyServiceTest} 的 {@code @BeforeAll}）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("项目删除级联清理监听器（contract 模块）")
class ContractProjectCascadeCleanupListenerTest {

    private static final Long PROJECT_ID = 90001L;

    @Mock private BizConstructionContractMapper constructionContractMapper;
    @Mock private BizOutputReportMapper outputReportMapper;
    @Mock private BizQuantityListMapper quantityListMapper;
    @Mock private BizChangeVisaMapper changeVisaMapper;
    @Mock private BizChangeEventMapper changeEventMapper;
    @Mock private BizExpenseContractMapper expenseContractMapper;
    @Mock private BizOtherContractMapper otherContractMapper;
    @Mock private BizFinalSettlementMapper finalSettlementMapper;

    @InjectMocks
    private ContractProjectCascadeCleanupListener listener;

    /** 校验 wrapper 确实按 project_id 绑定了目标项目 ID（列名或参数值任一出错即变红） */
    @SuppressWarnings("unchecked")
    private static boolean isProjectScoped(Object wrapper, Long projectId) {
        QueryWrapper<Object> q = (QueryWrapper<Object>) wrapper;
        return q.getSqlSegment().contains("project_id")
                && q.getParamNameValuePairs().containsValue(projectId);
    }

    private static ProjectDeletedEvent event() {
        return new ProjectDeletedEvent(new Object(), PROJECT_ID, 9999L, "PRJ-T9-0001", "滨江花园一期工程");
    }

    @Test
    @DisplayName("正常路径：8 张表全部按 project_id 清理")
    void onProjectDeleted_cleansAllEightTablesScopedToProject() {
        listener.onProjectDeleted(event());

        verify(constructionContractMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(outputReportMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(quantityListMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(changeVisaMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(changeEventMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(expenseContractMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(otherContractMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(finalSettlementMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
    }

    @Test
    @DisplayName("异常路径：清理失败必须向上抛出以触发整体回滚，且后续表不再执行（fail-fast）")
    void onProjectDeleted_mapperFailure_propagatesAndStops() {
        when(constructionContractMapper.delete(any()))
                .thenThrow(new RuntimeException("DB write timeout"));

        assertThatThrownBy(() -> listener.onProjectDeleted(event()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB write timeout");

        // 首表即失败，其余 7 张表不应被触碰——证明没有 try/catch 吞异常后继续
        verify(outputReportMapper, never()).delete(any());
        verify(quantityListMapper, never()).delete(any());
        verify(changeVisaMapper, never()).delete(any());
        verify(changeEventMapper, never()).delete(any());
        verify(expenseContractMapper, never()).delete(any());
        verify(otherContractMapper, never()).delete(any());
        verify(finalSettlementMapper, never()).delete(any());
    }

    @Test
    @DisplayName("边界：项目下无数据时影响 0 行，不抛异常（幂等，可安全重投）")
    void onProjectDeleted_noRows_isNoop() {
        // 所有 mapper 默认返回 0（未 stub），监听器不得因「影响行数为 0」而报错
        listener.onProjectDeleted(event());

        verify(constructionContractMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
    }
}
