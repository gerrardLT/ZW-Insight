package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizVehicleMaintenance;
import com.zwinsight.hr.mapper.BizVehicleMaintenanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 车辆维保服务
 */
@Service
@RequiredArgsConstructor
public class VehicleMaintenanceService {

    private final BizVehicleMaintenanceMapper maintenanceMapper;

    /**
     * 分页查询
     */
    public PageResult<BizVehicleMaintenance> page(int page, int size, Long vehicleId) {
        Page<BizVehicleMaintenance> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizVehicleMaintenance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(vehicleId != null, BizVehicleMaintenance::getVehicleId, vehicleId)
                .orderByDesc(BizVehicleMaintenance::getMaintDate);
        Page<BizVehicleMaintenance> result = maintenanceMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增维保记录
     */
    public void save(BizVehicleMaintenance maintenance) {
        maintenanceMapper.insert(maintenance);
    }
}
