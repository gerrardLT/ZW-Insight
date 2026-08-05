package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizQuotationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PublicQuotationService 单元测试（公开询价免登录场景，重点验证脱敏与身份校验）
 */
@ExtendWith(MockitoExtension.class)
class PublicQuotationServiceTest {

    @Mock
    private BizInquiryMapper inquiryMapper;

    @Mock
    private BizQuotationMapper quotationMapper;

    @InjectMocks
    private PublicQuotationService service;

    private BizInquiry inquiry(String inviteMode, String status) {
        BizInquiry i = new BizInquiry();
        i.setId(1L);
        i.setTitle("公开询价");
        i.setInviteMode(inviteMode);
        i.setStatus(status);
        i.setAwardMethod("LOWEST");
        return i;
    }

    @Test
    @DisplayName("listPublicInquiries - 脱敏转换仅返回公开字段")
    void listPublicInquiries_sanitizedFields() {
        Page<BizInquiry> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(inquiry("PUBLIC", "PUBLISHED")));
        page.setTotal(1L);
        when(inquiryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<Map<String, Object>> result = service.listPublicInquiries(1, 10, "公开");

        assertThat(result.getRecords()).hasSize(1);
        Map<String, Object> item = result.getRecords().get(0);
        assertThat(item).containsKeys("id", "title", "status", "deadline", "awardMethod", "createdAt");
    }

    @Test
    @DisplayName("getPublicInquiryDetail - 不存在/非公开抛异常，正常返回详情")
    void getPublicInquiryDetail_guardAndSuccess() {
        when(inquiryMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.getPublicInquiryDetail(1L)).hasMessageContaining("询价不存在");

        when(inquiryMapper.selectById(2L)).thenReturn(inquiry("INVITED", "PUBLISHED"));
        assertThatThrownBy(() -> service.getPublicInquiryDetail(2L)).hasMessageContaining("不支持公开查看");

        when(inquiryMapper.selectById(3L)).thenReturn(inquiry("PUBLIC", "PUBLISHED"));
        Map<String, Object> detail = service.getPublicInquiryDetail(3L);
        assertThat(detail.get("title")).isEqualTo("公开询价");
    }

    @Test
    @DisplayName("getAwardAnnouncement - 未公示状态抛异常，AWARDED 正常返回中标信息")
    void getAwardAnnouncement_guardAndSuccess() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("PUBLIC", "PUBLISHED"));
        assertThatThrownBy(() -> service.getAwardAnnouncement(1L)).hasMessageContaining("尚未公示中标结果");

        BizInquiry awarded = inquiry("PUBLIC", "AWARDED");
        awarded.setWinnerName("中标供应商");
        awarded.setWinnerAmount(new BigDecimal("8888"));
        when(inquiryMapper.selectById(2L)).thenReturn(awarded);
        Map<String, Object> announcement = service.getAwardAnnouncement(2L);
        assertThat(announcement.get("winnerName")).isEqualTo("中标供应商");
        assertThat(announcement.get("inquiryId")).isEqualTo(2L);
    }

    @Test
    @DisplayName("submitPublicQuotation - 询价守卫：不存在/非公开/已截止")
    void submitPublicQuotation_inquiryGuards_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.submitPublicQuotation(1L, new HashMap<>()))
                .hasMessageContaining("询价不存在");

        when(inquiryMapper.selectById(2L)).thenReturn(inquiry("INVITED", "PUBLISHED"));
        assertThatThrownBy(() -> service.submitPublicQuotation(2L, new HashMap<>()))
                .hasMessageContaining("不支持公开报价");

        when(inquiryMapper.selectById(3L)).thenReturn(inquiry("PUBLIC", "AWARDED"));
        assertThatThrownBy(() -> service.submitPublicQuotation(3L, new HashMap<>()))
                .hasMessageContaining("已截止报价");
    }

    @Test
    @DisplayName("submitPublicQuotation - 入参守卫：手机号/供应商名为空、同手机号重复报价")
    void submitPublicQuotation_inputGuards_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("PUBLIC", "PUBLISHED"));

        Map<String, Object> noPhone = new HashMap<>();
        assertThatThrownBy(() -> service.submitPublicQuotation(1L, noPhone))
                .hasMessageContaining("手机号不能为空");

        Map<String, Object> noName = new HashMap<>();
        noName.put("phone", "13800138000");
        assertThatThrownBy(() -> service.submitPublicQuotation(1L, noName))
                .hasMessageContaining("供应商名称不能为空");

        Map<String, Object> ok = new HashMap<>();
        ok.put("phone", "13800138000");
        ok.put("supplierName", "供应商");
        when(quotationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.submitPublicQuotation(1L, ok))
                .hasMessageContaining("不可重复报价");
    }

    @Test
    @DisplayName("submitPublicQuotation - 正常提交：金额转换、来源标记 PUBLIC")
    void submitPublicQuotation_success() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("PUBLIC", "PUBLISHED"));
        when(quotationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> req = new HashMap<>();
        req.put("phone", "13800138000");
        req.put("supplierName", "公开供应商");
        req.put("totalAmount", "12345.67");
        service.submitPublicQuotation(1L, req);

        verify(quotationMapper).insert(argThat(q ->
                q.getTotalAmount().compareTo(new BigDecimal("12345.67")) == 0
                        && "PUBLIC".equals(q.getQuotationSource())
                        && "SUBMITTED".equals(q.getStatus())
                        && "13800138000".equals(q.getSupplierPhone())));
    }

    @Test
    @DisplayName("getMyQuotation - 手机号为空抛异常；无记录返回空 Map；有记录返回报价信息")
    void getMyQuotation_variants() {
        assertThatThrownBy(() -> service.getMyQuotation(1L, ""))
                .hasMessageContaining("手机号不能为空");

        when(quotationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThat(service.getMyQuotation(1L, "13800138000")).isEmpty();

        BizQuotation q = new BizQuotation();
        q.setId(9L);
        q.setSupplierName("供应商");
        q.setTotalAmount(new BigDecimal("100"));
        q.setStatus("SUBMITTED");
        when(quotationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(q);
        Map<String, Object> result = service.getMyQuotation(1L, "13800138000");
        assertThat(result.get("id")).isEqualTo(9L);
        assertThat(result.get("totalAmount")).isEqualTo(new BigDecimal("100"));
    }
}
