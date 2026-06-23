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
     * 新增收款（回写项目totalIncome + 合同cumulativeReceivedAmount）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizPaymentReceived paymentReceived) {
        paymentReceived.setStatus("APPROVED");
        paymentReceivedMapper.insert(paymentReceived);

        // 回写项目总收入
        BizProject project = projectMapper.selectById(paymentReceived.getProjectId());
        if (project != null) {
            BigDecimal totalIncome = project.getTotalIncome() == null
                    ? BigDecimal.ZERO : project.getTotalIncome();
            project.setTotalIncome(totalIncome.add(paymentReceived.getReceiveAmount()));
            projectMapper.updateById(project);
        }

        // 回写合同累计收款金额
        if (paymentReceived.getContractId() != null) {
            BizConstructionContract contract = contractMapper.selectById(paymentReceived.getContractId());
            if (contract != null) {
                BigDecimal cumulativeReceived = contract.getCumulativeReceivedAmount() == null
                        ? BigDecimal.ZERO : contract.getCumulativeReceivedAmount();
                contract.setCumulativeReceivedAmount(cumulativeReceived.add(paymentReceived.getReceiveAmount()));
                contractMapper.updateById(contract);
            }
        }
    }
}
