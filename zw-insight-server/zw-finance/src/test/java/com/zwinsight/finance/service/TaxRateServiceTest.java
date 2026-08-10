package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizTaxRate;
import com.zwinsight.finance.domain.dto.TaxRateDTO;
import com.zwinsight.finance.mapper.BizTaxRateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaxRateService 单元测试（阶段四批 1 补测）
 * <p>税率字典：新增/修改/停用/查询，名称唯一性与数值边界校验。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaxRateService — 税率字典管理")
class TaxRateServiceTest {

    @Mock
    private BizTaxRateMapper taxRateMapper;

    @InjectMocks
    private TaxRateService service;

    private BizTaxRate entity(Long id, String name, String rate) {
        BizTaxRate e = new BizTaxRate();
        e.setId(id);
        e.setName(name);
        e.setRateValue(new BigDecimal(rate));
        e.setStatus("ENABLED");
        e.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        return e;
    }

    @Test
    @DisplayName("create - 合法入参插入并返回 ENABLED 状态 DTO")
    void create_success() {
        when(taxRateMapper.selectCount(any())).thenReturn(0L);

        TaxRateDTO dto = service.create("增值税13%", new BigDecimal("13"));

        ArgumentCaptor<BizTaxRate> captor = ArgumentCaptor.forClass(BizTaxRate.class);
        verify(taxRateMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ENABLED");
        assertThat(dto.getName()).isEqualTo("增值税13%");
        assertThat(dto.getRateValue()).isEqualByComparingTo("13");
        assertThat(dto.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("create - 名称为 null/空白/超长均拒绝")
    void create_invalidName_rejected() {
        assertThatThrownBy(() -> service.create(null, new BigDecimal("9")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("税率名称");
        assertThatThrownBy(() -> service.create("  ", new BigDecimal("9")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create("A".repeat(31), new BigDecimal("9")))
                .isInstanceOf(BusinessException.class);
        verify(taxRateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create - 数值越界（null/小于0.01/大于99.99/超2位小数）均拒绝")
    void create_invalidRate_rejected() {
        assertThatThrownBy(() -> service.create("税率A", null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("税率数值");
        assertThatThrownBy(() -> service.create("税率A", new BigDecimal("0.001")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create("税率A", new BigDecimal("100")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create("税率A", new BigDecimal("9.999")))
                .isInstanceOf(BusinessException.class);
        verify(taxRateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create - 数值边界 0.01 与 99.99 合法，尾随零不算超精度")
    void create_boundaryValues_accepted() {
        when(taxRateMapper.selectCount(any())).thenReturn(0L);
        assertThat(service.create("边界低", new BigDecimal("0.01")).getRateValue()).isEqualByComparingTo("0.01");
        assertThat(service.create("边界高", new BigDecimal("99.99")).getRateValue()).isEqualByComparingTo("99.99");
        assertThat(service.create("尾随零", new BigDecimal("9.00")).getRateValue()).isEqualByComparingTo("9");
    }

    @Test
    @DisplayName("create - 名称重复（含停用记录）拒绝插入")
    void create_duplicateName_rejected() {
        when(taxRateMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create("增值税13%", new BigDecimal("13")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已存在");
        verify(taxRateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("update - 正常更新返回新值")
    void update_success() {
        when(taxRateMapper.selectById(1L)).thenReturn(entity(1L, "旧名", "9"));
        when(taxRateMapper.selectCount(any())).thenReturn(0L);

        TaxRateDTO dto = service.update(1L, "新名", new BigDecimal("6"));

        verify(taxRateMapper).updateById(any());
        assertThat(dto.getName()).isEqualTo("新名");
        assertThat(dto.getRateValue()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("update - 记录不存在抛异常")
    void update_notFound() {
        when(taxRateMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(99L, "任意", new BigDecimal("9")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("update - 改为他人已用名称被唯一性校验拒绝")
    void update_duplicateName_rejected() {
        when(taxRateMapper.selectById(1L)).thenReturn(entity(1L, "旧名", "9"));
        when(taxRateMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1L, "已占用", new BigDecimal("9")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已存在");
        verify(taxRateMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("delete - 逻辑停用：状态置 DISABLED 而非物理删除")
    void delete_disablesRecord() {
        BizTaxRate e = entity(1L, "税率A", "9");
        when(taxRateMapper.selectById(1L)).thenReturn(e);

        service.delete(1L);

        ArgumentCaptor<BizTaxRate> captor = ArgumentCaptor.forClass(BizTaxRate.class);
        verify(taxRateMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("delete - 记录不存在抛异常")
    void delete_notFound() {
        when(taxRateMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("listEnabled/listAll - 实体正确映射为 DTO")
    void list_mapsToDto() {
        when(taxRateMapper.selectList(any())).thenReturn(List.of(entity(1L, "税率A", "13"), entity(2L, "税率B", "9")));

        List<TaxRateDTO> enabled = service.listEnabled();
        assertThat(enabled).hasSize(2)
                .extracting(TaxRateDTO::getName).containsExactly("税率A", "税率B");
        assertThat(enabled.get(0).getCreateTime()).isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 0));

        assertThat(service.listAll()).hasSize(2);
    }
}
