package com.zwinsight.material.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.material.mapper.BizMaterialInboundMapper;
import com.zwinsight.material.mapper.BizMaterialInventoryMapper;
import com.zwinsight.material.mapper.BizMaterialOutboundMapper;
import com.zwinsight.material.mapper.BizMaterialRefundMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MaterialProjectCascadeCleanupListener} 单元测试（R7-02）。
 * <p>钉住两条核心契约：</p>
 * <ol>
 *   <li><b>清理范围正确</b>：本模块所有含 {@code project_id} 的表逐一张按项目过滤，
 *       不误伤其他项目的数据</li>
 *   <li><b>异常不吞</b>：任一表清理失败必须向上传播，让 {@code ProjectService.delete}
 *       的整体事务回滚——否则会产生「项目已删、子表仍在」的半成品状态，
 *       正是 R7-02 要修的那类缺陷</li>
 * </ol>
 * <p>实现约定（字符串列名 QueryWrapper、模块前缀类名的由来）详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("项目删除级联清理监听器（material 模块）")
class MaterialProjectCascadeCleanupListenerTest {

    private static final Long PROJECT_ID = 90001L;

    @Mock private BizMaterialInboundMapper materialInboundMapper;
    @Mock private BizMaterialOutboundMapper materialOutboundMapper;
    @Mock private BizProjectMaterialStockMapper projectMaterialStockMapper;
    @Mock private BizMaterialInventoryMapper materialInventoryMapper;
    @Mock private BizMaterialRefundMapper materialRefundMapper;
    @Mock private BizStockWarningConfigMapper stockWarningConfigMapper;

    @InjectMocks
    private MaterialProjectCascadeCleanupListener listener;

    /** 校验 wrapper 确实按 project_id 绑定了目标项目 ID（列名或参数值任一出错即变红） */
    @SuppressWarnings("unchecked")
    private static boolean isProjectScoped(Object wrapper, Long projectId) {
        QueryWrapper<Object> q = (QueryWrapper<Object>) wrapper;
        return q.getSqlSegment().contains("project_id")
                && q.getParamNameValuePairs().containsValue(projectId);
    }

    private static ProjectDeletedEvent event() {
        return new ProjectDeletedEvent(new Object(), PROJECT_ID, 1L, "PRJ-T9-0001", "级联清理测试项目");
    }

    @Test
    @DisplayName("正常路径：本模块全部表按 project_id 清理")
    void onProjectDeleted_cleansAllOwnedTablesScopedToProject() {
        listener.onProjectDeleted(event());

        verify(materialInboundMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(materialOutboundMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(projectMaterialStockMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(materialInventoryMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(materialRefundMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
        verify(stockWarningConfigMapper).delete(argThat(w -> isProjectScoped(w, PROJECT_ID)));
    }

    @Test
    @DisplayName("异常路径：清理失败必须向上抛出以触发整体回滚（不吞异常）")
    void onProjectDeleted_mapperFailure_propagates() {
        when(materialInboundMapper.delete(any())).thenThrow(new RuntimeException("DB write timeout"));

        assertThatThrownBy(() -> listener.onProjectDeleted(event()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB write timeout");
    }
}
