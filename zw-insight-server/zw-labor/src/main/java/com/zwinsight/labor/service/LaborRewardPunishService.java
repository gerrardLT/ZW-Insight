package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborRewardPunish;
import com.zwinsight.labor.mapper.BizLaborRewardPunishMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 劳务奖罚服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaborRewardPunishService {

    private final BizLaborRewardPunishMapper rewardPunishMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizLaborRewardPunish> page(int page, int size, Long projectId, Long contractId) {
        Page<BizLaborRewardPunish> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizLaborRewardPunish> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizLaborRewardPunish::getProjectId, projectId)
                .eq(contractId != null, BizLaborRewardPunish::getContractId, contractId)
                .orderByDesc(BizLaborRewardPunish::getCreatedAt);
        Page<BizLaborRewardPunish> result = rewardPunishMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存奖罚
     */
    public void save(BizLaborRewardPunish rewardPunish) {
        if (rewardPunish.getAmount() == null || rewardPunish.getAmount().signum() <= 0) {
            throw new BusinessException("奖惩金额必须大于0");
        }
        if (!"REWARD".equals(rewardPunish.getRpType()) && !"PUNISH".equals(rewardPunish.getRpType())) {
            throw new BusinessException("奖惩类型必须为 REWARD 或 PUNISH");
        }
        rewardPunish.setStatus("DRAFT");
        rewardPunish.setWorkflowInstanceId(null);
        rewardPunishMapper.insert(rewardPunish);
    }

    /**
     * 根据ID查询
     */
    public BizLaborRewardPunish getById(Long id) {
        BizLaborRewardPunish record = rewardPunishMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("奖罚记录不存在");
        }
        return record;
    }

    /**
     * 更新奖罚（仅 DRAFT）
     */
    public void update(BizLaborRewardPunish rewardPunish) {
        BizLaborRewardPunish existing = rewardPunishMapper.selectById(rewardPunish.getId());
        if (existing == null) {
            throw new BusinessException("奖罚记录不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        if (rewardPunish.getAmount() != null && rewardPunish.getAmount().signum() <= 0) {
            throw new BusinessException("奖惩金额必须大于0");
        }
        if (rewardPunish.getRpType() != null && !"REWARD".equals(rewardPunish.getRpType()) && !"PUNISH".equals(rewardPunish.getRpType())) {
            throw new BusinessException("奖惩类型必须为 REWARD 或 PUNISH");
        }
        existing.setRpType(rewardPunish.getRpType());
        existing.setAmount(rewardPunish.getAmount());
        existing.setReason(rewardPunish.getReason());
        if (rewardPunish.getProjectId() != null) {
            existing.setProjectId(rewardPunish.getProjectId());
        }
        if (rewardPunish.getContractId() != null) {
            existing.setContractId(rewardPunish.getContractId());
        }
        rewardPunishMapper.updateById(existing);
    }

    /**
     * 删除
     */
    public void delete(Long id) {
        BizLaborRewardPunish existing = rewardPunishMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("奖罚记录不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        rewardPunishMapper.deleteById(id);
    }

    /**
     * 提交审批
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizLaborRewardPunish record = rewardPunishMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("奖罚记录不存在");
        }
        if (!"DRAFT".equals(record.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("amount", record.getAmount());
        variables.put("projectId", record.getProjectId());
        variables.put("contractId", record.getContractId());
        String processInstanceId = approvalService.startProcess(
                "LABOR_REWARD_PUNISH", id, "labor_reward_punish_approval", variables);

        record.setWorkflowInstanceId(processInstanceId);
        record.setStatus("SUBMITTED");
        rewardPunishMapper.updateById(record);
    }

    /**
     * 审批通过回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizLaborRewardPunish record = rewardPunishMapper.selectById(id);
        if (record == null) {
            log.warn("劳务奖罚审批通过回调：单据不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(record.getStatus())) {
            log.info("劳务奖罚已生效，跳过重复回调, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(record.getStatus())) {
            log.warn("劳务奖罚当前状态非 SUBMITTED，忽略生效回调: id={}, status={}", id, record.getStatus());
            return;
        }

        record.setStatus("APPROVED");
        rewardPunishMapper.updateById(record);
        log.info("劳务奖罚审批通过: id={}", id);
    }

    /**
     * 审批驳回回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizLaborRewardPunish record = rewardPunishMapper.selectById(id);
        if (record == null) {
            log.warn("劳务奖罚审批驳回回调：单据不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(record.getStatus())) {
            log.warn("劳务奖罚当前状态非 SUBMITTED，忽略驳回回调: id={}, status={}", id, record.getStatus());
            return;
        }

        record.setStatus("DRAFT");
        rewardPunishMapper.updateById(record);
        log.info("劳务奖罚审批驳回回退草稿: id={}", id);
    }
}
