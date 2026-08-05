package com.zwinsight.purchase.portal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizInquiryItem;
import com.zwinsight.purchase.domain.BizInquirySupplier;
import com.zwinsight.purchase.mapper.BizInquiryItemMapper;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizInquirySupplierMapper;
import com.zwinsight.purchase.portal.dto.PublicInquiryDetailVO;
import com.zwinsight.purchase.portal.dto.PublicInquiryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SupplierInquiryService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SupplierInquiryServiceTest {

    @Mock
    private BizInquiryMapper inquiryMapper;

    @Mock
    private BizInquiryItemMapper inquiryItemMapper;

    @Mock
    private BizInquirySupplierMapper inquirySupplierMapper;

    @InjectMocks
    private SupplierInquiryService service;

    private BizInquiry inquiry(String inviteMode, String status) {
        BizInquiry i = new BizInquiry();
        i.setId(1L);
        i.setTitle("测试询价");
        i.setInviteMode(inviteMode);
        i.setStatus(status);
        i.setPublishTime(LocalDateTime.now().minusDays(1));
        return i;
    }

    // ── listMyInquiries ──────────────────────────────────

    @Test
    @DisplayName("listMyInquiries - 无邀请记录返回空分页")
    void listMyInquiries_noInvitation_returnsEmpty() {
        when(inquirySupplierMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        PageResult<BizInquiry> result = service.listMyInquiries(100L, 1, 10);

        assertThat(result).isNotNull();
        verify(inquiryMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listMyInquiries - 有邀请则分页查询")
    void listMyInquiries_withInvitation_pages() {
        BizInquirySupplier inv = new BizInquirySupplier();
        inv.setInquiryId(1L);
        when(inquirySupplierMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(inv));
        Page<BizInquiry> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(inquiry("INVITED", "PUBLISHED")));
        page.setTotal(1L);
        when(inquiryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizInquiry> result = service.listMyInquiries(100L, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    // ── getInquiryDetail ──────────────────────────────────

    @Test
    @DisplayName("getInquiryDetail - 未被邀请/询价单不存在抛异常")
    void getInquiryDetail_permissionAndNotFound_throws() {
        when(inquirySupplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertThatThrownBy(() -> service.getInquiryDetail(100L, 1L))
                .hasMessageContaining("您无权查看此询价单");

        when(inquirySupplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(inquiryMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.getInquiryDetail(100L, 1L))
                .hasMessageContaining("询价单不存在");
    }

    @Test
    @DisplayName("getInquiryDetail - 正常返回询价与物料明细")
    void getInquiryDetail_success_returnsMap() {
        when(inquirySupplierMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("INVITED", "PUBLISHED"));
        BizInquiryItem item = new BizInquiryItem();
        item.setMaterialName("螺纹钢");
        when(inquiryItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(item));

        Map<String, Object> detail = service.getInquiryDetail(100L, 1L);

        assertThat(detail).containsKeys("inquiry", "items");
        assertThat((List<?>) detail.get("items")).hasSize(1);
    }

    // ── listPublicInquiries ──────────────────────────────────

    @Test
    @DisplayName("listPublicInquiries - 转换为 VO 并保留分页信息")
    void listPublicInquiries_convertsToVo() {
        Page<BizInquiry> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(inquiry("PUBLIC", "OPEN")));
        page.setTotal(1L);
        when(inquiryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<PublicInquiryVO> result = service.listPublicInquiries(1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTitle()).isEqualTo("测试询价");
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    // ── getPublicInquiryDetail ──────────────────────────────────

    @Test
    @DisplayName("getPublicInquiryDetail - 不存在/非公开/状态不允许分别抛异常")
    void getPublicInquiryDetail_guardCases_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.getPublicInquiryDetail(1L))
                .hasMessageContaining("询价单不存在");

        when(inquiryMapper.selectById(2L)).thenReturn(inquiry("INVITED", "OPEN"));
        assertThatThrownBy(() -> service.getPublicInquiryDetail(2L))
                .hasMessageContaining("非公开询价");

        when(inquiryMapper.selectById(3L)).thenReturn(inquiry("PUBLIC", "AWARDED"));
        assertThatThrownBy(() -> service.getPublicInquiryDetail(3L))
                .hasMessageContaining("当前状态不可查看");
    }

    @Test
    @DisplayName("getPublicInquiryDetail - 正常返回详情含物料 VO")
    void getPublicInquiryDetail_success() {
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry("PUBLIC", "OPEN"));
        BizInquiryItem item = new BizInquiryItem();
        item.setMaterialName("螺纹钢");
        item.setUnit("吨");
        when(inquiryItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(item));

        PublicInquiryDetailVO vo = service.getPublicInquiryDetail(1L);

        assertThat(vo.getTitle()).isEqualTo("测试询价");
        assertThat(vo.getItems()).hasSize(1);
        assertThat(vo.getItems().get(0).getMaterialName()).isEqualTo("螺纹钢");
    }

    // ── checkDeadline ──────────────────────────────────

    @Test
    @DisplayName("checkDeadline - 不存在/非公开/状态不允许抛异常")
    void checkDeadline_guardCases_throws() {
        when(inquiryMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.checkDeadline(1L)).hasMessageContaining("询价单不存在");

        when(inquiryMapper.selectById(2L)).thenReturn(inquiry("INVITED", "OPEN"));
        assertThatThrownBy(() -> service.checkDeadline(2L)).hasMessageContaining("非公开询价");

        when(inquiryMapper.selectById(3L)).thenReturn(inquiry("PUBLIC", "AWARDED"));
        assertThatThrownBy(() -> service.checkDeadline(3L)).hasMessageContaining("状态不允许报价");
    }

    @Test
    @DisplayName("checkDeadline - 已过 deadline 抛'报价已截止'")
    void checkDeadline_pastDeadline_throws() {
        BizInquiry inq = inquiry("PUBLIC", "OPEN");
        inq.setDeadline(LocalDateTime.now().minusHours(1));
        when(inquiryMapper.selectById(1L)).thenReturn(inq);

        assertThatThrownBy(() -> service.checkDeadline(1L)).hasMessageContaining("报价已截止");
    }

    @Test
    @DisplayName("checkDeadline - 无 deadline 时按发布后 7 天兜底：超期抛异常，未超期通过")
    void checkDeadline_fallbackSevenDays() {
        BizInquiry overdue = inquiry("PUBLIC", "OPEN");
        overdue.setDeadline(null);
        overdue.setPublishTime(LocalDateTime.now().minusDays(8));
        when(inquiryMapper.selectById(1L)).thenReturn(overdue);
        assertThatThrownBy(() -> service.checkDeadline(1L)).hasMessageContaining("报价已截止");

        BizInquiry active = inquiry("PUBLIC", "OPEN");
        active.setDeadline(null);
        active.setPublishTime(LocalDateTime.now().minusDays(2));
        when(inquiryMapper.selectById(2L)).thenReturn(active);
        service.checkDeadline(2L); // 不抛异常即通过
    }
}
