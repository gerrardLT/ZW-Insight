package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizBudgetChange;
import com.zwinsight.budget.domain.BizBudgetChangeDetail;
import com.zwinsight.budget.dto.BudgetChangeDTO;
import com.zwinsight.budget.dto.BudgetChangeDetailDTO;
import com.zwinsight.budget.mapper.BizBudgetChangeDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetChangeMapper;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BizBudgetMapper;
import com.zwinsight.budget.mapper.BudgetOccupiedMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
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
 * 预算变更服务
 * <p>
 * 提供目标成本变更的 CRUD 操作，以及提交前的预算余额校验。
 * 调减校验逻辑：调整后金额 >= 已占用预算（已签合同金额合计）
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetChangeService {

    private final BizBudgetChangeMapper budgetChangeMapper;
    private final BizBudgetChangeDetailMapper budgetChangeDetailMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final BizBudgetMapper budgetMapper;
    private final BudgetOccupiedMapper budgetOccupiedMapper;
    private final ApprovalService approvalService;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询变更记录
     */
    public PageResult<BizBudgetChange> page(int page, int size, Long projectId) {
        Page<BizBudgetChange> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizBudgetChange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizBudgetChange::getProjectId, projectId)
                .orderByDesc(BizBudgetChange::getCreatedAt);
        Page<BizBudgetChange> result = budgetChangeMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询变更记录
     */
    public BizBudgetChange getById(Long id) {
        BizBudgetChange change = budgetChangeMapper.selectById(id);
        if (change == null) {
            throw new BusinessException("预算变更记录不存在");
        }
        return change;
    }

    /**
     * 查询变更明细列表
     */
    public List<BizBudgetChangeDetail> getDetailsByChangeId(Long changeId) {
        LambdaQueryWrapper<BizBudgetChangeDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBudgetChangeDetail::getChangeId, changeId)
                .orderByAsc(BizBudgetChangeDetail::getId);
        return budgetChangeDetailMapper.selectList(wrapper);
    }

    /**
     * 创建预算变更记录
     * <p>
     * 自动计算 totalAdjustAmount = SUM(details.adjustAmount)
     * 自动计算 adjustedAmount = originalAmount + adjustAmount
     * 状态设为 DRAFT
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BudgetChangeDTO dto) {
        // 创建变更主表
        BizBudgetChange change = new BizBudgetChange();
        change.setProjectId(dto.getProjectId());
        change.setBudgetId(dto.getBudgetId());
        change.setChangeReason(dto.getChangeReason());
        change.setStatus("DRAFT");

        // 计算调整总额
        BigDecimal totalAdjustAmount = dto.getDetails().stream()
                .map(BudgetChangeDetailDTO::getAdjustAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        change.setTotalAdjustAmount(totalAdjustAmount);

        budgetChangeMapper.insert(change);

        // 保存变更明细
        for (BudgetChangeDetailDTO detailDTO : dto.getDetails()) {
            BizBudgetChangeDetail detail = new BizBudgetChangeDetail();
            detail.setChangeId(change.getId());
            detail.setBudgetDetailId(detailDTO.getBudgetDetailId());
            detail.setCostCategory(detailDTO.getCostCategory());
            detail.setCostSubcategory(detailDTO.getCostSubcategory());
            detail.setItemName(detailDTO.getItemName());
            detail.setOriginalAmount(detailDTO.getOriginalAmount());
            detail.setAdjustAmount(detailDTO.getAdjustAmount());
            // 自动计算调整后金额
            detail.setAdjustedAmount(
                    detailDTO.getOriginalAmount().add(detailDTO.getAdjustAmount()));
            budgetChangeDetailMapper.insert(detail);
        }
    }

    /**
     * 编辑预算变更记录（仅 DRAFT 状态可编辑）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BudgetChangeDTO dto) {
        BizBudgetChange change = budgetChangeMapper.selectById(id);
        if (change == null) {
            throw new BusinessException("预算变更记录不存在");
        }
        if (!"DRAFT".equals(change.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }

        // 更新主表
        change.setChangeReason(dto.getChangeReason());
        BigDecimal totalAdjustAmount = dto.getDetails().stream()
                .map(BudgetChangeDetailDTO::getAdjustAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        change.setTotalAdjustAmount(totalAdjustAmount);
        budgetChangeMapper.updateById(change);

        // 删除旧明细，重新插入
        LambdaQueryWrapper<BizBudgetChangeDetail> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(BizBudgetChangeDetail::getChangeId, id);
        budgetChangeDetailMapper.delete(deleteWrapper);

        for (BudgetChangeDetailDTO detailDTO : dto.getDetails()) {
            BizBudgetChangeDetail detail = new BizBudgetChangeDetail();
            detail.setChangeId(id);
            detail.setBudgetDetailId(detailDTO.getBudgetDetailId());
            detail.setCostCategory(detailDTO.getCostCategory());
            detail.setCostSubcategory(detailDTO.getCostSubcategory());
            detail.setItemName(detailDTO.getItemName());
            detail.setOriginalAmount(detailDTO.getOriginalAmount());
            detail.setAdjustAmount(detailDTO.getAdjustAmount());
            detail.setAdjustedAmount(
                    detailDTO.getOriginalAmount().add(detailDTO.getAdjustAmount()));
            budgetChangeDetailMapper.insert(detail);
        }
    }

    /**
     * 删除预算变更记录（仅 DRAFT 状态可删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizBudgetChange change = budgetChangeMapper.selectById(id);
        if (change == null) {
            throw new BusinessException("预算变更记录不存在");
        }
        if (!"DRAFT".equals(change.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }

        // 删除明细
        LambdaQueryWrapper<BizBudgetChangeDetail> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(BizBudgetChangeDetail::getChangeId, id);
        budgetChangeDetailMapper.delete(deleteWrapper);

        // 删除主表
        budgetChangeMapper.deleteById(id);
    }

    /**
     * 提交前预算余额校验
     * <p>
     * 遍历变更明细，对于 adjustAmount < 0 的调减项：
     * 计算已占用预算 = 该项目+该科目下已签合同金额合计
     * 调整后金额(originalAmount + adjustAmount) 必须 >= 已占用预算，否则抛异常
     * </p>
     *
     * @param changeId 变更单ID
     */
    public void validateBeforeSubmit(Long changeId) {
        BizBudgetChange change = budgetChangeMapper.selectById(changeId);
        if (change == null) {
            throw new BusinessException("预算变更记录不存在");
        }

        List<BizBudgetChangeDetail> details = getDetailsByChangeId(changeId);
        for (BizBudgetChangeDetail detail : details) {
            // 仅校验调减项
            if (detail.getAdjustAmount().compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal adjustedAmount = detail.getOriginalAmount().add(detail.getAdjustAmount());
                BigDecimal occupiedBudget = calculateOccupiedBudget(
                        change.getProjectId(), detail.getCostCategory());

                if (adjustedAmount.compareTo(occupiedBudget) < 0) {
                    throw new BusinessException(
                            String.format("科目[%s]预算余额不足以支撑调减：调整后金额%.2f < 已占用预算%.2f",
                                    detail.getItemName() != null ? detail.getItemName() : detail.getCostCategory(),
                                    adjustedAmount,
                                    occupiedBudget));
                }
            }
        }
    }

    /**
     * 计算已占用预算：该项目 + 该科目下已签合同金额合计
     * <p>
     * 根据 costCategory 查询对应类型的已生效合同金额：
     * - SUBCONTRACT → biz_subcontract
     * - LABOR → biz_labor_contract
     * - MACHINE → biz_machine_contract
     * - MATERIAL → biz_purchase_contract
     * </p>
     *
     * @param projectId    项目ID
     * @param costCategory 成本大类
     * @return 已占用预算金额
     */
    public BigDecimal calculateOccupiedBudget(Long projectId, String costCategory) {
        if (costCategory == null || projectId == null) {
            return BigDecimal.ZERO;
        }

        return switch (costCategory) {
            case "SUBCONTRACT" -> budgetOccupiedMapper.sumSubcontractAmount(projectId);
            case "LABOR" -> budgetOccupiedMapper.sumLaborContractAmount(projectId);
            case "MACHINE" -> budgetOccupiedMapper.sumMachineContractAmount(projectId);
            case "MATERIAL" -> budgetOccupiedMapper.sumPurchaseContractAmount(projectId);
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * 提交审批
     * <p>
     * 校验变更记录存在且状态为 DRAFT，执行预算余额校验，
     * 调用审批服务启动流程，更新状态为 SUBMITTED 并设置 workflowInstanceId。
     * </p>
     *
     * @param changeId 变更单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long changeId) {
        BizBudgetChange change = budgetChangeMapper.selectById(changeId);
        if (change == null) {
            throw new BusinessException("预算变更记录不存在");
        }
        if (!"DRAFT".equals(change.getStatus())) {
            throw new BusinessException("仅草稿状态可提交审批");
        }

        // 预算余额校验
        validateBeforeSubmit(changeId);

        // 构建流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("businessType", "BUDGET_CHANGE");
        variables.put("projectId", change.getProjectId());
        variables.put("amount", change.getTotalAdjustAmount());

        // 启动审批流程
        String processInstanceId = approvalService.startProcess(
                "BUDGET_CHANGE", changeId, "budget_change_approval", variables);

        // 更新状态和流程实例ID
        change.setStatus("SUBMITTED");
        change.setWorkflowInstanceId(processInstanceId);
        budgetChangeMapper.updateById(change);

        log.info("预算变更单提交审批成功, changeId={}, processInstanceId={}", changeId, processInstanceId);
    }

    /**
     * 审批通过回调
     * <p>
     * 更新状态为 APPROVED，遍历变更明细累加调整金额至对应预算明细，
     * 汇总新的预算总额并回写项目预算金额。
     * </p>
     *
     * @param changeId 变更单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long changeId) {
        BizBudgetChange change = budgetChangeMapper.selectById(changeId);
        if (change == null) {
            throw new BusinessException("预算变更记录不存在");
        }

        // 更新状态为 APPROVED
        change.setStatus("APPROVED");
        budgetChangeMapper.updateById(change);

        // 遍历变更明细，逐科目回写预算明细金额
        List<BizBudgetChangeDetail> details = getDetailsByChangeId(changeId);
        for (BizBudgetChangeDetail detail : details) {
            budgetDetailMapper.addBudgetTotalPrice(detail.getBudgetDetailId(), detail.getAdjustAmount());
        }

        // 汇总该预算下所有明细的预算合计金额
        BigDecimal newTotal = budgetDetailMapper.sumBudgetTotalPriceByBudgetId(change.getBudgetId());

        // 回写项目预算金额
        projectMapper.updateBudgetAmount(change.getProjectId(), newTotal);

        log.info("预算变更审批通过, changeId={}, 新预算总额={}", changeId, newTotal);
    }

    /**
     * 审批驳回回调
     * <p>
     * 更新状态为 REJECTED，不修改原预算数据。
     * </p>
     *
     * @param changeId 变更单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long changeId) {
        BizBudgetChange change = budgetChangeMapper.selectById(changeId);
        if (change == null) {
            throw new BusinessException("预算变更记录不存在");
        }

        change.setStatus("REJECTED");
        budgetChangeMapper.updateById(change);

        log.info("预算变更审批驳回, changeId={}", changeId);
    }

    /**
     * 按项目查询全部变更记录及审批结果（变更轨迹）
     * <p>
     * 包含各状态的变更记录，按创建时间倒序排列。
     * 用于展示项目预算变更的完整历史轨迹。
     * </p>
     *
     * @param projectId 项目ID
     * @return 该项目的全部变更记录列表
     */
    public List<BizBudgetChange> getChangeTraceByProject(Long projectId) {
        LambdaQueryWrapper<BizBudgetChange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBudgetChange::getProjectId, projectId)
                .orderByDesc(BizBudgetChange::getCreatedAt);
        return budgetChangeMapper.selectList(wrapper);
    }

    /**
     * 撤回操作
     * <p>
     * 校验状态为 SUBMITTED，更新状态为 WITHDRAWN，不修改原预算数据。
     * </p>
     *
     * @param changeId 变更单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long changeId) {
        BizBudgetChange change = budgetChangeMapper.selectById(changeId);
        if (change == null) {
            throw new BusinessException("预算变更记录不存在");
        }
        if (!"SUBMITTED".equals(change.getStatus())) {
            throw new BusinessException("仅已提交状态可撤回");
        }

        change.setStatus("WITHDRAWN");
        budgetChangeMapper.updateById(change);

        log.info("预算变更撤回成功, changeId={}", changeId);
    }
}
