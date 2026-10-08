package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizFinalSettlement;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizFinalSettlementMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 竣工结算服务
 */
@Service
@RequiredArgsConstructor
public class FinalSettlementService {

    private final BizFinalSettlementMapper settlementMapper;
    private final BizConstructionContractMapper contractMapper;
    private final BizProjectMapper projectMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizFinalSettlement> page(int page, int size, Long projectId) {
        Page<BizFinalSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizFinalSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizFinalSettlement::getProjectId, projectId)
                .orderByDesc(BizFinalSettlement::getCreatedAt);
        Page<BizFinalSettlement> result = settlementMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存竣工结算（草稿）
     */
    public void save(BizFinalSettlement settlement) {
        settlement.setStatus("DRAFT");
        // 信任边界：流程实例由系统写入
        settlement.setWorkflowInstanceId(null);
        settlementMapper.insert(settlement);
    }

    /**
     * 提交审批：DRAFT→SUBMITTED，仅启动流程；合同置 SETTLED 与项目结算额累加由审批通过回调
     * {@link #onApproved(Long)} 生效（原实现启动流程后立即置 APPROVED 并回写，审批形同虚设）。
     * 提交时即校验引用完整：合同存在、属于该项目且为生效状态，项目存在，金额为正。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizFinalSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("竣工结算不存在");
        }
        if (!"DRAFT".equals(settlement.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        if (settlement.getSettlementAmount() == null || settlement.getSettlementAmount().signum() <= 0) {
            throw new BusinessException("竣工结算金额必须大于0");
        }
        BizConstructionContract contract = contractMapper.selectById(settlement.getContractId());
        if (contract == null) {
            throw new BusinessException("关联的施工合同不存在");
        }
        if (!java.util.Objects.equals(contract.getProjectId(), settlement.getProjectId())) {
            throw new BusinessException("施工合同不属于该项目");
        }
        // D2 守卫（2026-08-11）：仅生效合同可竣工结算
        if (!"EFFECTIVE".equals(contract.getStatus()) && !"SETTLED".equals(contract.getStatus())) {
            throw new BusinessException("仅生效状态的合同可进行竣工结算，当前合同状态：" + contract.getStatus());
        }
        if (projectMapper.selectById(settlement.getProjectId()) == null) {
            throw new BusinessException("关联项目不存在");
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("settlementAmount", settlement.getSettlementAmount());
        variables.put("projectId", settlement.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "FINAL_SETTLEMENT", id, "final_settlement_approval", variables);

        settlement.setWorkflowInstanceId(processInstanceId);
        settlement.setStatus("SUBMITTED");
        settlementMapper.updateById(settlement);
    }

    /**
     * 审批通过回调：SUBMITTED→APPROVED（状态 CAS），合同置 SETTLED，项目结算额原子累加。
     * 任一步失败抛出，由审批引擎事务整体回滚，不留下"已批准但未生效"的半成品。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizFinalSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("竣工结算不存在");
        }
        if ("APPROVED".equals(settlement.getStatus())) {
            return; // 重复回调幂等
        }
        if (!"SUBMITTED".equals(settlement.getStatus())) {
            throw new BusinessException("竣工结算状态异常，无法生效：" + settlement.getStatus());
        }
        int moved = settlementMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFinalSettlement>()
                .eq(BizFinalSettlement::getId, id)
                .eq(BizFinalSettlement::getStatus, "SUBMITTED")
                .set(BizFinalSettlement::getStatus, "APPROVED"));
        if (moved != 1) {
            return; // 并发回调已处理
        }
        BizConstructionContract contract = contractMapper.selectById(settlement.getContractId());
        if (contract == null) {
            throw new BusinessException("关联的施工合同不存在，竣工结算无法生效");
        }
        if (!"EFFECTIVE".equals(contract.getStatus()) && !"SETTLED".equals(contract.getStatus())) {
            throw new BusinessException("合同状态已变更为 " + contract.getStatus() + "，竣工结算无法生效");
        }
        contract.setStatus("SETTLED");
        contractMapper.updateById(contract);
        if (projectMapper.addSettlementAmount(settlement.getProjectId(), settlement.getSettlementAmount()) != 1) {
            throw new BusinessException("关联项目不存在，竣工结算无法生效");
        }
    }

    /** 审批驳回/撤回回调：SUBMITTED→DRAFT（数据未生效，无需回冲；非 SUBMITTED 幂等跳过） */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        settlementMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFinalSettlement>()
                .eq(BizFinalSettlement::getId, id)
                .eq(BizFinalSettlement::getStatus, "SUBMITTED")
                .set(BizFinalSettlement::getStatus, "DRAFT"));
    }
}
