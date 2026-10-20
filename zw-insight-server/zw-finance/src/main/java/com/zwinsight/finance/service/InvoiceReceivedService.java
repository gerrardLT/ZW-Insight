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
import java.util.Set;

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
        return page(page, size, projectId, false, null);
    }

    /**
     * 分页查询（unlinkedContract=true 时只返回未关联支出合同的发票，供「发票库」认领）。
     */
    public PageResult<BizInvoiceReceived> page(int page, int size, Long projectId, boolean unlinkedContract) {
        return page(page, size, projectId, unlinkedContract, null);
    }

    /**
     * 分页查询（keyword 匹配发票号码或供应商名称，用于发票库搜索）。
     */
    public PageResult<BizInvoiceReceived> page(int page, int size, Long projectId,
                                               boolean unlinkedContract, String keyword) {
        Page<BizInvoiceReceived> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInvoiceReceived> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizInvoiceReceived::getProjectId, projectId)
                .isNull(unlinkedContract, BizInvoiceReceived::getContractId)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(BizInvoiceReceived::getInvoiceNumber, keyword.trim())
                                .or().like(BizInvoiceReceived::getSupplierName, keyword.trim()))
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
     * 发票库认领（V2026_123）：把已登记但未关联支出合同的发票补关联到合同。
     * <p>发票号唯一，复制新建会被唯一键拒绝，故「从发票库选择」实现为认领合同：
     * 不改金额/发票号，只补合同归属并回写合同累计收票；已关联合同的发票拒绝重复认领，
     * 跨项目认领拒绝（防串账）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id, com.zwinsight.finance.domain.dto.InvoiceClaimRequest request) {
        if (request == null || request.getContractId() == null) {
            throw new BusinessException(400, "请选择要关联的支出合同");
        }
        if (request.getContractCategory() == null || request.getContractCategory().isBlank()) {
            throw new BusinessException(400, "关联支出合同时必须指定合同类别");
        }
        String category = request.getContractCategory().trim().toUpperCase();
        if (!Set.of("PURCHASE", "LABOR", "MACHINE", "SUBCONTRACT", "OTHER_EXPENSE").contains(category)) {
            throw new BusinessException(400, "不支持的合同类别：" + category);
        }
        BizInvoiceReceived existing = invoiceReceivedMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "收票记录不存在");
        }
        if (existing.getContractId() != null) {
            throw new BusinessException(409, "该发票已关联支出合同，不可重复认领");
        }
        // 合同存在性 + 项目一致性校验（防跨项目串账；发票无项目时用合同项目补齐）
        Long contractProjectId = contractProjectId(category, request.getContractId());
        if (contractProjectId == null) {
            throw new BusinessException(404, "关联的支出合同不存在");
        }
        if (existing.getProjectId() != null && !existing.getProjectId().equals(contractProjectId)) {
            throw new BusinessException(409, "合同与发票不属于同一项目，禁止跨项目关联");
        }
        Long invoiceProjectId = existing.getProjectId() == null ? contractProjectId : existing.getProjectId();
        // 条件 UPDATE 守卫：仅当仍无合同时才认领成功，防并发重复入账
        int updated = invoiceReceivedMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizInvoiceReceived>()
                        .eq(BizInvoiceReceived::getId, id)
                        .isNull(BizInvoiceReceived::getContractId)
                        .set(BizInvoiceReceived::getContractId, request.getContractId())
                        .set(BizInvoiceReceived::getContractCategory, category)
                        .set(BizInvoiceReceived::getProjectId, invoiceProjectId)
                        .set(request.getSupplierName() != null && !request.getSupplierName().isBlank(),
                                BizInvoiceReceived::getSupplierName,
                                request.getSupplierName() == null ? null : request.getSupplierName().trim()));
        if (updated != 1) {
            throw new BusinessException(409, "发票已被认领，请刷新后重试");
        }
        // 认领即补关联合同：回写合同累计收票（命中 0 行必须报错回滚，不允许 200 但未入账）
        int written = routeInvoiceReceived(category, request.getContractId(), existing.getInvoiceAmount());
        if (written <= 0) {
            throw new BusinessException(500, "合同累计收票回写失败，请核对合同状态后重试");
        }
        log.info("进项发票认领合同完成: id={}, contractId={}, category={}, amount={}",
                id, request.getContractId(), category, existing.getInvoiceAmount());
    }

    /** 按合同类别查询合同归属项目（不存在返回 null） */
    private Long contractProjectId(String category, Long contractId) {
        return switch (category) {
            case "PURCHASE" -> contractPayableMapper.purchaseContractProjectId(contractId);
            case "LABOR" -> contractPayableMapper.laborContractProjectId(contractId);
            case "MACHINE" -> contractPayableMapper.machineContractProjectId(contractId);
            case "SUBCONTRACT" -> contractPayableMapper.subcontractProjectId(contractId);
            case "OTHER_EXPENSE" -> contractPayableMapper.otherContractProjectId(contractId);
            default -> null;
        };
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
     * 按合同类型将收票金额累加到对应的支出类合同（支持负数回冲）。
     *
     * @return 实际命中的合同行数（0=合同不存在/已删除；认领场景必须 >0）
     */
    private int routeInvoiceReceived(String category, Long contractId, BigDecimal amount) {
        if (contractId == null || amount == null || amount.signum() == 0) {
            return 0;
        }
        if ("PURCHASE".equalsIgnoreCase(category)) {
            return contractPayableMapper.addPurchaseInvoiceReceived(contractId, amount);
        } else if ("LABOR".equalsIgnoreCase(category)) {
            return contractPayableMapper.addLaborInvoiceReceived(contractId, amount);
        } else if ("MACHINE".equalsIgnoreCase(category)) {
            return contractPayableMapper.addMachineInvoiceReceived(contractId, amount);
        } else if ("SUBCONTRACT".equalsIgnoreCase(category)) {
            return contractPayableMapper.addSubcontractInvoiceReceived(contractId, amount);
        } else if ("OTHER_EXPENSE".equalsIgnoreCase(category) || "OTHER".equalsIgnoreCase(category)) {
            return contractPayableMapper.addOtherInvoiceReceived(contractId, amount);
        }

        // 未指定 category 的存量调用：优先查其他合同，再按采购/分包/劳务/机械依次尝试
        BizOtherContract contract = otherContractMapper.selectById(contractId);
        if (contract != null) {
            BigDecimal cumulativeInvoice = contract.getCumulativeInvoice() == null
                    ? BigDecimal.ZERO : contract.getCumulativeInvoice();
            contract.setCumulativeInvoice(cumulativeInvoice.add(amount));
            return otherContractMapper.updateById(contract);
        }
        if (category == null || category.isBlank()) {
            if (contractPayableMapper.addPurchaseInvoiceReceived(contractId, amount) > 0) return 1;
            if (contractPayableMapper.addSubcontractInvoiceReceived(contractId, amount) > 0) return 1;
            if (contractPayableMapper.addLaborInvoiceReceived(contractId, amount) > 0) return 1;
            if (contractPayableMapper.addMachineInvoiceReceived(contractId, amount) > 0) return 1;
        }
        log.info("回写支出类合同累计收票未命中: category={}, contractId={}, amount={}", category, contractId, amount);
        return 0;
    }
}
