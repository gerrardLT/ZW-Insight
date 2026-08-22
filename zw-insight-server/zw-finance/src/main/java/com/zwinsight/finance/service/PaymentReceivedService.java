package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款登记服务
 */
@Service
@RequiredArgsConstructor
public class PaymentReceivedService {

    /** 认领状态：待认领 */
    private static final String CLAIM_UNCLAIMED = "UNCLAIMED";
    /** 认领状态：已认领 */
    private static final String CLAIM_CLAIMED = "CLAIMED";
    /** 认领状态：已核销 */
    private static final String CLAIM_WRITTEN_OFF = "WRITTEN_OFF";

    private final BizPaymentReceivedMapper paymentReceivedMapper;
    private final BizProjectMapper projectMapper;
    private final BizConstructionContractMapper contractMapper;

    /**
     * 分页查询（支持按认领状态筛选）
     */
    public PageResult<BizPaymentReceived> page(int page, int size, Long projectId, String claimStatus) {
        Page<BizPaymentReceived> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPaymentReceived> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizPaymentReceived::getProjectId, projectId)
                .eq(claimStatus != null && !claimStatus.isBlank(), BizPaymentReceived::getClaimStatus, claimStatus)
                .orderByDesc(BizPaymentReceived::getCreatedAt);
        Page<BizPaymentReceived> result = paymentReceivedMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizPaymentReceived::getProjectId, BizPaymentReceived::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 新增收款（校验回款上限 + 回写项目totalIncome + 合同cumulativeReceivedAmount）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizPaymentReceived paymentReceived) {
        BigDecimal receiveAmount = paymentReceived.getReceiveAmount() == null
                ? BigDecimal.ZERO : paymentReceived.getReceiveAmount();

        // B2 修复（2026-08-11）：负数/零金额拒绝——原仅校验上限，负数可落库并反向
        // 扣减项目总收入与合同累计收款
        if (receiveAmount.signum() <= 0) {
            throw new BusinessException("回款金额必须大于0");
        }

        // 校验回款金额上限：不能超过已开票未收金额（累计开票 - 累计已回款）
        BizConstructionContract contract = null;
        if (paymentReceived.getContractId() != null) {
            contract = contractMapper.selectById(paymentReceived.getContractId());
            if (contract != null) {
                BigDecimal invoiced = contract.getCumulativeInvoiceAmount() == null
                        ? BigDecimal.ZERO : contract.getCumulativeInvoiceAmount();
                BigDecimal received = contract.getCumulativeReceivedAmount() == null
                        ? BigDecimal.ZERO : contract.getCumulativeReceivedAmount();
                BigDecimal maxReceivable = invoiced.subtract(received);
                if (receiveAmount.compareTo(maxReceivable) > 0) {
                    throw new BusinessException("回款金额不能超过已开票未收金额，最大可回款金额：" + maxReceivable);
                }
            }
        }

        paymentReceived.setStatus("APPROVED");
        paymentReceivedMapper.insert(paymentReceived);

        // 回写项目总收入
        BizProject project = projectMapper.selectById(paymentReceived.getProjectId());
        if (project != null) {
            BigDecimal totalIncome = project.getTotalIncome() == null
                    ? BigDecimal.ZERO : project.getTotalIncome();
            project.setTotalIncome(totalIncome.add(receiveAmount));
            projectMapper.updateById(project);
        }

        // 回写合同累计收款金额（复用已查询的 contract）
        if (contract != null) {
            BigDecimal cumulativeReceived = contract.getCumulativeReceivedAmount() == null
                    ? BigDecimal.ZERO : contract.getCumulativeReceivedAmount();
            contract.setCumulativeReceivedAmount(cumulativeReceived.add(receiveAmount));
            contractMapper.updateById(contract);
        }
    }

    /**
     * 根据ID查询
     */
    public BizPaymentReceived getById(Long id) {
        BizPaymentReceived record = paymentReceivedMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("收款记录不存在");
        }
        return record;
    }

    /**
     * 认领回款：UNCLAIMED → CLAIMED，记录认领人与认领时间。
     * 非法流转（已认领/已核销再认领）抛 BusinessException。
     * 存量数据无 claim_status 列默认值时按待认领处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id) {
        BizPaymentReceived record = getById(id);
        String current = record.getClaimStatus() == null ? CLAIM_UNCLAIMED : record.getClaimStatus();
        if (!CLAIM_UNCLAIMED.equals(current)) {
            throw new BusinessException("当前状态不允许认领：仅待认领的回款可以认领");
        }
        record.setClaimStatus(CLAIM_CLAIMED);
        record.setClaimedBy(SecurityContextHolder.getUserId());
        record.setClaimedAt(LocalDateTime.now());
        paymentReceivedMapper.updateById(record);
    }

    /**
     * 核销回款：CLAIMED → WRITTEN_OFF。
     * 非法流转（未认领直接核销/重复核销）抛 BusinessException。
     */
    @Transactional(rollbackFor = Exception.class)
    public void writeOff(Long id) {
        BizPaymentReceived record = getById(id);
        if (!CLAIM_CLAIMED.equals(record.getClaimStatus())) {
            throw new BusinessException("当前状态不允许核销：仅已认领的回款可以核销");
        }
        record.setClaimStatus(CLAIM_WRITTEN_OFF);
        paymentReceivedMapper.updateById(record);
    }

    /**
     * 更新收款记录（B1 修复，2026-08-11）：原实现直接 updateById 不回冲累计字段，
     * 改额后项目总收入与合同累计收款永久虚高/虚低。现按差额对称调整，
     * 增额部分重新校验可回款上限（与 save 口径一致）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(BizPaymentReceived paymentReceived) {
        BizPaymentReceived existing = paymentReceivedMapper.selectById(paymentReceived.getId());
        if (existing == null) {
            throw new BusinessException("收款记录不存在");
        }
        BigDecimal newAmount = paymentReceived.getReceiveAmount() == null
                ? BigDecimal.ZERO : paymentReceived.getReceiveAmount();
        if (newAmount.signum() <= 0) {
            throw new BusinessException("回款金额必须大于0");
        }
        BigDecimal oldAmount = existing.getReceiveAmount() == null
                ? BigDecimal.ZERO : existing.getReceiveAmount();
        BigDecimal diff = newAmount.subtract(oldAmount);

        // 增额时校验增量不超过可回款额度（原额已计入累计收款，无需扣回再校）
        BizConstructionContract contract = null;
        if (diff.signum() > 0 && existing.getContractId() != null) {
            contract = contractMapper.selectById(existing.getContractId());
            if (contract != null) {
                BigDecimal invoiced = contract.getCumulativeInvoiceAmount() == null
                        ? BigDecimal.ZERO : contract.getCumulativeInvoiceAmount();
                BigDecimal received = contract.getCumulativeReceivedAmount() == null
                        ? BigDecimal.ZERO : contract.getCumulativeReceivedAmount();
                BigDecimal maxReceivable = invoiced.subtract(received);
                if (diff.compareTo(maxReceivable) > 0) {
                    throw new BusinessException("回款金额不能超过已开票未收金额，最大可回款金额：" + maxReceivable);
                }
            }
        }

        paymentReceivedMapper.updateById(paymentReceived);

        // 按差额回冲/追加项目总收入
        if (diff.signum() != 0) {
            BizProject project = projectMapper.selectById(existing.getProjectId());
            if (project != null && project.getTotalIncome() != null) {
                project.setTotalIncome(project.getTotalIncome().add(diff));
                projectMapper.updateById(project);
            }
            if (contract == null && existing.getContractId() != null) {
                contract = contractMapper.selectById(existing.getContractId());
            }
            if (contract != null && contract.getCumulativeReceivedAmount() != null) {
                contract.setCumulativeReceivedAmount(contract.getCumulativeReceivedAmount().add(diff));
                contractMapper.updateById(contract);
            }
        }
    }

    /**
     * 删除收款记录（回冲项目 totalIncome + 合同 cumulativeReceivedAmount，与 save 回写对称；
     * 否则删除后合同可回款额度不恢复，逐轮消耗致数据不一致）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizPaymentReceived existing = paymentReceivedMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("收款记录不存在");
        }
        BigDecimal receiveAmount = existing.getReceiveAmount() == null
                ? BigDecimal.ZERO : existing.getReceiveAmount();
        paymentReceivedMapper.deleteById(id);

        // 回冲项目总收入
        BizProject project = projectMapper.selectById(existing.getProjectId());
        if (project != null && project.getTotalIncome() != null) {
            project.setTotalIncome(project.getTotalIncome().subtract(receiveAmount));
            projectMapper.updateById(project);
        }

        // 回冲合同累计收款金额
        if (existing.getContractId() != null) {
            BizConstructionContract contract = contractMapper.selectById(existing.getContractId());
            if (contract != null && contract.getCumulativeReceivedAmount() != null) {
                contract.setCumulativeReceivedAmount(
                        contract.getCumulativeReceivedAmount().subtract(receiveAmount));
                contractMapper.updateById(contract);
            }
        }
    }
}
