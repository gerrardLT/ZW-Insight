package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.domain.BizLaborOutputReport;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.mapper.BizLaborOutputReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 劳务产值报告服务
 */
@Service
@RequiredArgsConstructor
public class LaborOutputReportService {

    private final BizLaborOutputReportMapper outputReportMapper;
    private final BizLaborContractMapper laborContractMapper;

    /**
     * 分页查询
     */
    public PageResult<BizLaborOutputReport> page(int page, int size, Long projectId, Long contractId) {
        Page<BizLaborOutputReport> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizLaborOutputReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizLaborOutputReport::getProjectId, projectId)
                .eq(contractId != null, BizLaborOutputReport::getContractId, contractId)
                .orderByDesc(BizLaborOutputReport::getCreatedAt);
        Page<BizLaborOutputReport> result = outputReportMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存产值报告
     */
    public void save(BizLaborOutputReport report) {
        report.setStatus("DRAFT");
        outputReportMapper.insert(report);
    }

    /**
     * 根据ID查询
     */
    public BizLaborOutputReport getById(Long id) {
        BizLaborOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException("产值报告不存在");
        }
        return report;
    }

    /**
     * 更新产值报告
     */
    public void update(BizLaborOutputReport report) {
        BizLaborOutputReport existing = outputReportMapper.selectById(report.getId());
        if (existing == null) {
            throw new BusinessException("产值报告不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        outputReportMapper.updateById(report);
    }

    /**
     * 删除产值报告
     */
    public void delete(Long id) {
        BizLaborOutputReport existing = outputReportMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("产值报告不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        outputReportMapper.deleteById(id);
    }

    /**
     * 提交（回写合同累计结算）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizLaborOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException("产值报告不存在");
        }
        if (!"DRAFT".equals(report.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        report.setStatus("APPROVED");
        outputReportMapper.updateById(report);

        // 回写合同累计结算
        BizLaborContract contract = laborContractMapper.selectById(report.getContractId());
        if (contract != null) {
            BigDecimal cumulative = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            contract.setCumulativeSettlement(cumulative.add(report.getCurrentOutput()));
            laborContractMapper.updateById(contract);
        }
    }
}
