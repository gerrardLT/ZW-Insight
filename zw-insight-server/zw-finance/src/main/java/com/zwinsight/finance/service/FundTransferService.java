package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.finance.domain.BizFundTransfer;
import com.zwinsight.finance.mapper.BizFundTransferMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨项目资金调度服务
 * <p>
 * 业务规则：
 * <ul>
 *   <li>金额必须大于0</li>
 *   <li>调出和调入项目不能相同</li>
 *   <li>审批通过后自动回写项目的总收入/总支出</li>
 *   <li>调出项目：totalExpense += transferAmount</li>
 *   <li>调入项目：totalIncome += transferAmount</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundTransferService {

    private final BizFundTransferMapper fundTransferMapper;
    private final BizProjectMapper projectMapper;
    private final SerialNumberService serialNumberService;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizFundTransfer> page(int page, int size, Long fromProjectId, Long toProjectId) {
        Page<BizFundTransfer> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizFundTransfer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(fromProjectId != null, BizFundTransfer::getFromProjectId, fromProjectId)
                .eq(toProjectId != null, BizFundTransfer::getToProjectId, toProjectId)
                .orderByDesc(BizFundTransfer::getCreatedAt);
        return PageResult.of(fundTransferMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 创建资金调度单
     */
    public void save(BizFundTransfer transfer) {
        // 校验
        if (transfer.getTransferAmount() == null || transfer.getTransferAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("调拨金额必须大于0");
        }
        if (transfer.getFromProjectId() != null && transfer.getFromProjectId().equals(transfer.getToProjectId())) {
            throw new BusinessException("调出和调入项目不能相同");
        }

        // 自动编号
        String code = serialNumberService.generate("FUND_TRANSFER");
        transfer.setTransferCode(code);
        transfer.setStatus("DRAFT");
        fundTransferMapper.insert(transfer);
    }

    /**
     * 提交审批
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizFundTransfer transfer = fundTransferMapper.selectById(id);
        if (transfer == null) throw new BusinessException("资金调度单不存在");
        if (!"DRAFT".equals(transfer.getStatus())) throw new BusinessException("仅草稿状态可提交");

        // 发起审批
        Map<String, Object> variables = new HashMap<>();
        variables.put("amount", transfer.getTransferAmount());
        String processInstanceId = approvalService.startProcess(
                "FUND_TRANSFER", id, "fund_transfer_approval", variables);

        transfer.setWorkflowInstanceId(processInstanceId);
        transfer.setStatus("APPROVED");
        fundTransferMapper.updateById(transfer);

        // 回写项目资金
        writeBackProjectFund(transfer);
        log.info("资金调度审批通过: {} -> {}, 金额: {}",
                transfer.getFromProjectId(), transfer.getToProjectId(), transfer.getTransferAmount());
    }

    /**
     * 审批通过后回写项目资金
     * <p>
     * - 调出项目（fromProjectId）：totalExpense += amount（资金流出）
     * - 调入项目（toProjectId）：totalIncome += amount（资金流入）
     * - 公司资金池（project_id=NULL）：不回写项目表
     * </p>
     */
    private void writeBackProjectFund(BizFundTransfer transfer) {
        BigDecimal amount = transfer.getTransferAmount();

        // 调出项目
        if (transfer.getFromProjectId() != null) {
            BizProject fromProject = projectMapper.selectById(transfer.getFromProjectId());
            if (fromProject != null) {
                BigDecimal expense = fromProject.getTotalExpense() != null ? fromProject.getTotalExpense() : BigDecimal.ZERO;
                fromProject.setTotalExpense(expense.add(amount));
                projectMapper.updateById(fromProject);
            }
        }

        // 调入项目
        if (transfer.getToProjectId() != null) {
            BizProject toProject = projectMapper.selectById(transfer.getToProjectId());
            if (toProject != null) {
                BigDecimal income = toProject.getTotalIncome() != null ? toProject.getTotalIncome() : BigDecimal.ZERO;
                toProject.setTotalIncome(income.add(amount));
                projectMapper.updateById(toProject);
            }
        }
    }

    /**
     * 资金池总览
     * <p>
     * 汇总所有项目的资金进出情况，计算公司可用资金池余额。
     * </p>
     */
    public Map<String, Object> getFundPoolOverview() {
        Map<String, Object> overview = new HashMap<>();

        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<>());

        BigDecimal totalIncome = projects.stream()
                .map(p -> p.getTotalIncome() != null ? p.getTotalIncome() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = projects.stream()
                .map(p -> p.getTotalExpense() != null ? p.getTotalExpense() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal poolBalance = totalIncome.subtract(totalExpense);

        overview.put("totalIncome", totalIncome);
        overview.put("totalExpense", totalExpense);
        overview.put("poolBalance", poolBalance);
        overview.put("projectCount", projects.size());

        // 各项目资金余额
        List<Map<String, Object>> projectFunds = projects.stream().map(p -> {
            Map<String, Object> item = new HashMap<>();
            item.put("projectId", p.getId());
            item.put("projectName", p.getProjectName());
            BigDecimal income = p.getTotalIncome() != null ? p.getTotalIncome() : BigDecimal.ZERO;
            BigDecimal expense = p.getTotalExpense() != null ? p.getTotalExpense() : BigDecimal.ZERO;
            item.put("balance", income.subtract(expense));
            item.put("totalIncome", income);
            item.put("totalExpense", expense);
            return item;
        }).toList();
        overview.put("projectFunds", projectFunds);

        return overview;
    }

    public void delete(Long id) {
        BizFundTransfer existing = fundTransferMapper.selectById(id);
        if (existing == null) throw new BusinessException("资金调度单不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        fundTransferMapper.deleteById(id);
    }
}
