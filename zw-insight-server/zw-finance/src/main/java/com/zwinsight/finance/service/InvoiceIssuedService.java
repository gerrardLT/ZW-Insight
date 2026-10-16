package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.util.TaxAmounts;
import com.zwinsight.finance.domain.BizInvoiceIssued;
import com.zwinsight.finance.mapper.BizInvoiceIssuedMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 销项发票台账服务（V2026_110，C3）——真实票据事实层。
 * <p>
 * 登记、红冲、查验/入账状态流转；租户内有效身份唯一（数电20位号码，或
 * 传统8位号+10/12位代码，规则与进项 V2026_103 一致，DB 生成列 guard 兜底）。
 * <b>不回写</b>合同累计开票（申请审批口径不变），本台账是独立记录层。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceIssuedService {

    private final BizInvoiceIssuedMapper issuedMapper;

    /**
     * 分页查询（项目/合同/方向/申请来源筛选）
     */
    public PageResult<BizInvoiceIssued> page(int page, int size, Long projectId, Long contractId,
                                             String direction, Long applyId) {
        Page<BizInvoiceIssued> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInvoiceIssued> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizInvoiceIssued::getProjectId, projectId)
                .eq(contractId != null, BizInvoiceIssued::getContractId, contractId)
                .eq(direction != null && !direction.isBlank(), BizInvoiceIssued::getDirection, direction)
                .eq(applyId != null, BizInvoiceIssued::getApplyId, applyId)
                .orderByDesc(BizInvoiceIssued::getIssueDate)
                .orderByDesc(BizInvoiceIssued::getId);
        return PageResult.of(issuedMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 登记发票。规则：
     * <ul>
     *   <li>号码 8 位必须附 10/12 位代码；20 位数电号码以号码为身份（代码置空）</li>
     *   <li>红字必须挂蓝字原票，且 |金额| ≤ 原票剩余可红冲额</li>
     *   <li>税额/不含税由含税额+税率服务端派生（差额法，与进项口径一致）</li>
     *   <li>身份冲突转 409（DB 唯一 guard 兜底）</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizInvoiceIssued invoice) {
        normalizeAndValidate(invoice, null);
        invoice.setId(null);
        try {
            issuedMapper.insert(invoice);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(409, "该发票号码（及代码）已登记，请核对后重试", e);
        }
        log.info("销项发票登记, number={}, direction={}, amount={}, projectId={}",
                invoice.getInvoiceNumber(), invoice.getDirection(), invoice.getAmount(), invoice.getProjectId());
    }

    /**
     * 查验状态流转：UNVERIFIED → VERIFIED/MISMATCH（记录查验人与时间，可复核重验）。
     * 语义如实：VERIFIED 表示人工核对税务平台一致，不冒充税局接口直连。
     */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, String verifyStatus, Long verifiedBy) {
        if (!BizInvoiceIssued.VERIFY_VERIFIED.equals(verifyStatus)
                && !BizInvoiceIssued.VERIFY_MISMATCH.equals(verifyStatus)) {
            throw new BusinessException(400, "查验状态仅支持 VERIFIED/MISMATCH");
        }
        BizInvoiceIssued invoice = issuedMapper.selectById(id);
        if (invoice == null) {
            throw new BusinessException(404, "发票不存在");
        }
        int moved = issuedMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizInvoiceIssued>()
                .eq(BizInvoiceIssued::getId, id)
                .eq(BizInvoiceIssued::getDeleted, 0)
                .set(BizInvoiceIssued::getVerifyStatus, verifyStatus)
                .set(BizInvoiceIssued::getVerifyTime, LocalDateTime.now())
                .set(BizInvoiceIssued::getVerifiedBy, verifiedBy));
        if (moved != 1) {
            throw new BusinessException("发票状态已变更，请刷新重试");
        }
    }

    /** 入账状态流转：UNBOOKED ⇄ BOOKED（纯登记，不动金额与合同累计） */
    @Transactional(rollbackFor = Exception.class)
    public void markBookkeep(Long id, boolean booked) {
        BizInvoiceIssued invoice = issuedMapper.selectById(id);
        if (invoice == null) {
            throw new BusinessException(404, "发票不存在");
        }
        int moved = issuedMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizInvoiceIssued>()
                .eq(BizInvoiceIssued::getId, id)
                .eq(BizInvoiceIssued::getDeleted, 0)
                .set(BizInvoiceIssued::getBookkeepStatus,
                        booked ? BizInvoiceIssued.BOOK_BOOKED : BizInvoiceIssued.BOOK_UNBOOKED));
        if (moved != 1) {
            throw new BusinessException("发票状态已变更，请刷新重试");
        }
    }

    /** 删除（逻辑删；身份 guard 随 deleted=1 释放） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (issuedMapper.selectCount(new LambdaQueryWrapper<BizInvoiceIssued>()
                .eq(BizInvoiceIssued::getOriginalId, id)
                .eq(BizInvoiceIssued::getDeleted, 0)) > 0) {
            throw new BusinessException(409, "该发票存在关联红字发票，须先处理红冲记录");
        }
        if (issuedMapper.deleteById(id) != 1) {
            throw new BusinessException(404, "发票不存在");
        }
    }

    /** 销项税额合计（蓝字为正、红字为负，净额口径；驱动舱真税额指标用） */
    public BigDecimal sumTaxAmount(Long projectId) {
        return issuedMapper.selectList(new LambdaQueryWrapper<BizInvoiceIssued>()
                        .eq(projectId != null, BizInvoiceIssued::getProjectId, projectId))
                .stream()
                .map(i -> i.getTaxAmount() != null ? i.getTaxAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 私有 ====================

    private void normalizeAndValidate(BizInvoiceIssued invoice, Long excludeId) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            throw new BusinessException(400, "发票号码不能为空");
        }
        String number = invoice.getInvoiceNumber().trim();
        if (!number.matches("[0-9]{8}|[0-9]{20}")) {
            throw new BusinessException(400, "发票号码须为8位或20位数字");
        }
        String code = invoice.getInvoiceCode() == null ? "" : invoice.getInvoiceCode().trim();
        if (!code.isEmpty() && !code.matches("[0-9]{10}|[0-9]{12}")) {
            throw new BusinessException(400, "发票代码须为10位或12位数字");
        }
        if (number.length() == 8 && code.isEmpty()) {
            throw new BusinessException(400, "传统8位发票号码必须提供10位或12位发票代码");
        }
        if (number.length() == 20) {
            code = "";
        }
        invoice.setInvoiceNumber(number);
        invoice.setInvoiceCode(code);

        if (invoice.getIssueDate() == null) {
            throw new BusinessException(400, "开票日期不能为空");
        }
        String direction = invoice.getDirection() == null ? BizInvoiceIssued.DIRECTION_BLUE : invoice.getDirection();
        if (!BizInvoiceIssued.DIRECTION_BLUE.equals(direction) && !BizInvoiceIssued.DIRECTION_RED.equals(direction)) {
            throw new BusinessException(400, "发票方向仅支持 BLUE/RED");
        }
        invoice.setDirection(direction);

        if (invoice.getAmount() == null || invoice.getAmount().signum() == 0) {
            throw new BusinessException(400, "价税合计不能为0");
        }
        boolean blueAmountPositive = BizInvoiceIssued.DIRECTION_BLUE.equals(direction)
                ? invoice.getAmount().signum() > 0
                : invoice.getAmount().signum() < 0;
        if (!blueAmountPositive) {
            throw new BusinessException(400, "蓝字发票金额须为正数，红字发票金额须为负数");
        }

        if (BizInvoiceIssued.DIRECTION_RED.equals(direction)) {
            if (invoice.getOriginalId() == null) {
                throw new BusinessException(400, "红字发票必须关联原蓝字发票");
            }
            BizInvoiceIssued original = issuedMapper.selectById(invoice.getOriginalId());
            if (original == null || !BizInvoiceIssued.DIRECTION_BLUE.equals(original.getDirection())) {
                throw new BusinessException(400, "关联的原票不存在或非蓝字发票");
            }
            if (!Objects.equals(original.getProjectId(), invoice.getProjectId())
                    || !Objects.equals(original.getContractId(), invoice.getContractId())) {
                throw new BusinessException(400, "红字发票须与原票同项目同合同");
            }
            BigDecimal remaining = issuedMapper.remainingRedAmount(invoice.getOriginalId(), excludeId);
            if (invoice.getAmount().abs().compareTo(remaining) > 0) {
                throw new BusinessException(409, "红冲金额超过原票剩余可红冲额：" + remaining);
            }
        } else {
            invoice.setOriginalId(null);
        }

        try {
            TaxAmounts.validateRate(invoice.getTaxRate());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        }
        invoice.setAmountWithoutTax(TaxAmounts.withoutTax(invoice.getAmount().abs(), invoice.getTaxRate()));
        invoice.setTaxAmount(TaxAmounts.taxAmount(invoice.getAmount().abs(), invoice.getTaxRate()));
        if (BizInvoiceIssued.DIRECTION_RED.equals(direction)) {
            // 红字税额/不含税取负，与金额方向一致
            if (invoice.getAmountWithoutTax() != null) {
                invoice.setAmountWithoutTax(invoice.getAmountWithoutTax().negate());
            }
            if (invoice.getTaxAmount() != null) {
                invoice.setTaxAmount(invoice.getTaxAmount().negate());
            }
        }
        invoice.setVerifyStatus(BizInvoiceIssued.VERIFY_UNVERIFIED);
        invoice.setBookkeepStatus(BizInvoiceIssued.BOOK_UNBOOKED);
    }
}
