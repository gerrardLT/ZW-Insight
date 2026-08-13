package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizVehicle;
import com.zwinsight.hr.domain.BizVehicleApply;
import com.zwinsight.hr.mapper.BizVehicleApplyMapper;
import com.zwinsight.hr.mapper.BizVehicleMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 车辆申请服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleApplyService {

    private final BizVehicleApplyMapper vehicleApplyMapper;
    private final BizVehicleMapper vehicleMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizVehicleApply> page(int page, int size, Long vehicleId) {
        Page<BizVehicleApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizVehicleApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(vehicleId != null, BizVehicleApply::getVehicleId, vehicleId)
                .orderByDesc(BizVehicleApply::getCreatedAt);
        Page<BizVehicleApply> result = vehicleApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增车辆申请
     */
    public void save(BizVehicleApply apply) {
        apply.setStatus("DRAFT");
        vehicleApplyMapper.insert(apply);
    }

    /**
     * 提交车辆申请（DRAFT→SUBMITTED 中间态，审批通过后由 onApproved 置车辆 IN_USE）
     * <p>
     * P1 修复（2026-08-13，批次三取证枚举）：原实现提交即置 APPROVED 且未等审批
     * 直接将车辆置 IN_USE，审批驳回后车辆状态无法回退。改为审批后生效模式。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizVehicleApply apply = vehicleApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("车辆申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("plateNumber", apply.getPlateNumber());
        variables.put("vehicleId", apply.getVehicleId());
        approvalService.startProcess(
                "VEHICLE_APPLY", id, "vehicle_apply_approval", variables);

        apply.setStatus("SUBMITTED");
        vehicleApplyMapper.updateById(apply);
    }

    /**
     * 审批通过回调：SUBMITTED→APPROVED 并置车辆 IN_USE（幂等：非 SUBMITTED 跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizVehicleApply apply = vehicleApplyMapper.selectById(id);
        if (apply == null) {
            log.warn("车辆审批通过回调：申请不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(apply.getStatus())) {
            log.info("车辆审批通过回调：非待审批状态跳过, id={}, status={}", id, apply.getStatus());
            return;
        }
        apply.setStatus("APPROVED");
        vehicleApplyMapper.updateById(apply);

        // 更新车辆状态为IN_USE
        BizVehicle vehicle = vehicleMapper.selectById(apply.getVehicleId());
        if (vehicle != null) {
            vehicle.setVehicleStatus("IN_USE");
            vehicleMapper.updateById(vehicle);
        } else {
            log.warn("车辆审批通过：车辆不存在, vehicleId={}", apply.getVehicleId());
        }
        log.info("车辆审批通过，车辆已置 IN_USE: applyId={}, vehicleId={}", id, apply.getVehicleId());
    }

    /**
     * 审批驳回回调：SUBMITTED→DRAFT（幂等：非 SUBMITTED 跳过）
     */
    public void onRejected(Long id) {
        BizVehicleApply apply = vehicleApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("DRAFT");
        vehicleApplyMapper.updateById(apply);
        log.info("车辆审批驳回，申请回退草稿: applyId={}", id);
    }
}
