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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 车辆申请服务
 */
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
     * 提交车辆申请（审批→更新车辆状态为IN_USE）
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
        String processInstanceId = approvalService.startProcess(
                "VEHICLE_APPLY", id, "vehicle_apply_approval", variables);

        apply.setStatus("APPROVED");
        vehicleApplyMapper.updateById(apply);

        // 更新车辆状态为IN_USE
        BizVehicle vehicle = vehicleMapper.selectById(apply.getVehicleId());
        if (vehicle != null) {
            vehicle.setVehicleStatus("IN_USE");
            vehicleMapper.updateById(vehicle);
        }
    }
}
