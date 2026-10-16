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
        String number = invoiceReceived.getInvoiceNumber();
        if (number != null && !number.isBlank()) {
            number = number.trim();
            if (!number.matches("[0-9]{8}|[0-9]{20}")) {
                throw new BusinessException("发票号码须为8位或20位数字");
            }
            invoiceReceived.setInvoiceNumber(number);
        } else {
            number = null;
            invoiceReceived.setInvoiceNumber(null);
        }
        String code = invoiceReceived.getInvoiceCode();
        code = code == null ? "" : code.trim();
        if (!code.isEmpty() && !code.matches("[0-9]{10}|[0-9]{12}")) {
            throw new BusinessException("发票代码须为10位或12位数字");
        }
        if (number != null && number.length() == 8 && code.isEmpty()) {
            throw new BusinessException(400, "传统8位发票号码必须提供10位或12位发票代码");
        }
        // 数电票以20位号码为身份；可输入合法代码，但不让它改变唯一身份。
        if (number != null && number.length() == 20) code = "";
        invoiceReceived.setInvoiceCode(code);
        BigDecimal rate = invoiceReceived.getTaxRate();
        if (rate != null) {
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessException("税率须在0至100之间");
            }
            BigDecimal net = invoiceReceived.getInvoiceAmount().divide(
                    BigDecimal.ONE.add(rate.movePointLeft(2)), 2, java.math.RoundingMode.HALF_UP);
            invoiceReceived.setAmountWithoutTax(net);
            invoiceReceived.setTaxAmount(invoiceReceived.getInvoiceAmount().subtract(net));
        } else {
            invoiceReceived.setAmountWithoutTax(null);
            invoiceReceived.setTaxAmount(null);
        }
        invoiceReceived.setStatus("APPROVED");
        try {
            invoiceReceivedMapper.insert(invoiceReceived);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(409, "发票号码与代码已登记，请核对后重试", e);
        }

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
     * 查验状态流转（V2026_110）：UNVERIFIED → VERIFIED/MISMATCH，记录查验人与时间，可复核重验。
     * 语义如实：VERIFIED 表示人工核对税务平台一致，不冒充税局接口直连。
     */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, String verifyStatus) {
        if (!"VERIFIED".equals(verifyStatus) && !"MISMATCH".equals(verifyStatus)) {
            throw new BusinessException(400, "查验状态仅支持 VERIFIED/MISMATCH");
        }
        BizInvoiceReceived existing = invoiceReceivedMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "收票记录不存在");
        }
        int moved = invoiceReceivedMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizInvoiceReceived>()
                        .eq(BizInvoiceReceived::getId, id)
                        .set(BizInvoiceReceived::getVerifyStatus, verifyStatus)
                        .set(BizInvoiceReceived::getVerifyTime, java.time.LocalDateTime.now())
                        .set(BizInvoiceReceived::getVerifiedBy,
                                com.zwinsight.common.config.SecurityContextHolder.getUserId()));
        if (moved != 1) {
            throw new BusinessException("收票记录状态已变更，请刷新重试");
        }
    }

    /** 入账状态流转（V2026_110）：纯登记，不动金额与合同累计收票 */
    @Transactional(rollbackFor = Exception.class)
    public void markBookkeep(Long id, boolean booked) {
        BizInvoiceReceived existing = invoiceReceivedMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "收票记录不存在");
        }
        int moved = invoiceReceivedMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizInvoiceReceived>()
                        .eq(BizInvoiceReceived::getId, id)
                        .set(BizInvoiceReceived::getBookkeepStatus, booked ? "BOOKED" : "UNBOOKED"));
        if (moved != 1) {
            throw new BusinessException("收票记录状态已变更，请刷新重试");
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
