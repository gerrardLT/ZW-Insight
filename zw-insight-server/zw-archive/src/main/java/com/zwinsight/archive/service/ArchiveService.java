package com.zwinsight.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.basedata.domain.BdSupplier;
import com.zwinsight.basedata.mapper.BdSupplierMapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.hr.domain.BizEntryApply;
import com.zwinsight.hr.domain.BizRegularApply;
import com.zwinsight.hr.domain.BizResignApply;
import com.zwinsight.hr.domain.BizVehicle;
import com.zwinsight.hr.domain.BizVehicleApply;
import com.zwinsight.hr.domain.BizVehicleMaintenance;
import com.zwinsight.hr.mapper.BizEntryApplyMapper;
import com.zwinsight.hr.mapper.BizRegularApplyMapper;
import com.zwinsight.hr.mapper.BizResignApplyMapper;
import com.zwinsight.hr.mapper.BizVehicleApplyMapper;
import com.zwinsight.hr.mapper.BizVehicleMaintenanceMapper;
import com.zwinsight.hr.mapper.BizVehicleMapper;
import com.zwinsight.machine.domain.BizMachineContract;
import com.zwinsight.machine.domain.BizMachineSettlement;
import com.zwinsight.machine.domain.BizMachineUsageRecord;
import com.zwinsight.machine.mapper.BizMachineContractMapper;
import com.zwinsight.machine.mapper.BizMachineSettlementMapper;
import com.zwinsight.machine.mapper.BizMachineUsageRecordMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseContractDetail;
import com.zwinsight.purchase.mapper.BizPurchaseContractDetailMapper;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractSettlementMapper;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.domain.BizOpenBidRecord;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.domain.BizTenderTask;
import com.zwinsight.tender.mapper.BizDepositApplyMapper;
import com.zwinsight.tender.mapper.BizOpenBidRecordMapper;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import com.zwinsight.tender.mapper.BizTenderTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一档案查询服务
 * 档案是只读视图，不产生新数据。每个档案是一个聚合方法，汇总多模块数据返回。
 */
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final BizProjectMapper projectMapper;
    private final BizProjectMemberMapper projectMemberMapper;
    private final BizConstructionContractMapper constructionContractMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizPaymentReceivedMapper paymentReceivedMapper;
    private final BizSubcontractMapper subcontractMapper;
    private final BizSubcontractSettlementMapper subcontractSettlementMapper;
    private final BizMachineContractMapper machineContractMapper;
    private final BizMachineSettlementMapper machineSettlementMapper;
    private final BizMachineUsageRecordMapper machineUsageRecordMapper;
    private final BizPurchaseContractMapper purchaseContractMapper;
    private final BizPurchaseContractDetailMapper purchaseContractDetailMapper;
    private final BizTenderRegisterMapper tenderRegisterMapper;
    private final BizTenderTaskMapper tenderTaskMapper;
    private final BizOpenBidRecordMapper openBidRecordMapper;
    private final BizDepositApplyMapper depositApplyMapper;
    private final BizBudgetMapper budgetMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final BdSupplierMapper supplierMapper;
    private final BizEntryApplyMapper entryApplyMapper;
    private final BizRegularApplyMapper regularApplyMapper;
    private final BizResignApplyMapper resignApplyMapper;
    private final BizVehicleMapper vehicleMapper;
    private final BizVehicleApplyMapper vehicleApplyMapper;
    private final BizVehicleMaintenanceMapper vehicleMaintenanceMapper;

    /**
     * 项目档案（项目信息+成员+合同+资金+施工过程+成本+分包+投标+保证金）
     */
    public Map<String, Object> getProjectArchive(Long projectId) {
        Map<String, Object> archive = new HashMap<>();

        // 项目基本信息
        BizProject project = projectMapper.selectById(projectId);
        archive.put("project", project);

        // 项目成员
        List<BizProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<BizProjectMember>()
                        .eq(BizProjectMember::getProjectId, projectId));
        archive.put("members", members);

        // 施工合同
        List<BizConstructionContract> contracts = constructionContractMapper.selectList(
                new LambdaQueryWrapper<BizConstructionContract>()
                        .eq(BizConstructionContract::getProjectId, projectId));
        archive.put("constructionContracts", contracts);

        // 付款记录
        List<BizPaymentApply> payments = paymentApplyMapper.selectList(
                new LambdaQueryWrapper<BizPaymentApply>()
                        .eq(BizPaymentApply::getProjectId, projectId)
                        .eq(BizPaymentApply::getStatus, "APPROVED"));
        archive.put("payments", payments);

        // 收款记录
        List<BizPaymentReceived> receivedList = paymentReceivedMapper.selectList(
                new LambdaQueryWrapper<BizPaymentReceived>()
                        .eq(BizPaymentReceived::getProjectId, projectId));
        archive.put("receivedPayments", receivedList);

        // 分包合同
        List<BizSubcontract> subcontracts = subcontractMapper.selectList(
                new LambdaQueryWrapper<BizSubcontract>()
                        .eq(BizSubcontract::getProjectId, projectId));
        archive.put("subcontracts", subcontracts);

        // 机械合同
        List<BizMachineContract> machineContracts = machineContractMapper.selectList(
                new LambdaQueryWrapper<BizMachineContract>()
                        .eq(BizMachineContract::getProjectId, projectId));
        archive.put("machineContracts", machineContracts);

        // 资金汇总
        Map<String, Object> fundSummary = new HashMap<>();
        fundSummary.put("totalIncome", project != null ? project.getTotalIncome() : BigDecimal.ZERO);
        fundSummary.put("totalExpense", project != null ? project.getTotalExpense() : BigDecimal.ZERO);
        fundSummary.put("contractAmount", project != null ? project.getContractAmount() : BigDecimal.ZERO);
        archive.put("fundSummary", fundSummary);

        return archive;
    }

    /**
     * 投标档案（报名+任务分配+开标记录+竞标明细+保证金）
     */
    public Map<String, Object> getTenderArchive(Long registerId) {
        Map<String, Object> archive = new HashMap<>();

        // 投标登记
        BizTenderRegister register = tenderRegisterMapper.selectById(registerId);
        archive.put("register", register);

        // 任务分配
        List<BizTenderTask> tasks = tenderTaskMapper.selectList(
                new LambdaQueryWrapper<BizTenderTask>()
                        .eq(BizTenderTask::getRegisterId, registerId));
        archive.put("tasks", tasks);

        // 开标记录
        List<BizOpenBidRecord> openBidRecords = openBidRecordMapper.selectList(
                new LambdaQueryWrapper<BizOpenBidRecord>()
                        .eq(BizOpenBidRecord::getRegisterId, registerId));
        archive.put("openBidRecords", openBidRecords);

        // 保证金
        List<BizDepositApply> deposits = depositApplyMapper.selectList(
                new LambdaQueryWrapper<BizDepositApply>()
                        .eq(BizDepositApply::getRegisterId, registerId));
        archive.put("deposits", deposits);

        return archive;
    }

    /**
     * 预算档案（预算编制+变更记录+各分类汇总）
     */
    public Map<String, Object> getBudgetArchive(Long projectId) {
        Map<String, Object> archive = new HashMap<>();

        // 预算列表
        List<BizBudget> budgets = budgetMapper.selectList(
                new LambdaQueryWrapper<BizBudget>()
                        .eq(BizBudget::getProjectId, projectId)
                        .orderByDesc(BizBudget::getCreatedAt));
        archive.put("budgets", budgets);

        // 预算明细
        if (!budgets.isEmpty()) {
            BizBudget latestBudget = budgets.get(0);
            List<BizBudgetDetail> details = budgetDetailMapper.selectList(
                    new LambdaQueryWrapper<BizBudgetDetail>()
                            .eq(BizBudgetDetail::getBudgetId, latestBudget.getId()));
            archive.put("budgetDetails", details);

            // 分类汇总
            Map<String, BigDecimal> categorySummary = new HashMap<>();
            for (BizBudgetDetail detail : details) {
                String category = detail.getCostCategory();
                BigDecimal amount = detail.getBudgetTotalPrice() != null ? detail.getBudgetTotalPrice() : BigDecimal.ZERO;
                categorySummary.merge(category, amount, BigDecimal::add);
            }
            archive.put("categorySummary", categorySummary);
        }

        return archive;
    }

    /**
     * 合同档案（合同登记+变更+补充协议）
     */
    public Map<String, Object> getContractArchive(Long contractId) {
        Map<String, Object> archive = new HashMap<>();

        // 主合同
        BizConstructionContract contract = constructionContractMapper.selectById(contractId);
        archive.put("contract", contract);

        // 变更/补充协议
        if (contract != null) {
            List<BizConstructionContract> changes = constructionContractMapper.selectList(
                    new LambdaQueryWrapper<BizConstructionContract>()
                            .eq(BizConstructionContract::getParentContractId, contractId)
                            .orderByAsc(BizConstructionContract::getCreatedAt));
            archive.put("changes", changes);
        }

        return archive;
    }

    /**
     * 供应商档案（基本信息+合作合同列表+联系人）
     */
    public Map<String, Object> getSupplierArchive(Long supplierId) {
        Map<String, Object> archive = new HashMap<>();

        // 基本信息
        BdSupplier supplier = supplierMapper.selectById(supplierId);
        archive.put("supplier", supplier);

        // 采购合同列表
        List<BizPurchaseContract> purchaseContracts = purchaseContractMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseContract>()
                        .eq(BizPurchaseContract::getPartyBId, supplierId));
        archive.put("purchaseContracts", purchaseContracts);

        // 分包合同列表
        List<BizSubcontract> subcontracts = subcontractMapper.selectList(
                new LambdaQueryWrapper<BizSubcontract>()
                        .eq(BizSubcontract::getSupplierId, supplierId));
        archive.put("subcontracts", subcontracts);

        // 机械合同列表
        List<BizMachineContract> machineContracts = machineContractMapper.selectList(
                new LambdaQueryWrapper<BizMachineContract>()
                        .eq(BizMachineContract::getSupplierId, supplierId));
        archive.put("machineContracts", machineContracts);

        return archive;
    }

    /**
     * 材料合同档案（合同基础+明细）
     */
    public Map<String, Object> getMaterialContractArchive(Long contractId) {
        Map<String, Object> archive = new HashMap<>();

        // 合同基础
        BizPurchaseContract contract = purchaseContractMapper.selectById(contractId);
        archive.put("contract", contract);

        // 合同明细
        List<BizPurchaseContractDetail> details = purchaseContractDetailMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseContractDetail>()
                        .eq(BizPurchaseContractDetail::getContractId, contractId));
        archive.put("details", details);

        return archive;
    }

    /**
     * 分包档案（基础+累计结算/付款/收票）
     */
    public Map<String, Object> getSubcontractArchive(Long contractId) {
        Map<String, Object> archive = new HashMap<>();

        // 分包合同基础信息
        BizSubcontract subcontract = subcontractMapper.selectById(contractId);
        archive.put("contract", subcontract);

        // 结算记录
        List<BizSubcontractSettlement> settlements = subcontractSettlementMapper.selectList(
                new LambdaQueryWrapper<BizSubcontractSettlement>()
                        .eq(BizSubcontractSettlement::getContractId, contractId));
        archive.put("settlements", settlements);

        // 汇总
        if (subcontract != null) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("contractAmount", subcontract.getContractAmount());
            summary.put("cumulativeSettlement", subcontract.getCumulativeSettlement());
            summary.put("cumulativePaid", subcontract.getCumulativePaid());
            archive.put("summary", summary);
        }

        return archive;
    }

    /**
     * 机械合同档案（基础+累计+使用过程数据）
     */
    public Map<String, Object> getMachineContractArchive(Long contractId) {
        Map<String, Object> archive = new HashMap<>();

        // 合同基础
        BizMachineContract contract = machineContractMapper.selectById(contractId);
        archive.put("contract", contract);

        // 结算记录
        List<BizMachineSettlement> settlements = machineSettlementMapper.selectList(
                new LambdaQueryWrapper<BizMachineSettlement>()
                        .eq(BizMachineSettlement::getContractId, contractId));
        archive.put("settlements", settlements);

        // 使用记录
        List<BizMachineUsageRecord> usageRecords = machineUsageRecordMapper.selectList(
                new LambdaQueryWrapper<BizMachineUsageRecord>()
                        .eq(BizMachineUsageRecord::getContractId, contractId));
        archive.put("usageRecords", usageRecords);

        // 汇总
        if (contract != null) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("contractAmount", contract.getContractAmount());
            summary.put("cumulativeSettlement", contract.getCumulativeSettlement());
            summary.put("cumulativePaid", contract.getCumulativePaid());
            archive.put("summary", summary);
        }

        return archive;
    }

    /**
     * 人事档案（入职+转正+离职）
     */
    public Map<String, Object> getPersonnelArchive(Long userId) {
        Map<String, Object> archive = new HashMap<>();

        // 入职申请（通过createdBy关联用户）
        List<BizEntryApply> entryApplies = entryApplyMapper.selectList(
                new LambdaQueryWrapper<BizEntryApply>()
                        .eq(BizEntryApply::getCreatedBy, userId));
        archive.put("entryApplies", entryApplies);

        // 转正申请
        List<BizRegularApply> regularApplies = regularApplyMapper.selectList(
                new LambdaQueryWrapper<BizRegularApply>()
                        .eq(BizRegularApply::getUserId, userId));
        archive.put("regularApplies", regularApplies);

        // 离职申请
        List<BizResignApply> resignApplies = resignApplyMapper.selectList(
                new LambdaQueryWrapper<BizResignApply>()
                        .eq(BizResignApply::getUserId, userId));
        archive.put("resignApplies", resignApplies);

        return archive;
    }

    /**
     * 车辆档案（登记+用车申请+维保）
     */
    public Map<String, Object> getVehicleArchive(Long vehicleId) {
        Map<String, Object> archive = new HashMap<>();

        // 车辆登记信息
        BizVehicle vehicle = vehicleMapper.selectById(vehicleId);
        archive.put("vehicle", vehicle);

        // 用车申请
        List<BizVehicleApply> applies = vehicleApplyMapper.selectList(
                new LambdaQueryWrapper<BizVehicleApply>()
                        .eq(BizVehicleApply::getVehicleId, vehicleId)
                        .orderByDesc(BizVehicleApply::getCreatedAt));
        archive.put("vehicleApplies", applies);

        // 维保记录
        List<BizVehicleMaintenance> maintenances = vehicleMaintenanceMapper.selectList(
                new LambdaQueryWrapper<BizVehicleMaintenance>()
                        .eq(BizVehicleMaintenance::getVehicleId, vehicleId)
                        .orderByDesc(BizVehicleMaintenance::getCreatedAt));
        archive.put("maintenances", maintenances);

        return archive;
    }
}
