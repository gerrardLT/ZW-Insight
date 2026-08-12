package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizOtherPayment;
import com.zwinsight.finance.mapper.BizOtherPaymentMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OtherPaymentService 单元测试（阶段四批 1 补测）
 * <p>其他费用付款：新增置 APPROVED 并回写项目 totalOtherPayment。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OtherPaymentService — 其他费用付款")
class OtherPaymentServiceTest {

    @Mock
    private BizOtherPaymentMapper otherPaymentMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private OtherPaymentService service;

    @Test
    @DisplayName("page - 正常分页返回记录")
    void page_returnsRecords() {
        BizOtherPayment record = new BizOtherPayment();
        record.setProjectId(1L);
        Page<BizOtherPayment> page = new Page<>(1, 10);
        page.setRecords(List.of(record));
        page.setTotal(1);
        when(otherPaymentMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<BizOtherPayment> result = service.page(1, 10, 1L);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("page - 空结果返回空列表")
    void page_empty() {
        Page<BizOtherPayment> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(otherPaymentMapper.selectPage(any(), any())).thenReturn(page);

        assertThat(service.page(1, 10, null).getRecords()).isEmpty();
    }

    @Test
    @DisplayName("save - 置 APPROVED 并回写项目其他总付款（null 按 0 起算）")
    void save_writesBackProjectTotal() {
        BizOtherPayment payment = new BizOtherPayment();
        payment.setProjectId(1L);
        payment.setPaymentAmount(new BigDecimal("500"));

        BizProject project = new BizProject();
        project.setId(1L);
        project.setTotalOtherPayment(null);
        when(projectMapper.selectById(1L)).thenReturn(project);

        service.save(payment);

        assertThat(payment.getStatus()).isEqualTo("APPROVED");
        verify(otherPaymentMapper).insert(payment);
        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalOtherPayment()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("save - 项目已有其他付款时叠加")
    void save_addsToExistingTotal() {
        BizOtherPayment payment = new BizOtherPayment();
        payment.setProjectId(1L);
        payment.setPaymentAmount(new BigDecimal("120"));

        BizProject project = new BizProject();
        project.setId(1L);
        project.setTotalOtherPayment(new BigDecimal("880"));
        when(projectMapper.selectById(1L)).thenReturn(project);

        service.save(payment);

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalOtherPayment()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("save - 项目不存在时仅落付款记录不回写")
    void save_projectMissing_noWriteBack() {
        BizOtherPayment payment = new BizOtherPayment();
        payment.setProjectId(99L);
        payment.setPaymentAmount(new BigDecimal("100"));
        when(projectMapper.selectById(99L)).thenReturn(null);

        service.save(payment);

        verify(otherPaymentMapper).insert(payment);
        verify(projectMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("save - 金额负/零/null 拒绝（P0 FIN-OPT-03：防污染项目其他总支付与 NPE）")
    void save_invalidAmount_rejected() {
        BizOtherPayment neg = new BizOtherPayment();
        neg.setPaymentAmount(new BigDecimal("-50"));
        assertThatThrownBy(() -> service.save(neg))
                .isInstanceOf(BusinessException.class).hasMessageContaining("付款金额必须大于0");

        BizOtherPayment zero = new BizOtherPayment();
        zero.setPaymentAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.save(zero))
                .isInstanceOf(BusinessException.class).hasMessageContaining("付款金额必须大于0");

        BizOtherPayment nullAmount = new BizOtherPayment();
        nullAmount.setPaymentAmount(null);
        assertThatThrownBy(() -> service.save(nullAmount))
                .isInstanceOf(BusinessException.class).hasMessageContaining("付款金额必须大于0");

        verify(otherPaymentMapper, never()).insert(any());
    }
}
