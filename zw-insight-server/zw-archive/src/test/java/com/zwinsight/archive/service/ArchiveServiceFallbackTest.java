package com.zwinsight.archive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.archive.mapper.ArchiveOfficeSupplyMapper;
import com.zwinsight.basedata.mapper.BdSupplierMapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizExpenseContractMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.hr.mapper.BizEntryApplyMapper;
import com.zwinsight.hr.mapper.BizRegularApplyMapper;
import com.zwinsight.hr.mapper.BizResignApplyMapper;
import com.zwinsight.hr.mapper.BizVehicleApplyMapper;
import com.zwinsight.hr.mapper.BizVehicleMaintenanceMapper;
import com.zwinsight.hr.mapper.BizVehicleMapper;
import com.zwinsight.machine.mapper.BizMachineContractMapper;
import com.zwinsight.machine.mapper.BizMachineSettlementMapper;
import com.zwinsight.machine.mapper.BizMachineUsageRecordMapper;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.purchase.mapper.BizPurchaseContractDetailMapper;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractSettlementMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ArchiveService 兜底分支单元测试（2026-08-11 补：原 Service 层零测试，
 * 唯一 property 测试为逻辑复制品不驱动真实代码）。
 * 覆盖：主实体不存在/无关联数据时的兜底行为（fundSummary 置 ZERO、跳过子查询等）。
 */
@ExtendWith(MockitoExtension.class)
class ArchiveServiceFallbackTest {

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
        TableInfoHelper.initTableInfo(assistant, BizBudget.class);
        TableInfoHelper.initTableInfo(assistant, BizBudgetDetail.class);
    }

    @Test
    @DisplayName("项目档案：项目不存在时 fundSummary 兜底 ZERO 且结构键齐全")
    void getProjectArchive_projectNotFound_fundSummaryZero() {
        when(projectMapper.selectById(999L)).thenReturn(null);
        when(projectMemberMapper.selectList(any())).thenReturn(List.of());
        when(constructionContractMapper.selectList(any())).thenReturn(List.of());
        when(paymentApplyMapper.selectList(any())).thenReturn(List.of());
        when(paymentReceivedMapper.selectList(any())).thenReturn(List.of());
        when(subcontractMapper.selectList(any())).thenReturn(List.of());
        when(machineContractMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> archive = archiveService.getProjectArchive(999L);

        assertThat(archive.get("project")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> fundSummary = (Map<String, Object>) archive.get("fundSummary");
        assertThat((BigDecimal) fundSummary.get("totalIncome")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) fundSummary.get("totalExpense")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) fundSummary.get("contractAmount")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("预算档案：无预算时不查明细、无分类汇总（isEmpty 兜底分支）")
    void getBudgetArchive_noBudgets_skipsDetails() {
        when(budgetMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> archive = archiveService.getBudgetArchive(1L);

        assertThat((List<?>) archive.get("budgets")).isEmpty();
        assertThat(archive.containsKey("budgetDetails")).isFalse();
        verify(budgetDetailMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("合同档案：合同不存在时跳过变更查询")
    void getContractArchive_contractNull_skipsChanges() {
        when(constructionContractMapper.selectById(999L)).thenReturn(null);

        Map<String, Object> archive = archiveService.getContractArchive(999L);

        assertThat(archive.get("contract")).isNull();
        assertThat(archive.containsKey("changes")).isFalse();
        verify(constructionContractMapper, never()).selectList(any());
    }
}
