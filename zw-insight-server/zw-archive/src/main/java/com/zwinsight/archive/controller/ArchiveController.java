package com.zwinsight.archive.controller;

import com.zwinsight.archive.service.ArchiveService;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 档案管理接口
 * 档案为只读聚合视图，不产生新数据
 */
@RestController
@RequestMapping("/api/v1/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    /**
     * 项目档案
     */
    @GetMapping("/project/{projectId}")
    public R<Map<String, Object>> getProjectArchive(@PathVariable Long projectId) {
        return R.ok(archiveService.getProjectArchive(projectId));
    }

    /**
     * 投标档案
     */
    @GetMapping("/tender/{registerId}")
    public R<Map<String, Object>> getTenderArchive(@PathVariable Long registerId) {
        return R.ok(archiveService.getTenderArchive(registerId));
    }

    /**
     * 预算档案
     */
    @GetMapping("/budget/{projectId}")
    public R<Map<String, Object>> getBudgetArchive(@PathVariable Long projectId) {
        return R.ok(archiveService.getBudgetArchive(projectId));
    }

    /**
     * 合同档案
     */
    @GetMapping("/contract/{contractId}")
    public R<Map<String, Object>> getContractArchive(@PathVariable Long contractId) {
        return R.ok(archiveService.getContractArchive(contractId));
    }

    /**
     * 供应商档案
     */
    @GetMapping("/supplier/{supplierId}")
    public R<Map<String, Object>> getSupplierArchive(@PathVariable Long supplierId) {
        return R.ok(archiveService.getSupplierArchive(supplierId));
    }

    /**
     * 材料合同档案
     */
    @GetMapping("/material-contract/{contractId}")
    public R<Map<String, Object>> getMaterialContractArchive(@PathVariable Long contractId) {
        return R.ok(archiveService.getMaterialContractArchive(contractId));
    }

    /**
     * 分包档案
     */
    @GetMapping("/subcontract/{contractId}")
    public R<Map<String, Object>> getSubcontractArchive(@PathVariable Long contractId) {
        return R.ok(archiveService.getSubcontractArchive(contractId));
    }

    /**
     * 机械合同档案
     */
    @GetMapping("/machine-contract/{contractId}")
    public R<Map<String, Object>> getMachineContractArchive(@PathVariable Long contractId) {
        return R.ok(archiveService.getMachineContractArchive(contractId));
    }

    /**
     * 人事档案
     */
    @GetMapping("/personnel/{userId}")
    public R<Map<String, Object>> getPersonnelArchive(@PathVariable Long userId) {
        return R.ok(archiveService.getPersonnelArchive(userId));
    }

    /**
     * 车辆档案
     */
    @GetMapping("/vehicle/{vehicleId}")
    public R<Map<String, Object>> getVehicleArchive(@PathVariable Long vehicleId) {
        return R.ok(archiveService.getVehicleArchive(vehicleId));
    }
}
