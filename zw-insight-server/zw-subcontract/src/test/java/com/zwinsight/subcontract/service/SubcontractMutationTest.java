package com.zwinsight.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractOutputReport;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.domain.BizSubcontractSettlementDetail;
import com.zwinsight.subcontract.dto.SubcontractSettlementCreateRequest;
import com.zwinsight.subcontract.dto.SubcontractSettlementDetailDTO;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractOutputReportMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractSettlementMapper;
import com.zwinsight.subcontract.mapper.SubcontractSettlementDetailMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

/**
 * 分包模块变异补强测试（测试成熟度 2.1.3）
 * <p>
 * 针对 PIT 存活变异补断言：结算单创建/更新字段写入与明细行金额计算、
 * 提交累计校验边界（恰好等于合同额放行）、产值提交回写、保存预算校验分支。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("分包模块变异补强测试")
class SubcontractMutationTest {

    @Mock
    private BizSubcontractSettlementMapper settlementMapper;
    @Mock
    private SubcontractSettlementDetailMapper detailMapper;
    @Mock
    private BizSubcontractMapper subcontractMapper;
    @Mock
    private BizSubcontractOutputReportMapper outputReportMapper;
    @Mock
    private BizBudgetDetailMapper budgetDetailMapper;
    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private SubcontractSettlementService settlementService;

    private SubcontractSettlementDetailDTO detailDTO(String name, String qty, String price, Integer sortOrder) {
        SubcontractSettlementDetailDTO dto = new SubcontractSettlementDetailDTO();
        dto.setItemName(name);
        dto.setUnit("项");
        dto.setQuantity(new BigDecimal(qty));
        dto.setUnitPrice(new BigDecimal(price));
        dto.setRemark("备注-" + name);
        dto.setSortOrder(sortOrder);
        return dto;
    }

    // ==================== createSettlement 字段写入与金额计算 ====================

    @Test
    @DisplayName("创建结算单：主表初始字段+明细行逐字段写入+总金额回写")
    void createSettlement_allFieldsWrittenAndTotalUpdated() {
        doAnswer(inv -> {
            BizSubcontractSettlement s = inv.getArgument(0);
            s.setId(100L);
            return 1;
        }).when(settlementMapper).insert(any(BizSubcontractSettlement.class));
        when(settlementMapper.updateById(any(BizSubcontractSettlement.class))).thenReturn(1);

        SubcontractSettlementCreateRequest request = new SubcontractSettlementCreateRequest();
        request.setContractId(10L);
        request.setProjectId(1L);
        // 明细1：sortOrder 未传 → 自动 1；明细2：显式 sortOrder=9
        request.setDetails(Arrays.asList(
                detailDTO("土方开挖", "10", "100.555", null),
                detailDTO("混凝土浇筑", "5", "200", 9)));

        Long id = settlementService.createSettlement(request);
        assertThat(id).isEqualTo(100L);

        // 主表字段写入（insert 与 updateById 为同一对象引用，captor 呈最终态；
        // 初始 settlementAmount=0 为中间态不可观测，总额以 updateById 后断言为准）
        ArgumentCaptor<BizSubcontractSettlement> insertCaptor =
                ArgumentCaptor.forClass(BizSubcontractSettlement.class);
        verify(settlementMapper).insert(insertCaptor.capture());
        BizSubcontractSettlement inserted = insertCaptor.getValue();
        assertThat(inserted.getContractId()).isEqualTo(10L);
        assertThat(inserted.getProjectId()).isEqualTo(1L);
        assertThat(inserted.getStatus()).isEqualTo("DRAFT");

        // 明细行逐字段断言
        ArgumentCaptor<BizSubcontractSettlementDetail> detailCaptor =
                ArgumentCaptor.forClass(BizSubcontractSettlementDetail.class);
        verify(detailMapper, org.mockito.Mockito.times(2)).insert(detailCaptor.capture());
        List<BizSubcontractSettlementDetail> details = detailCaptor.getAllValues();

        BizSubcontractSettlementDetail d1 = details.get(0);
        assertThat(d1.getSettlementId()).isEqualTo(100L);
        assertThat(d1.getItemName()).isEqualTo("土方开挖");
        assertThat(d1.getUnit()).isEqualTo("项");
        assertThat(d1.getQuantity()).isEqualByComparingTo("10");
        assertThat(d1.getUnitPrice()).isEqualByComparingTo("100.555");
        assertThat(d1.getRemark()).isEqualTo("备注-土方开挖");
        assertThat(d1.getSortOrder()).isEqualTo(1); // 未传 → 自增序号 1
        assertThat(d1.getAmount()).isEqualByComparingTo("1005.55"); // 10×100.555 HALF_UP 两位

        BizSubcontractSettlementDetail d2 = details.get(1);
        assertThat(d2.getSortOrder()).isEqualTo(9); // 显式值保留
        assertThat(d2.getAmount()).isEqualByComparingTo("1000.00");

        // 总金额回写 = 1005.55 + 1000.00
        ArgumentCaptor<BizSubcontractSettlement> updateCaptor =
                ArgumentCaptor.forClass(BizSubcontractSettlement.class);
        verify(settlementMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSettlementAmount()).isEqualByComparingTo("2005.55");
    }

    // ==================== updateSettlement ====================

    @Test
    @DisplayName("更新结算单：非草稿拒绝；草稿更新合同/项目字段并重算")
    void updateSettlement_guardAndFieldWrites() {
        BizSubcontractSettlement approved = new BizSubcontractSettlement();
        approved.setStatus("APPROVED");
        when(settlementMapper.selectById(1L)).thenReturn(approved);
        assertThatThrownBy(() -> settlementService.updateSettlement(1L, new SubcontractSettlementCreateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");

        BizSubcontractSettlement draft = new BizSubcontractSettlement();
        draft.setId(2L);
        draft.setStatus("DRAFT");
        when(settlementMapper.selectById(2L)).thenReturn(draft);

        SubcontractSettlementCreateRequest request = new SubcontractSettlementCreateRequest();
        request.setContractId(20L);
        request.setProjectId(2L);
        request.setDetails(Collections.singletonList(detailDTO("钢筋", "2", "3000", null)));

        settlementService.updateSettlement(2L, request);

        // 旧明细删除 + 新明细插入 + 主表字段更新
        verify(detailMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<BizSubcontractSettlement> captor = ArgumentCaptor.forClass(BizSubcontractSettlement.class);
        verify(settlementMapper).updateById(captor.capture());
        BizSubcontractSettlement updated = captor.getValue();
        assertThat(updated.getContractId()).isEqualTo(20L);
        assertThat(updated.getProjectId()).isEqualTo(2L);
        assertThat(updated.getSettlementAmount()).isEqualByComparingTo("6000.00");
    }

    // ==================== submit 累计校验边界 ====================

    @Test
    @DisplayName("提交结算单：守卫分支+累计恰好等于合同额放行（边界）")
    void submit_guardsAndExactAmountBoundary() {
        // 非草稿拒绝
        BizSubcontractSettlement approved = new BizSubcontractSettlement();
        approved.setStatus("APPROVED");
        when(settlementMapper.selectById(1L)).thenReturn(approved);
        assertThatThrownBy(() -> settlementService.submit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");

        // 合同不存在拒绝
        BizSubcontractSettlement draftNoContract = new BizSubcontractSettlement();
        draftNoContract.setStatus("DRAFT");
        draftNoContract.setContractId(99L);
        when(settlementMapper.selectById(2L)).thenReturn(draftNoContract);
        when(subcontractMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> settlementService.submit(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分包合同不存在");

        // 累计恰好等于合同额 → 放行（> 边界变异时此处将被误拒）
        BizSubcontractSettlement draft = new BizSubcontractSettlement();
        draft.setId(3L);
        draft.setStatus("DRAFT");
        draft.setContractId(30L);
        draft.setSettlementAmount(new BigDecimal("4000"));
        when(settlementMapper.selectById(3L)).thenReturn(draft);
        BizSubcontract contract = new BizSubcontract();
        contract.setContractAmount(new BigDecimal("10000"));
        contract.setCumulativeSettlement(new BigDecimal("6000")); // 6000+4000=10000 恰好
        when(subcontractMapper.selectById(30L)).thenReturn(contract);

        settlementService.submit(3L);

        ArgumentCaptor<BizSubcontractSettlement> captor = ArgumentCaptor.forClass(BizSubcontractSettlement.class);
        verify(settlementMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("APPROVED");

        // 超出 → 拒绝并给出最大可结算金额
        BizSubcontractSettlement draftOver = new BizSubcontractSettlement();
        draftOver.setId(4L);
        draftOver.setStatus("DRAFT");
        draftOver.setContractId(30L);
        draftOver.setSettlementAmount(new BigDecimal("4000.01"));
        when(settlementMapper.selectById(4L)).thenReturn(draftOver);
        assertThatThrownBy(() -> settlementService.submit(4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出合同金额限制");
    }

    // ==================== SubcontractService.save 预算分支 ====================

    @Test
    @DisplayName("保存分包合同：无预算ID跳过校验；累计恰达预算放行（边界）；超预算拒绝")
    void save_budgetBranches() {
        SubcontractService subcontractService =
                new SubcontractService(subcontractMapper, budgetDetailMapper, projectMapper);

        // budgetId=null → 跳过预算校验直接插入
        BizSubcontract noBudget = new BizSubcontract();
        noBudget.setContractAmount(new BigDecimal("1000"));
        subcontractService.save(noBudget);
        verify(subcontractMapper).insert(noBudget);
        assertThat(noBudget.getStatus()).isEqualTo("DRAFT");
        assertThat(noBudget.getCumulativeSettlement()).isEqualByComparingTo("0");
        assertThat(noBudget.getCumulativePaid()).isEqualByComparingTo("0");

        // 有预算：预算 10000，已用 6000，本次 4000 → 恰好放行（> 边界变异时被误拒）
        BizBudgetDetail budgetDetail = new BizBudgetDetail();
        budgetDetail.setBudgetTotalPrice(new BigDecimal("10000"));
        when(budgetDetailMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(budgetDetail));
        BizSubcontract used = new BizSubcontract();
        used.setContractAmount(new BigDecimal("6000"));
        when(subcontractMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(used));

        BizSubcontract atLimit = new BizSubcontract();
        atLimit.setBudgetId(1L);
        atLimit.setProjectId(1L);
        atLimit.setContractAmount(new BigDecimal("4000"));
        subcontractService.save(atLimit);
        verify(subcontractMapper).insert(atLimit);

        // 超预算 → 拒绝
        BizSubcontract over = new BizSubcontract();
        over.setBudgetId(1L);
        over.setProjectId(1L);
        over.setContractAmount(new BigDecimal("4000.01"));
        assertThatThrownBy(() -> subcontractService.save(over))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出预算");
        verify(subcontractMapper, never()).insert(over);
    }

    // ==================== SubcontractOutputService delete/submit ====================

    @Test
    @DisplayName("产值报告：非草稿删除/提交拒绝；提交成功回写合同累计产值")
    void outputReport_deleteSubmitGuardsAndWriteback() {
        SubcontractOutputService outputService =
                new SubcontractOutputService(outputReportMapper, subcontractMapper);

        // delete：不存在/非草稿拒绝
        when(outputReportMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> outputService.delete(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("产值报告不存在");
        BizSubcontractOutputReport approved = new BizSubcontractOutputReport();
        approved.setStatus("APPROVED");
        when(outputReportMapper.selectById(1L)).thenReturn(approved);
        assertThatThrownBy(() -> outputService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
        // 草稿删除成功
        BizSubcontractOutputReport draftDel = new BizSubcontractOutputReport();
        draftDel.setStatus("DRAFT");
        when(outputReportMapper.selectById(2L)).thenReturn(draftDel);
        outputService.delete(2L);
        verify(outputReportMapper).deleteById(2L);

        // submit：非草稿拒绝
        when(outputReportMapper.selectById(3L)).thenReturn(approved);
        assertThatThrownBy(() -> outputService.submit(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可提交");

        // submit：草稿成功，状态置 APPROVED 且回写合同累计产值（null 兜底 0）
        BizSubcontractOutputReport draft = new BizSubcontractOutputReport();
        draft.setId(4L);
        draft.setStatus("DRAFT");
        draft.setContractId(40L);
        draft.setCurrentOutput(new BigDecimal("5000"));
        when(outputReportMapper.selectById(4L)).thenReturn(draft);
        BizSubcontract contract = new BizSubcontract();
        contract.setCumulativeOutput(null); // null → 从 0 累加
        when(subcontractMapper.selectById(40L)).thenReturn(contract);

        outputService.submit(4L);

        ArgumentCaptor<BizSubcontractOutputReport> reportCaptor =
                ArgumentCaptor.forClass(BizSubcontractOutputReport.class);
        verify(outputReportMapper).updateById(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getStatus()).isEqualTo("APPROVED");

        ArgumentCaptor<BizSubcontract> contractCaptor = ArgumentCaptor.forClass(BizSubcontract.class);
        verify(subcontractMapper).updateById(contractCaptor.capture());
        assertThat(contractCaptor.getValue().getCumulativeOutput()).isEqualByComparingTo("5000");
    }
}
