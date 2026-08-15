package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizOfficeSupply;
import com.zwinsight.hr.mapper.BizOfficeSupplyMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OfficeSupplyService（办公用品）单元测试（2026-08-15 P3 方向2 补测）
 *
 * 覆盖场景:
 * - 新增时库存数量缺省补 0 / 已指定不覆盖
 * - 更新不存在抛 BusinessException
 * - 分页查询透传 / 删除
 */
@ExtendWith(MockitoExtension.class)
class OfficeSupplyServiceTest {

    @Mock
    private BizOfficeSupplyMapper supplyMapper;

    @InjectMocks
    private OfficeSupplyService officeSupplyService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizOfficeSupply.class);
    }

    @Test
    @DisplayName("新增：库存数量缺省补 0")
    void save_defaultsStockToZero() {
        BizOfficeSupply supply = new BizOfficeSupply();
        supply.setSupplyName("打印纸");

        officeSupplyService.save(supply);

        assertThat(supply.getStockQuantity()).isEqualTo(0);
        verify(supplyMapper).insert(supply);
    }

    @Test
    @DisplayName("新增：已指定库存不覆盖")
    void save_keepsExplicitStock() {
        BizOfficeSupply supply = new BizOfficeSupply();
        supply.setSupplyName("硒鼓");
        supply.setStockQuantity(12);

        officeSupplyService.save(supply);

        assertThat(supply.getStockQuantity()).isEqualTo(12);
    }

    @Test
    @DisplayName("更新：不存在抛 BusinessException")
    void update_notFound_throws() {
        BizOfficeSupply supply = new BizOfficeSupply();
        supply.setId(999L);
        when(supplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> officeSupplyService.update(supply))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("办公用品不存在");
    }

    @Test
    @DisplayName("更新：存在则更新")
    void update_existing_updates() {
        BizOfficeSupply supply = new BizOfficeSupply();
        supply.setId(1L);
        supply.setSupplyName("订书机");
        when(supplyMapper.selectById(1L)).thenReturn(new BizOfficeSupply());

        officeSupplyService.update(supply);

        verify(supplyMapper).updateById(supply);
    }

    @Test
    @DisplayName("分页查询返回 PageResult")
    void page_returnsResult() {
        Page<BizOfficeSupply> page = new Page<>(1, 10);
        page.setRecords(List.of(new BizOfficeSupply()));
        page.setTotal(1);
        when(supplyMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<BizOfficeSupply> result = officeSupplyService.page(1, 10, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("删除调用 mapper deleteById")
    void delete_callsMapper() {
        officeSupplyService.delete(5L);

        verify(supplyMapper).deleteById(5L);
    }
}
