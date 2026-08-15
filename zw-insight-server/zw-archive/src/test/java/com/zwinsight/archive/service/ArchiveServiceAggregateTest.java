package com.zwinsight.archive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.archive.domain.BizOfficeSupply;
import com.zwinsight.archive.mapper.ArchiveOfficeSupplyMapper;
import com.zwinsight.archive.vo.OfficeSupplyArchiveVO;
import com.zwinsight.archive.vo.OtherContractArchiveVO;
import com.zwinsight.basedata.domain.BdSupplier;
import com.zwinsight.basedata.mapper.BdSupplierMapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizExpenseContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizExpenseContractMapper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ArchiveService 聚合路径单元测试（2026-08-15 P3 补测，与 FallbackTest 互补）
 *
 * FallbackTest 覆盖主实体缺失的兜底分支；本类覆盖有数据时的聚合组装与
 * VO 转换：10 个档案方法正向路径 + 2 个分页 VO 映射（项目名关联/null 项目过滤）。
 */
@ExtendWith(MockitoExtension.class)
class ArchiveServiceAggregateTest {

    @Mock private BizProjectMapper projectMapper;
    @Mock private BizProjectMemberMapper projectMemberMapper;
    @Mock private BizConstructionContractMapper constructionContractMapper;
    @Mock private BizPaymentApplyMapper paymentApplyMapper;
    @Mock private BizPaymentReceivedMapper paymentReceivedMapper;
    @Mock private BizSubcontractMapper subcontractMapper;
    @Mock private BizSubcontractSettlementMapper subcontractSettlementMapper;
    @Mock private BizMachineContractMapper machineContractMapper;
    @Mock private BizMachineSettlementMapper machineSettlementMapper;
    @Mock private BizMachineUsageRecordMapper machineUsageRecordMapper;
    @Mock private BizPurchaseContractMapper purchaseContractMapper;
    @Mock private BizPurchaseContractDetailMapper purchaseContractDetailMapper;
    @Mock private BizTenderRegisterMapper tenderRegisterMapper;
    @Mock private BizTenderTaskMapper tenderTaskMapper;
    @Mock private BizOpenBidRecordMapper openBidRecordMapper;
    @Mock private BizDepositApplyMapper depositApplyMapper;
    @Mock private BizBudgetMapper budgetMapper;
    @Mock private BizBudgetDetailMapper budgetDetailMapper;
    @Mock private BdSupplierMapper supplierMapper;
    @Mock private BizEntryApplyMapper entryApplyMapper;
    @Mock private BizRegularApplyMapper regularApplyMapper;
    @Mock private BizResignApplyMapper resignApplyMapper;
    @Mock private BizVehicleMapper vehicleMapper;
    @Mock private BizVehicleApplyMapper vehicleApplyMapper;
    @Mock private BizVehicleMaintenanceMapper vehicleMaintenanceMapper;
    @Mock private BizExpenseContractMapper expenseContractMapper;
    @Mock private ArchiveOfficeSupplyMapper officeSupplyMapper;

