package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 收票登记服务
 */
@Service
@RequiredArgsConstructor
public class InvoiceReceivedService {

    private final BizInvoiceReceivedMapper invoiceReceivedMapper;
    private final BizOtherContractMapper otherContractMapper;

    /**
     * 分页查询
     */
    public PageResult<BizInvoiceReceived> page(int page, int size, Long projectId) {
        Page<BizInvoiceReceived> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInvoiceReceived> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizInvoiceReceived::getProjectId, projectId)
                .orderByDesc(BizInvoiceReceived::getCreatedAt);
        Page<BizInvoiceReceived> result = invoiceReceivedMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增收票（回写合同cumulativeInvoiceReceived）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizInvoiceReceived invoiceReceived) {
        invoiceReceived.setStatus("APPROVED");
        invoiceReceivedMapper.insert(invoiceReceived);

        // 回写其他合同累计收票
        if (invoiceReceived.getContractId() != null) {
            BizOtherContract contract = otherContractMapper.selectById(invoiceReceived.getContractId());
            if (contract != null) {
                BigDecimal cumulativeInvoice = contract.getCumulativeInvoice() == null
                        ? BigDecimal.ZERO : contract.getCumulativeInvoice();
                contract.setCumulativeInvoice(cumulativeInvoice.add(invoiceReceived.getInvoiceAmount()));
                otherContractMapper.updateById(contract);
            }
        }
    }
}
