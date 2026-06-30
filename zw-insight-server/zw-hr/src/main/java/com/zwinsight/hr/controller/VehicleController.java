package com.zwinsight.hr.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.hr.domain.BizVehicle;
import com.zwinsight.hr.domain.BizVehicleApply;
import com.zwinsight.hr.domain.BizVehicleMaintenance;
import com.zwinsight.hr.service.VehicleApplyService;
import com.zwinsight.hr.service.VehicleMaintenanceService;
import com.zwinsight.hr.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 车辆管理接口
 */
@RestController
@RequestMapping("/api/v1/hr/vehicle")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleApplyService vehicleApplyService;
    private final VehicleMaintenanceService maintenanceService;

    // ===== 车辆信息 =====

    @GetMapping("/page")
    public R<PageResult<BizVehicle>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String plateNumber) {
        return R.ok(vehicleService.page(page, size, plateNumber));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizVehicle vehicle) {
        vehicleService.save(vehicle);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizVehicle vehicle) {
        vehicle.setId(id);
        vehicleService.update(vehicle);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        vehicleService.delete(id);
        return R.ok();
    }

    // ===== 车辆申请 =====

    @GetMapping("/apply")
    public R<PageResult<BizVehicleApply>> applyPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long vehicleId) {
        return R.ok(vehicleApplyService.page(page, size, vehicleId));
    }

    @PostMapping("/apply")
    public R<Void> saveApply(@RequestBody BizVehicleApply apply) {
        vehicleApplyService.save(apply);
        return R.ok();
    }

    @PostMapping("/apply/{id}/submit")
    public R<Void> submitApply(@PathVariable Long id) {
        vehicleApplyService.submit(id);
        return R.ok();
    }

    // ===== 车辆维保 =====

    @GetMapping("/maintenance")
    public R<PageResult<BizVehicleMaintenance>> maintenancePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long vehicleId) {
        return R.ok(maintenanceService.page(page, size, vehicleId));
    }

    @PostMapping("/maintenance")
    public R<Void> saveMaintenance(@RequestBody BizVehicleMaintenance maintenance) {
        maintenanceService.save(maintenance);
        return R.ok();
    }
}