    @InjectMocks
    private ArchiveService archiveService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizProjectMember.class);
        TableInfoHelper.initTableInfo(assistant, BizConstructionContract.class);
        TableInfoHelper.initTableInfo(assistant, com.zwinsight.finance.domain.BizPaymentApply.class);
        TableInfoHelper.initTableInfo(assistant, com.zwinsight.finance.domain.BizPaymentReceived.class);
        TableInfoHelper.initTableInfo(assistant, BizSubcontract.class);
        TableInfoHelper.initTableInfo(assistant, BizMachineContract.class);
        TableInfoHelper.initTableInfo(assistant, BizTenderTask.class);
        TableInfoHelper.initTableInfo(assistant, BizOpenBidRecord.class);
        TableInfoHelper.initTableInfo(assistant, BizDepositApply.class);
        TableInfoHelper.initTableInfo(assistant, BizBudget.class);
        TableInfoHelper.initTableInfo(assistant, BizBudgetDetail.class);
        TableInfoHelper.initTableInfo(assistant, BdSupplier.class);
        TableInfoHelper.initTableInfo(assistant, BizPurchaseContract.class);
        TableInfoHelper.initTableInfo(assistant, BizPurchaseContractDetail.class);
        TableInfoHelper.initTableInfo(assistant, BizSubcontractSettlement.class);
        TableInfoHelper.initTableInfo(assistant, BizMachineSettlement.class);
        TableInfoHelper.initTableInfo(assistant, BizMachineUsageRecord.class);
        TableInfoHelper.initTableInfo(assistant, BizEntryApply.class);
        TableInfoHelper.initTableInfo(assistant, BizRegularApply.class);
        TableInfoHelper.initTableInfo(assistant, BizResignApply.class);
        TableInfoHelper.initTableInfo(assistant, BizVehicle.class);
        TableInfoHelper.initTableInfo(assistant, BizVehicleApply.class);
        TableInfoHelper.initTableInfo(assistant, BizVehicleMaintenance.class);
        TableInfoHelper.initTableInfo(assistant, BizExpenseContract.class);
        TableInfoHelper.initTableInfo(assistant, BizOfficeSupply.class);
    }

    @Test
    @DisplayName("项目档案：项目存在时 fundSummary 取项目金额")
    void getProjectArchive_withProject_fundSummaryFromProject() {
        BizProject project = new BizProject();
        project.setId(1L);
        project.setTotalIncome(new BigDecimal("100.00"));
        project.setTotalExpense(new BigDecimal("60.00"));
        project.setContractAmount(new BigDecimal("200.00"));
        when(projectMapper.selectById(1L)).thenReturn(project);
        when(projectMemberMapper.selectList(any())).thenReturn(List.of(new BizProjectMember()));
        when(constructionContractMapper.selectList(any())).thenReturn(List.of(new BizConstructionContract()));
        when(paymentApplyMapper.selectList(any())).thenReturn(List.of());
        when(paymentReceivedMapper.selectList(any())).thenReturn(List.of());
        when(subcontractMapper.selectList(any())).thenReturn(List.of());
        when(machineContractMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> archive = archiveService.getProjectArchive(1L);

        assertThat(archive.get("project")).isSameAs(project);
        assertThat((List<?>) archive.get("members")).hasSize(1);
        assertThat((List<?>) archive.get("constructionContracts")).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> fundSummary = (Map<String, Object>) archive.get("fundSummary");
        assertThat((BigDecimal) fundSummary.get("totalIncome")).isEqualByComparingTo("100.00");
        assertThat((BigDecimal) fundSummary.get("totalExpense")).isEqualByComparingTo("60.00");
        assertThat((BigDecimal) fundSummary.get("contractAmount")).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("投标档案：登记+任务+开标+保证金聚合")
    void getTenderArchive_aggregatesAllSections() {
        BizTenderRegister register = new BizTenderRegister();
        when(tenderRegisterMapper.selectById(7L)).thenReturn(register);
        when(tenderTaskMapper.selectList(any())).thenReturn(List.of(new BizTenderTask()));
        when(openBidRecordMapper.selectList(any())).thenReturn(List.of(new BizOpenBidRecord(), new BizOpenBidRecord()));
        when(depositApplyMapper.selectList(any())).thenReturn(List.of(new BizDepositApply()));

        Map<String, Object> archive = archiveService.getTenderArchive(7L);

        assertThat(archive.get("register")).isSameAs(register);
        assertThat((List<?>) archive.get("tasks")).hasSize(1);
        assertThat((List<?>) archive.get("openBidRecords")).hasSize(2);
        assertThat((List<?>) archive.get("deposits")).hasSize(1);
    }

    @Test
    @DisplayName("预算档案：有预算时查明细并按科目汇总（null 金额按 0 累加）")
    void getBudgetArchive_withBudgets_categorySummaryMerges() {
        BizBudget budget = new BizBudget();
        budget.setId(11L);
        when(budgetMapper.selectList(any())).thenReturn(List.of(budget));
        BizBudgetDetail labor1 = new BizBudgetDetail();
        labor1.setCostCategory("LABOR");
        labor1.setBudgetTotalPrice(new BigDecimal("10.00"));
        BizBudgetDetail labor2 = new BizBudgetDetail();
        labor2.setCostCategory("LABOR");
        labor2.setBudgetTotalPrice(null); // null 金额按 ZERO 兜底
        BizBudgetDetail material = new BizBudgetDetail();
        material.setCostCategory("MATERIAL");
        material.setBudgetTotalPrice(new BigDecimal("5.00"));
        when(budgetDetailMapper.selectList(any())).thenReturn(List.of(labor1, labor2, material));

        Map<String, Object> archive = archiveService.getBudgetArchive(1L);

        assertThat((List<?>) archive.get("budgetDetails")).hasSize(3);
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> summary = (Map<String, BigDecimal>) archive.get("categorySummary");
        assertThat(summary).containsOnly(
                entry("LABOR", new BigDecimal("10.00")),
                entry("MATERIAL", new BigDecimal("5.00")));
    }

    @Test
    @DisplayName("合同档案：合同存在时查变更/补充协议")
    void getContractArchive_withContract_queriesChanges() {
        BizConstructionContract contract = new BizConstructionContract();
        when(constructionContractMapper.selectById(3L)).thenReturn(contract);
        when(constructionContractMapper.selectList(any())).thenReturn(List.of(new BizConstructionContract()));

        Map<String, Object> archive = archiveService.getContractArchive(3L);

        assertThat(archive.get("contract")).isSameAs(contract);
        assertThat((List<?>) archive.get("changes")).hasSize(1);
    }

    @Test
    @DisplayName("供应商档案：基本信息+三类合同列表聚合")
    void getSupplierArchive_aggregatesThreeContractTypes() {
        BdSupplier supplier = new BdSupplier();
        when(supplierMapper.selectById(5L)).thenReturn(supplier);
        when(purchaseContractMapper.selectList(any())).thenReturn(List.of(new BizPurchaseContract()));
        when(subcontractMapper.selectList(any())).thenReturn(List.of());
        when(machineContractMapper.selectList(any())).thenReturn(List.of(new BizMachineContract()));

        Map<String, Object> archive = archiveService.getSupplierArchive(5L);

        assertThat(archive.get("supplier")).isSameAs(supplier);
        assertThat((List<?>) archive.get("purchaseContracts")).hasSize(1);
        assertThat((List<?>) archive.get("subcontracts")).isEmpty();
        assertThat((List<?>) archive.get("machineContracts")).hasSize(1);
    }

    @Test
    @DisplayName("材料合同档案：合同基础+明细聚合")
    void getMaterialContractArchive_contractAndDetails() {
        BizPurchaseContract contract = new BizPurchaseContract();
        when(purchaseContractMapper.selectById(9L)).thenReturn(contract);
        when(purchaseContractDetailMapper.selectList(any())).thenReturn(List.of(new BizPurchaseContractDetail()));

        Map<String, Object> archive = archiveService.getMaterialContractArchive(9L);

        assertThat(archive.get("contract")).isSameAs(contract);
        assertThat((List<?>) archive.get("details")).hasSize(1);
    }

    @Test
    @DisplayName("分包档案：合同存在时 summary 取累计结算/付款")
    void getSubcontractArchive_summaryFromContract() {
        BizSubcontract subcontract = new BizSubcontract();
        subcontract.setContractAmount(new BigDecimal("500.00"));
        subcontract.setCumulativeSettlement(new BigDecimal("300.00"));
        subcontract.setCumulativePaid(new BigDecimal("200.00"));
        when(subcontractMapper.selectById(4L)).thenReturn(subcontract);
        when(subcontractSettlementMapper.selectList(any())).thenReturn(List.of(new BizSubcontractSettlement()));

        Map<String, Object> archive = archiveService.getSubcontractArchive(4L);

        assertThat((List<?>) archive.get("settlements")).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) archive.get("summary");
        assertThat((BigDecimal) summary.get("contractAmount")).isEqualByComparingTo("500.00");
        assertThat((BigDecimal) summary.get("cumulativeSettlement")).isEqualByComparingTo("300.00");
        assertThat((BigDecimal) summary.get("cumulativePaid")).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("机械合同档案：结算+使用记录+summary 聚合")
    void getMachineContractArchive_aggregatesUsageAndSummary() {
        BizMachineContract contract = new BizMachineContract();
        contract.setContractAmount(new BigDecimal("800.00"));
        contract.setCumulativeSettlement(new BigDecimal("100.00"));
        contract.setCumulativePaid(new BigDecimal("50.00"));
        when(machineContractMapper.selectById(6L)).thenReturn(contract);
        when(machineSettlementMapper.selectList(any())).thenReturn(List.of(new BizMachineSettlement()));
        when(machineUsageRecordMapper.selectList(any())).thenReturn(List.of(new BizMachineUsageRecord(), new BizMachineUsageRecord()));

        Map<String, Object> archive = archiveService.getMachineContractArchive(6L);

        assertThat((List<?>) archive.get("settlements")).hasSize(1);
        assertThat((List<?>) archive.get("usageRecords")).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) archive.get("summary");
        assertThat((BigDecimal) summary.get("contractAmount")).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("人事档案：入职/转正/离职三列表聚合")
    void getPersonnelArchive_threeApplyLists() {
        when(entryApplyMapper.selectList(any())).thenReturn(List.of(new BizEntryApply()));
        when(regularApplyMapper.selectList(any())).thenReturn(List.of());
        when(resignApplyMapper.selectList(any())).thenReturn(List.of(new BizResignApply()));

        Map<String, Object> archive = archiveService.getPersonnelArchive(100L);

        assertThat((List<?>) archive.get("entryApplies")).hasSize(1);
        assertThat((List<?>) archive.get("regularApplies")).isEmpty();
        assertThat((List<?>) archive.get("resignApplies")).hasSize(1);
    }

    @Test
    @DisplayName("车辆档案：登记+用车申请+维保聚合")
    void getVehicleArchive_vehicleAppliesAndMaintenances() {
        BizVehicle vehicle = new BizVehicle();
        when(vehicleMapper.selectById(2L)).thenReturn(vehicle);
        when(vehicleApplyMapper.selectList(any())).thenReturn(List.of(new BizVehicleApply()));
        when(vehicleMaintenanceMapper.selectList(any())).thenReturn(List.of(new BizVehicleMaintenance()));

        Map<String, Object> archive = archiveService.getVehicleArchive(2L);

        assertThat(archive.get("vehicle")).isSameAs(vehicle);
        assertThat((List<?>) archive.get("vehicleApplies")).hasSize(1);
        assertThat((List<?>) archive.get("maintenances")).hasSize(1);
    }

    @Test
    @DisplayName("其它合同档案分页：VO 映射+项目名关联+null 项目 ID 过滤")
    void pageOtherContractArchive_voMappingWithProjectName() {
        BizExpenseContract c1 = new BizExpenseContract();
        c1.setId(21L);
        c1.setContractCode("OC-001");
        c1.setContractName("其它收入合同");
        c1.setContractAmount(new BigDecimal("10.00"));
        c1.setStatus("EFFECTIVE");
        c1.setProjectId(101L);
        BizExpenseContract c2 = new BizExpenseContract();
        c2.setId(22L);
        c2.setProjectId(null); // null 项目 ID 应被过滤（不查项目名）
        Page<BizExpenseContract> page = new Page<>(1, 10);
        page.setRecords(List.of(c1, c2));
        page.setTotal(2);
        when(expenseContractMapper.selectPage(any(), any())).thenReturn(page);
        BizProject project = new BizProject();
        project.setId(101L);
        project.setProjectName("滨江花园一期");
        when(projectMapper.selectBatchIds(List.of(101L))).thenReturn(List.of(project));

        PageResult<OtherContractArchiveVO> result =
                archiveService.pageOtherContractArchive("OTHER_INCOME", 1, 10, null);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
        OtherContractArchiveVO vo1 = result.getRecords().get(0);
        assertThat(vo1.getContractCode()).isEqualTo("OC-001");
        assertThat(vo1.getProjectName()).isEqualTo("滨江花园一期");
        assertThat(result.getRecords().get(1).getProjectName()).isNull();
    }

    @Test
    @DisplayName("办公用品档案分页：VO 字段全量映射")
    void pageOfficeSupplyArchive_voFieldMapping() {
        BizOfficeSupply supply = new BizOfficeSupply();
        supply.setId(31L);
        supply.setSupplyName("打印纸");
        supply.setCurrentStock(new BigDecimal("100"));
        supply.setTotalInbound(new BigDecimal("150"));
        supply.setTotalIssued(new BigDecimal("50"));
        Page<BizOfficeSupply> page = new Page<>(1, 10);
        page.setRecords(List.of(supply));
        page.setTotal(1);
        when(officeSupplyMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<OfficeSupplyArchiveVO> result =
                archiveService.pageOfficeSupplyArchive(1, 10, "打印");

        assertThat(result.getTotal()).isEqualTo(1);
        OfficeSupplyArchiveVO vo = result.getRecords().get(0);
        assertThat(vo.getId()).isEqualTo(31L);
        assertThat(vo.getSupplyName()).isEqualTo("打印纸");
        assertThat(vo.getCurrentStock()).isEqualByComparingTo("100");
        assertThat(vo.getTotalInbound()).isEqualByComparingTo("150");
        assertThat(vo.getTotalIssued()).isEqualByComparingTo("50");
    }
}
