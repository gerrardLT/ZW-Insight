package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
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
     * 新增收票（回写合同cumulativeInvoiceReceived）
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
