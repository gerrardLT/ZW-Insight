package com.zwinsight.hr.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizVehicle;
import com.zwinsight.hr.mapper.BizVehicleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 车辆管理服务
 */
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final BizVehicleMapper vehicleMapper;

    /**
     * 分页查询
     */
    public PageResult<BizVehicle> page(int page, int size, String plateNumber) {
        Page<BizVehicle> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizVehicle> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(plateNumber), BizVehicle::getPlateNumber, plateNumber)
                .orderByDesc(BizVehicle::getCreatedAt);
        Page<BizVehicle> result = vehicleMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增车辆
     */
    public void save(BizVehicle vehicle) {
        if (vehicle.getVehicleStatus() == null) {
            vehicle.setVehicleStatus("IDLE");
        }
        vehicleMapper.insert(vehicle);
    }

    /**
     * 更新车辆
     */
    public void update(BizVehicle vehicle) {
        BizVehicle existing = vehicleMapper.selectById(vehicle.getId());
        if (existing == null) {
            throw new BusinessException("车辆不存在");
        }
        vehicleMapper.updateById(vehicle);
    }

    /**
     * 删除车辆
     */
    public void delete(Long id) {
        vehicleMapper.deleteById(id);
    }
}
