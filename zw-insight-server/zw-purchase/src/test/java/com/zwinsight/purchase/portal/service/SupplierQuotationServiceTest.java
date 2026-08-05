package com.zwinsight.purchase.portal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizInquirySupplier;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.domain.BizQuotationDetail;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizInquirySupplierMapper;
import com.zwinsight.purchase.mapper.BizQuotationDetailMapper;
import com.zwinsight.purchase.mapper.BizQuotationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SupplierQuotationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SupplierQuotationServiceTest {

    @Mock
    private BizQuotationMapper quotationMapper;

    @Mock
    private BizQuotationDetailMapper quotationDetailMapper;

    @Mock
    private BizInquiryMapper inquiryMapper;

    @Mock
    private BizInquirySupplierMapper inquirySupplierMapper;

    @InjectMocks
    private SupplierQuotationService service;

    private BizInquiry inquiry(String status) {
        BizInquiry i = new BizInquiry();
        i.setId(1L);
        i.setStatus(status);
        i.setInviteMode("INVITED");
        i.setDeadline(LocalDateTime.now().plusDays(3));
        return i;
    }

    private BizQuotationDetail detail(String totalPrice, String unitPrice) {
        BizQuotationDetail d = new BizQuotationDetail();
        d.setTotalPrice(totalPrice == null ? null : new BigDecimal(totalPrice));
        d.setUnitPrice(unitPrice == null ? null : new BigDecimal(unitPrice));
        return d;
    }

    // ── submitQuotation ──────────────────────────────────

    @Test
    @DisplayName("submitQuotation - 询价单不存在/状态不允许/已截止分别抛异常")
    void submitQuotation_guardCases_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.submitQuotation(100L, "供应商", 1L, Collections.emptyList()))
                .hasMessageContaining("询价单不存在");

        when(inquiryMapper.selectById(2L)).thenReturn(inquiry("AWARDED"));
        assertThatThrownBy(() -> service.submitQuotation(100L, "供应商", 2L, Collections.emptyList()))
                .hasMessageContaining("状态不允许报价");

        BizInquiry overdue = inquiry("PUBLISHED");
        overdue.setDeadline(LocalDateTime.now().minusHours(1));
        when(inquiryMapper.selectById(3L)).thenReturn(overdue);
        assertThatThrownBy(() -> service.submitQuotation(100L, "供应商", 3L, Collections.emptyList()))
                .hasMessageContaining("报价已截止");
    }

    @Test
    @DisplayName("submitQuotation - 未被邀请/重复报价抛异常")
    void submitQuotation_invitationAndDuplicate_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("PUBLISHED"));
        when(inquirySupplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertThatThrownBy(() -> service.submitQuotation(100L, "供应商", 1L, Collections.emptyList()))
                .hasMessageContaining("您未被邀请参与此询价");

        when(inquirySupplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(quotationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.submitQuotation(100L, "供应商", 1L, Collections.emptyList()))
                .hasMessageContaining("不可重复提交");
    }

    @Test
    @DisplayName("submitQuotation - 正常提交：总额=totalPrice 优先、unitPrice 兜底，PUBLISHED 转 QUOTED")
    void submitQuotation_success_computesTotalAndUpdatesStatus() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("PUBLISHED"));
        when(inquirySupplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(quotationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(inv -> {
            BizQuotation q = inv.getArgument(0);
            q.setId(77L);
            return 1;
        }).when(quotationMapper).insert(any(BizQuotation.class));

        service.submitQuotation(100L, "测试供应商", 1L,
                Arrays.asList(detail("1000.00", null), detail(null, "200.00"), detail(null, null)));

        ArgumentCaptor<BizQuotation> captor = ArgumentCaptor.forClass(BizQuotation.class);
        verify(quotationMapper).insert(captor.capture());
        BizQuotation saved = captor.getValue();
        // 1000(totalPrice) + 200(unitPrice 兜底) + 跳过 null = 1200
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("1200.00");
        assertThat(saved.getStatus()).isEqualTo("SUBMITTED");
        assertThat(saved.getSupplierId()).isEqualTo(100L);
        verify(quotationDetailMapper, times(3)).insert(argThat(d -> Long.valueOf(77L).equals(d.getQuotationId())));
        verify(inquiryMapper).updateById(argThat(i -> "QUOTED".equals(i.getStatus())));
    }

    @Test
    @DisplayName("submitQuotation - 询价单已 QUOTED 时不重复更新状态")
    void submitQuotation_alreadyQuoted_noStatusUpdate() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("QUOTED"));
        when(inquirySupplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(quotationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.submitQuotation(100L, "供应商", 1L, Collections.emptyList());

        verify(inquiryMapper, never()).updateById(any());
    }

    // ── myQuotations ──────────────────────────────────

    @Test
    @DisplayName("myQuotations - 分页查询透传")
    void myQuotations_pages() {
        Page<BizQuotation> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(new BizQuotation()));
        page.setTotal(1L);
        when(quotationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizQuotation> result = service.myQuotations(100L, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
    }

    // ── submitPublicQuotation ──────────────────────────────────

    @Test
    @DisplayName("submitPublicQuotation - 非公开询价/状态不允许抛异常")
    void submitPublicQuotation_guardCases_throws() {
        BizInquiry invited = inquiry("PUBLISHED");
        invited.setInviteMode("INVITED");
        when(inquiryMapper.selectById(1L)).thenReturn(invited);
        assertThatThrownBy(() -> service.submitPublicQuotation(100L, "供应商", 1L, Collections.emptyList()))
                .hasMessageContaining("非公开询价");

        BizInquiry awarded = inquiry("AWARDED");
        awarded.setInviteMode("PUBLIC");
        when(inquiryMapper.selectById(2L)).thenReturn(awarded);
        assertThatThrownBy(() -> service.submitPublicQuotation(100L, "供应商", 2L, Collections.emptyList()))
                .hasMessageContaining("状态不允许报价");
    }

    @Test
    @DisplayName("submitPublicQuotation - 未关联供应商时自动关联，OPEN 转 QUOTED")
    void submitPublicQuotation_success_autoLinksSupplier() {
        BizInquiry inq = inquiry("OPEN");
        inq.setInviteMode("PUBLIC");
        when(inquiryMapper.selectById(1L)).thenReturn(inq);
        when(quotationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(inquirySupplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.submitPublicQuotation(100L, "公开供应商", 1L,
                Collections.singletonList(detail("500.00", null)));

        verify(inquirySupplierMapper).insert(argThat(s ->
                Long.valueOf(100L).equals(s.getSupplierId()) && Long.valueOf(1L).equals(s.getInquiryId())));
        verify(quotationMapper).insert(argThat(q -> q.getTotalAmount().compareTo(new BigDecimal("500.00")) == 0));
        verify(inquiryMapper).updateById(argThat(i -> "QUOTED".equals(i.getStatus())));
    }

    @Test
    @DisplayName("submitPublicQuotation - 已关联供应商时不重复插入关联")
    void submitPublicQuotation_alreadyLinked_noDuplicate() {
        BizInquiry inq = inquiry("QUOTED");
        inq.setInviteMode("PUBLIC");
        when(inquiryMapper.selectById(1L)).thenReturn(inq);
        when(quotationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(inquirySupplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        service.submitPublicQuotation(100L, "供应商", 1L, Collections.emptyList());

        verify(inquirySupplierMapper, never()).insert(any(BizInquirySupplier.class));
        // QUOTED 状态不再更新
        verify(inquiryMapper, never()).updateById(any());
    }
}
