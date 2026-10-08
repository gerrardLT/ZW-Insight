package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborRewardPunish;
import com.zwinsight.labor.mapper.BizLaborRewardPunishMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LaborRewardPunishService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LaborRewardPunishServiceTest {

    @Mock
    private BizLaborRewardPunishMapper rewardPunishMapper;

    @Mock
    private com.zwinsight.workflow.service.ApprovalService approvalService;

    @InjectMocks
    private LaborRewardPunishService service;

    @Test
    @DisplayName("page - 分页查询透传")
    void page_delegates() {
        Page<BizLaborRewardPunish> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(new BizLaborRewardPunish()));
        page.setTotal(1L);
        when(rewardPunishMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizLaborRewardPunish> result = service.page(1, 10, 1L, 2L);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("save - 委托插入")
    void save_delegates() {
        BizLaborRewardPunish rp = new BizLaborRewardPunish();
        rp.setAmount(new java.math.BigDecimal("1000"));
        rp.setRpType("REWARD");

        service.save(rp);

        verify(rewardPunishMapper).insert(rp);
        assertThat(rp.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("submit - 提交审批")
    void submit_delegates() {
        BizLaborRewardPunish rp = new BizLaborRewardPunish();
        rp.setId(1L);
        rp.setAmount(new java.math.BigDecimal("1000"));
        rp.setRpType("REWARD");
        rp.setStatus("DRAFT");
        when(rewardPunishMapper.selectById(1L)).thenReturn(rp);
        when(approvalService.startProcess(eq("LABOR_REWARD_PUNISH"), eq(1L), eq("labor_reward_punish_approval"), any()))
                .thenReturn("proc-rp-123");

        service.submit(1L);

        assertThat(rp.getStatus()).isEqualTo("SUBMITTED");
        assertThat(rp.getWorkflowInstanceId()).isEqualTo("proc-rp-123");
        verify(rewardPunishMapper).updateById(rp);
    }

    @Test
    @DisplayName("onApproved - 审批通过生效")
    void onApproved_delegates() {
        BizLaborRewardPunish rp = new BizLaborRewardPunish();
        rp.setId(1L);
        rp.setStatus("SUBMITTED");
        when(rewardPunishMapper.selectById(1L)).thenReturn(rp);

        service.onApproved(1L);

        assertThat(rp.getStatus()).isEqualTo("APPROVED");
        verify(rewardPunishMapper).updateById(rp);
    }

    @Test
    @DisplayName("delete - 委托删除")
    void delete_delegates() {
        BizLaborRewardPunish rp = new BizLaborRewardPunish();
        rp.setId(1L);
        rp.setStatus("DRAFT");
        when(rewardPunishMapper.selectById(1L)).thenReturn(rp);

        service.delete(1L);

        verify(rewardPunishMapper).deleteById(1L);
    }
}
