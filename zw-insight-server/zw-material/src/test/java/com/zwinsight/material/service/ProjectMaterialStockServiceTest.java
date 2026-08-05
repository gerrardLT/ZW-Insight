package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * ProjectMaterialStockService 单元测试
 * <p>库存查询：预警内存过滤手动分页、安全库存回填（项目级优先、全局兜底）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectMaterialStockServiceTest {

    @Mock
    private BizProjectMaterialStockMapper stockMapper;

    @Mock
    private BizStockWarningConfigMapper warningConfigMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private ProjectMaterialStockService service;

    private BizProjectMaterialStock stock(Long projectId, Long materialId, String qty) {
        BizProjectMaterialStock s = new BizProjectMaterialStock();
        s.setProjectId(projectId);
        s.setMaterialId(materialId);
        s.setMaterialName("材料" + materialId);
        s.setStockQuantity(new BigDecimal(qty));
        return s;
    }

    private BizStockWarningConfig config(Long projectId, Long materialId, String safety) {
        BizStockWarningConfig c = new BizStockWarningConfig();
        c.setProjectId(projectId); // null = 全局
        c.setMaterialId(materialId);
        c.setSafetyStock(new BigDecimal(safety));
        return c;
    }

    @Test
    @DisplayName("page - 项目名筛选无匹配直接返回空")
    void page_projectNameNoMatch_returnsEmpty() {
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        PageResult<BizProjectMaterialStock> result = service.page(1, 10, null, null, "不存在", null);

        assertThat(result.getRecords()).isEmpty();
        verify(stockMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("page(warning=LOW) - 内存过滤低库存并手动分页")
    void page_lowWarningFilter() {
        // 材料10 低库存（qty 5 <= 安全 10），材料11 正常（qty 100）
        BizProjectMaterialStock low = stock(1L, 10L, "5");
        BizProjectMaterialStock normal = stock(1L, 11L, "100");
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(low, normal));
        when(warningConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(config(1L, 10L, "10"), config(1L, 11L, "10")));
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        PageResult<BizProjectMaterialStock> result = service.page(1, 10, null, null, null, "LOW");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getMaterialId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("page(warning=NORMAL) - 过滤库存正常项；越界页返回空")
    void page_normalWarningFilter_andOutOfBounds() {
        BizProjectMaterialStock low = stock(1L, 10L, "5");
        BizProjectMaterialStock normal = stock(1L, 11L, "100");
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(low, normal));
        when(warningConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(config(1L, 10L, "10"), config(1L, 11L, "10")));
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        PageResult<BizProjectMaterialStock> normalResult = service.page(1, 10, null, null, null, "NORMAL");
        assertThat(normalResult.getRecords()).extracting(BizProjectMaterialStock::getMaterialId)
                .containsExactly(11L);

        PageResult<BizProjectMaterialStock> outOfRange = service.page(9, 10, null, null, null, "NORMAL");
        assertThat(outOfRange.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("page 正常分页 - 回填 minStock：项目级优先、全局兜底")
    void page_fillsMinStock_projectFirstThenGlobal() {
        BizProjectMaterialStock s1 = stock(1L, 10L, "50");
        BizProjectMaterialStock s2 = stock(2L, 10L, "50"); // 同材料不同项目 → 走全局
        Page<BizProjectMaterialStock> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(s1, s2));
        page.setTotal(2L);
        when(stockMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(warningConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(
                        config(1L, 10L, "20"),   // 项目1 专属
                        config(null, 10L, "30"))); // 全局
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        service.page(1, 10, null, null, null, null);

        assertThat(s1.getMinStock()).isEqualByComparingTo("20"); // 项目级优先
        assertThat(s2.getMinStock()).isEqualByComparingTo("30"); // 全局兜底
    }

    @Test
    @DisplayName("getByProject - 透传查询并回填 minStock")
    void getByProject_fillsMinStock() {
        BizProjectMaterialStock s = stock(1L, 10L, "50");
        when(stockMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(s));
        when(warningConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(config(1L, 10L, "15")));

        List<BizProjectMaterialStock> result = service.getByProject(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMinStock()).isEqualByComparingTo("15");
    }
}
