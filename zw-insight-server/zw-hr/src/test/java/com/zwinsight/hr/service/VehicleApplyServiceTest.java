package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizVehicle;
import com.zwinsight.hr.domain.BizVehicleApply;
import com.zwinsight.hr.mapper.BizVehicleApplyMapper;
import com.zwinsight.hr.mapper.BizVehicleMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VehicleApplyService（车辆申请）单元测试
 *
 * 覆盖场景:
 * - 新增保存（DRAFT 状态）
 * - 提交的状态校验
 * - 提交发起审批流程并将车辆状态置为 IN_USE
 */
@ExtendWith(MockitoExtension.class)
class VehicleApplyServiceTest {

    @Mock
    private BizVehicleApplyMapper vehicleApplyMapper;

    @Mock
    private BizVehicleMapper vehicleMapper;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private VehicleApplyService vehicleApplyService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizVehicleApply.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizVehicle.class);
    }

    @Test
    @DisplayName("新增车辆申请：状态设置为 DRAFT")
    void save_setsDraftStatus() {
        BizVehicleApply apply = new BizVehicleApply();
        apply.setPlateNumber("苏A12345");

        vehicleApplyService.save(apply);

        assertThat(apply.getStatus()).isEqualTo("DRAFT");
        verify(vehicleApplyMapper).insert(apply);
    }

    @Test
    @DisplayName("提交车辆申请：不存在抛异常")
    void submit_notFound_throwsException() {
        when(vehicleApplyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> vehicleApplyService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("车辆申请不存在");
    }

    @Test
    @DisplayName("提交车辆申请：非草稿状态拒绝提交")
    void submit_nonDraft_rejected() {
        BizVehicleApply existing = new BizVehicleApply();
        existing.setId(1L);
        existing.setStatus("APPROVED");
        when(vehicleApplyMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> vehicleApplyService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");
    }

    @Test
    @DisplayName("提交车辆申请：置 SUBMITTED 中间态，不提前置车辆 IN_USE（P1 审批后生效修复）")
    void submit_success_setsSubmittedOnly() {
        BizVehicleApply apply = new BizVehicleApply();
        apply.setId(1L);
        apply.setVehicleId(10L);
        apply.setPlateNumber("苏A12345");
        apply.setStatus("DRAFT");
        when(vehicleApplyMapper.selectById(1L)).thenReturn(apply);
        when(approvalService.startProcess(anyString(), anyLong(), anyString(), anyMap()))
                .thenReturn("proc-1");

        vehicleApplyService.submit(1L);

        assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
        verify(vehicleApplyMapper).updateById(apply);
        verify(approvalService).startProcess(eq("VEHICLE_APPLY"), eq(1L),
                eq("vehicle_apply_approval"), anyMap());
        // 未审批不得置车辆 IN_USE
        verify(vehicleMapper, org.mockito.Mockito.never()).updateById(any(BizVehicle.class));
    }

    @Test
    @DisplayName("审批通过回调：SUBMITTED→APPROVED 并将车辆置为 IN_USE")
    void onApproved_success_setsVehicleInUse() {
        BizVehicleApply apply = new BizVehicleApply();
        apply.setId(1L);
        apply.setVehicleId(10L);
        apply.setStatus("SUBMITTED");
        when(vehicleApplyMapper.selectById(1L)).thenReturn(apply);
        BizVehicle vehicle = new BizVehicle();
        vehicle.setId(10L);
        vehicle.setVehicleStatus("IDLE");
        when(vehicleMapper.selectById(10L)).thenReturn(vehicle);

        vehicleApplyService.onApproved(1L);

        assertThat(apply.getStatus()).isEqualTo("APPROVED");
        ArgumentCaptor<BizVehicle> captor = ArgumentCaptor.forClass(BizVehicle.class);
        verify(vehicleMapper).updateById(captor.capture());
        assertThat(captor.getValue().getVehicleStatus()).isEqualTo("IN_USE");
    }

    @Test
    @DisplayName("审批通过回调：车辆不存在时仅告警；幂等（非 SUBMITTED 跳过）")
    void onApproved_vehicleNotFoundAndIdempotent() {
        BizVehicleApply apply = new BizVehicleApply();
        apply.setId(1L);
        apply.setVehicleId(10L);
        apply.setStatus("SUBMITTED");
        when(vehicleApplyMapper.selectById(1L)).thenReturn(apply);
        when(vehicleMapper.selectById(10L)).thenReturn(null);

        vehicleApplyService.onApproved(1L);

        assertThat(apply.getStatus()).isEqualTo("APPROVED");
        verify(vehicleMapper, org.mockito.Mockito.never()).updateById(any(BizVehicle.class));

        // 幂等：非 SUBMITTED 跳过
        vehicleApplyService.onApproved(1L);
        verify(vehicleApplyMapper, org.mockito.Mockito.times(1)).updateById(any());
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizVehicleApply> stubPage = new Page<>(1, 10);
        stubPage.setTotal(2);
        when(vehicleApplyMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizVehicleApply> result = vehicleApplyService.page(1, 10, 10L);

        assertThat(result.getTotal()).isEqualTo(2);
    }
}
