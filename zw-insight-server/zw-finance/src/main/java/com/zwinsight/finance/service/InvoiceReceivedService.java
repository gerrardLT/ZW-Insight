package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import com.zwinsight.finance.mapper.ContractPayableMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 收票登记服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceReceivedService {

    private final BizInvoiceReceivedMapper invoiceReceivedMapper;
    private final BizOtherContractMapper otherContractMapper;
    private final ContractPayableMapper contractPayableMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询
     */
    public PageResult<BizInvoiceReceived> page(int page, int size, Long projectId) {
        Page<BizInvoiceReceived> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInvoiceReceived> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizInvoiceReceived::getProjectId, projectId)
                .orderByDesc(BizInvoiceReceived::getCreatedAt);
        Page<BizInvoiceReceived> result = invoiceReceivedMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizInvoiceReceived::getProjectId, BizInvoiceReceived::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 新增收票（回写支出合同cumulativeInvoiceReceived）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizInvoiceReceived invoiceReceived) {
        // P0 修复（FIN-RCI-03/04，2026-08-12）：收票金额必须>0，
        // 原实现负/零/null 可落库并污染合同累计收票（null 时 add NPE）
        if (invoiceReceived.getInvoiceAmount() == null
                || invoiceReceived.getInvoiceAmount().signum() <= 0) {
            throw new BusinessException("收票金额必须大于0");
        }
        invoiceReceived.setStatus("APPROVED");
        invoiceReceivedMapper.insert(invoiceReceived);

        // 回写支出类合同累计收票
        if (invoiceReceived.getContractId() != null) {
            routeInvoiceReceived(invoiceReceived.getContractCategory(),
                    invoiceReceived.getContractId(), invoiceReceived.getInvoiceAmount());
        }
    }

    /**
     * 删除收票记录（回冲合同累计收票，与 save 回写对称）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizInvoiceReceived existing = invoiceReceivedMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("收票记录不存在");
        }
        BigDecimal invoiceAmount = existing.getInvoiceAmount() == null
                ? BigDecimal.ZERO : existing.getInvoiceAmount();
        invoiceReceivedMapper.deleteById(id);

        // 回冲合同累计收票金额（传负数）
        if (existing.getContractId() != null) {
            routeInvoiceReceived(existing.getContractCategory(),
                    existing.getContractId(), invoiceAmount.negate());
        }
    }

    /**
     * 按合同类型将收票金额累加到对应的支出类合同（支持负数回冲）
     */
    private void routeInvoiceReceived(String category, Long contractId, BigDecimal amount) {
        if (contractId == null || amount == null || amount.signum() == 0) {
            return;
        }
        if ("PURCHASE".equalsIgnoreCase(category)) {
            contractPayableMapper.addPurchaseInvoiceReceived(contractId, amount);
            return;
        } else if ("LABOR".equalsIgnoreCase(category)) {
            contractPayableMapper.addLaborInvoiceReceived(contractId, amount);
            return;
        } else if ("MACHINE".equalsIgnoreCase(category)) {
            contractPayableMapper.addMachineInvoiceReceived(contractId, amount);
            return;
        } else if ("SUBCONTRACT".equalsIgnoreCase(category)) {
            contractPayableMapper.addSubcontractInvoiceReceived(contractId, amount);
            return;
        }

        // OTHER / OTHER_EXPENSE 或未指定 category 时：优先查其他合同（兼容既有单测与存量调用），
        // 若其他合同不存在且未指定 category，再按采购/分包/劳务/机械依次尝试回写
        BizOtherContract contract = otherContractMapper.selectById(contractId);
        if (contract != null) {
            BigDecimal cumulativeInvoice = contract.getCumulativeInvoice() == null
                    ? BigDecimal.ZERO : contract.getCumulativeInvoice();
            contract.setCumulativeInvoice(cumulativeInvoice.add(amount));
            otherContractMapper.updateById(contract);
            return;
        }
        if (category == null || category.isBlank()) {
            if (contractPayableMapper.addPurchaseInvoiceReceived(contractId, amount) > 0) return;
            if (contractPayableMapper.addSubcontractInvoiceReceived(contractId, amount) > 0) return;
            if (contractPayableMapper.addLaborInvoiceReceived(contractId, amount) > 0) return;
            contractPayableMapper.addMachineInvoiceReceived(contractId, amount);
        }
        log.info("回写支出类合同累计收票完成: category={}, contractId={}, amount={}", category, contractId, amount);
    }
}
