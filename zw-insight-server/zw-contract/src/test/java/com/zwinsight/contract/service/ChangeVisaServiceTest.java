package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizChangeVisa;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizChangeVisaMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ChangeVisaService 单元测试
 * <p>变更签证：提交审批即置 APPROVED，回写合同累计变更金额。</p>
 */
@ExtendWith(MockitoExtension.class)
class ChangeVisaServiceTest {

    @Mock
    private BizChangeVisaMapper changeVisaMapper;

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private ChangeVisaService service;

    private BizChangeVisa visa(String status) {
        BizChangeVisa v = new BizChangeVisa();
        v.setId(1L);
        v.setProjectId(10L);
        v.setContractId(20L);
        v.setChangeAmount(new BigDecimal("8000"));
        v.setStatus(status);
        return v;
    }

    @Test
    @DisplayName("page - 分页透传")
    void page_delegates() {
        Page<BizChangeVisa> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(visa("DRAFT")));
        page.setTotal(1L);
        when(changeVisaMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizChangeVisa> result = service.page(1, 10, 10L, 20L, "DESIGN");

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("save - 置 DRAFT")
    void save_setsDraft() {
        BizChangeVisa v = visa(null);

        service.save(v);

        assertThat(v.getStatus()).isEqualTo("DRAFT");
        verify(changeVisaMapper).insert(v);
    }

    @Test
    @DisplayName("submit - 守卫：不存在/非草稿抛异常")
    void submit_guardCases_throws() {
        when(changeVisaMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.submit(1L)).hasMessageContaining("变更签证不存在");

        when(changeVisaMapper.selectById(2L)).thenReturn(visa("APPROVED"));
        assertThatThrownBy(() -> service.submit(2L)).hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("submit - 正常：APPROVED + 合同累计变更金额累加（null 视为 0）")
    void submit_success_writesBackContract() {
        BizChangeVisa v = visa("DRAFT");
        when(changeVisaMapper.selectById(1L)).thenReturn(v);
        when(approvalService.startProcess(eq("CHANGE_VISA"), eq(1L),
                eq("change_visa_approval"), anyMap())).thenReturn("proc-1");
        BizConstructionContract contract = new BizConstructionContract();
        contract.setCumulativeChangeAmount(null);
        when(contractMapper.selectById(20L)).thenReturn(contract);

        service.submit(1L);

        assertThat(v.getStatus()).isEqualTo("APPROVED");
        verify(contractMapper).updateById(argThat(c ->
                c.getCumulativeChangeAmount().compareTo(new BigDecimal("8000")) == 0));
    }

    @Test
    @DisplayName("submit - 变更金额 null 时不抛 NPE 按 0 累加（P0 VIS-05）")
    void submit_nullChangeAmount_noNpe() {
        BizChangeVisa v = visa("DRAFT");
        v.setChangeAmount(null);
        when(changeVisaMapper.selectById(1L)).thenReturn(v);
        BizConstructionContract contract = new BizConstructionContract();
        contract.setCumulativeChangeAmount(new BigDecimal("500"));
        when(contractMapper.selectById(20L)).thenReturn(contract);

        service.submit(1L);

        // 累计变更 500 + 0 = 500，无 NPE
        verify(contractMapper).updateById(argThat(c ->
                c.getCumulativeChangeAmount().compareTo(new BigDecimal("500")) == 0));
    }

    @Test
    @DisplayName("submit - 合同不存在时不回写但不报错")
    void submit_contractMissing_skipsWriteBack() {
        BizChangeVisa v = visa("DRAFT");
        when(changeVisaMapper.selectById(1L)).thenReturn(v);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap())).thenReturn("proc-1");
        when(contractMapper.selectById(20L)).thenReturn(null);

        service.submit(1L);

        assertThat(v.getStatus()).isEqualTo("APPROVED");
        verify(contractMapper, never()).updateById(any());
    }
}
