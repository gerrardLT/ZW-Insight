package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.finance.domain.BizFundTransfer;
import com.zwinsight.finance.mapper.BizFundTransferMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FundTransferService 单元测试
 * <p>跨项目资金调度：金额/项目守卫、审批回写（调出计支出、调入计收入、资金池不回写）、资金池总览。</p>
 */
@ExtendWith(MockitoExtension.class)
class FundTransferServiceTest {

    @Mock
    private BizFundTransferMapper fundTransferMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private SerialNumberService serialNumberService;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private FundTransferService service;

    private BizFundTransfer transfer(Long id, String status, Long from, Long to, String amount) {
        BizFundTransfer t = new BizFundTransfer();
        t.setId(id);
        t.setStatus(status);
        t.setFromProjectId(from);
        t.setToProjectId(to);
        t.setTransferAmount(amount == null ? null : new BigDecimal(amount));
        return t;
    }

    private BizProject project(Long id, String income, String expense) {
        BizProject p = new BizProject();
        p.setId(id);
        p.setProjectName("项目" + id);
        p.setTotalIncome(income == null ? null : new BigDecimal(income));
        p.setTotalExpense(expense == null ? null : new BigDecimal(expense));
        return p;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizFundTransfer> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(transfer(1L, "DRAFT", 1L, 2L, "100")));
        page.setTotal(1L);
        when(fundTransferMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizFundTransfer> result = service.page(1, 10, 1L, 2L);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("save - 守卫：金额非正/调出调入同项目抛异常；正常自动编号置 DRAFT")
    void save_variants() {
        assertThatThrownBy(() -> service.save(transfer(null, null, 1L, 2L, "0")))
                .hasMessageContaining("调拨金额必须大于0");
        assertThatThrownBy(() -> service.save(transfer(null, null, 1L, 2L, null)))
                .hasMessageContaining("调拨金额必须大于0");
        assertThatThrownBy(() -> service.save(transfer(null, null, 1L, 1L, "100")))
                .hasMessageContaining("调出和调入项目不能相同");

        when(serialNumberService.generate("FUND_TRANSFER")).thenReturn("FT-001");
        BizFundTransfer t = transfer(null, null, 1L, 2L, "100");
        service.save(t);
        assertThat(t.getTransferCode()).isEqualTo("FT-001");
        assertThat(t.getStatus()).isEqualTo("DRAFT");
        verify(fundTransferMapper).insert(t);
    }

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿抛异常")
    void submit_guardCases_throws() {
        when(fundTransferMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(99L)).hasMessageContaining("资金调度单不存在");

        when(fundTransferMapper.selectById(1L)).thenReturn(transfer(1L, "APPROVED", 1L, 2L, "100"));
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("submit - 正常：APPROVED + 调出项目计支出 + 调入项目计收入")
    void submit_success_writesBackBothProjects() {
        BizFundTransfer t = transfer(1L, "DRAFT", 10L, 20L, "5000");
        when(fundTransferMapper.selectById(1L)).thenReturn(t);
        when(approvalService.startProcess(eq("FUND_TRANSFER"), eq(1L),
                eq("fund_transfer_approval"), anyMap())).thenReturn("proc-1");
        BizProject from = project(10L, null, "1000");
        BizProject to = project(20L, "2000", null);
        when(projectMapper.selectById(10L)).thenReturn(from);
        when(projectMapper.selectById(20L)).thenReturn(to);

        service.submit(1L);

        assertThat(t.getStatus()).isEqualTo("APPROVED");
        assertThat(from.getTotalExpense()).isEqualByComparingTo("6000"); // 1000+5000
        assertThat(to.getTotalIncome()).isEqualByComparingTo("7000");   // 2000+5000
    }

    @Test
    @DisplayName("submit - 公司资金池（fromProjectId=null）不回写项目表")
    void submit_poolSource_noWriteBack() {
        BizFundTransfer t = transfer(1L, "DRAFT", null, 20L, "5000");
        when(fundTransferMapper.selectById(1L)).thenReturn(t);
        when(approvalService.startProcess(anyString(), any(), anyString(), anyMap())).thenReturn("proc-1");
        when(projectMapper.selectById(20L)).thenReturn(null); // 调入项目也不存在

        service.submit(1L);

        assertThat(t.getStatus()).isEqualTo("APPROVED");
        verify(projectMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("getFundPoolOverview - 汇总各项目资金与池余额")
    void getFundPoolOverview_aggregates() {
        BizProject p1 = project(1L, "10000", "4000");
        BizProject p2 = project(2L, null, "1000"); // null 收入按 0
        when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(p1, p2));

        Map<String, Object> overview = service.getFundPoolOverview();

        assertThat((BigDecimal) overview.get("totalIncome")).isEqualByComparingTo("10000");
        assertThat((BigDecimal) overview.get("totalExpense")).isEqualByComparingTo("5000");
        assertThat((BigDecimal) overview.get("poolBalance")).isEqualByComparingTo("5000");
        assertThat(overview.get("projectCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> funds = (List<Map<String, Object>>) overview.get("projectFunds");
        assertThat(funds).hasSize(2);
        assertThat((BigDecimal) funds.get(0).get("balance")).isEqualByComparingTo("6000");
        assertThat((BigDecimal) funds.get(1).get("balance")).isEqualByComparingTo("-1000");
    }

    @Test
    @DisplayName("delete - 守卫与正常删除")
    void delete_variants() {
        when(fundTransferMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L)).hasMessageContaining("资金调度单不存在");

        when(fundTransferMapper.selectById(1L)).thenReturn(transfer(1L, "APPROVED", 1L, 2L, "100"));
        assertThatThrownBy(() -> service.delete(1L)).hasMessageContaining("仅草稿状态可删除");

        when(fundTransferMapper.selectById(2L)).thenReturn(transfer(2L, "DRAFT", 1L, 2L, "100"));
        service.delete(2L);
        verify(fundTransferMapper).deleteById(2L);
    }
}
