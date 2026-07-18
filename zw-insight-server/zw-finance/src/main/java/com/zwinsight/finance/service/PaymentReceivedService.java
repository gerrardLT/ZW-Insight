package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 收款登记服务
 */
@Service
@RequiredArgsConstructor
public class PaymentReceivedService {

    private final BizPaymentReceivedMapper paymentReceivedMapper;
    private final BizProjectMapper projectMapper;
    private final BizConstructionContractMapper contractMapper;

    /**
     * 分页查询
     */
    public PageResult<BizPaymentReceived> page(int page, int size, Long projectId) {
        Page<BizPaymentReceived> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPaymentReceived> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizPaymentReceived::getProjectId, projectId)
                .orderByDesc(BizPaymentReceived::getCreatedAt);
        Page<BizPaymentReceived> result = paymentReceivedMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增收款（校验回款上限 + 回写项目totalIncome + 合同cumulativeReceivedAmount）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizPaymentReceived paymentReceived) {
        BigDecimal receiveAmount = paymentReceived.getReceiveAmount() == null
                ? BigDecimal.ZERO : paymentReceived.getReceiveAmount();

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
     * 更新收款记录
     */
    public void update(BizPaymentReceived paymentReceived) {
        paymentReceivedMapper.updateById(paymentReceived);
    }

    /**
     * 删除收款记录
     */
    public void delete(Long id) {
        paymentReceivedMapper.deleteById(id);
    }
}
