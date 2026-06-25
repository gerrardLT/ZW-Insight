package com.zwinsight.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractOutputReport;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractOutputReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 分包产值服务
 */
@Service
@RequiredArgsConstructor
public class SubcontractOutputService {

    private final BizSubcontractOutputReportMapper outputReportMapper;
    private final BizSubcontractMapper subcontractMapper;

    public PageResult<BizSubcontractOutputReport> page(int page, int size, Long projectId, Long contractId) {
        Page<BizSubcontractOutputReport> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSubcontractOutputReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizSubcontractOutputReport::getProjectId, projectId)
                .eq(contractId != null, BizSubcontractOutputReport::getContractId, contractId)
                .orderByDesc(BizSubcontractOutputReport::getCreatedAt);
        return PageResult.of(outputReportMapper.selectPage(pageParam, wrapper));
    }

    public void save(BizSubcontractOutputReport report) {
        report.setStatus("DRAFT");
        outputReportMapper.insert(report);
    }

    public BizSubcontractOutputReport getById(Long id) {
        BizSubcontractOutputReport report = outputReportMapper.selectById(id);
        if (report == null) throw new BusinessException("产值报告不存在");
        return report;
    }

    public void update(BizSubcontractOutputReport report) {
        BizSubcontractOutputReport existing = outputReportMapper.selectById(report.getId());
        if (existing == null) throw new BusinessException("产值报告不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        outputReportMapper.updateById(report);
    }

    public void delete(Long id) {
        BizSubcontractOutputReport existing = outputReportMapper.selectById(id);
        if (existing == null) throw new BusinessException("产值报告不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        outputReportMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizSubcontractOutputReport report = outputReportMapper.selectById(id);
        if (report == null) throw new BusinessException("产值报告不存在");
        if (!"DRAFT".equals(report.getStatus())) throw new BusinessException("仅草稿状态可提交");

        report.setStatus("APPROVED");
        outputReportMapper.updateById(report);
    }
}
