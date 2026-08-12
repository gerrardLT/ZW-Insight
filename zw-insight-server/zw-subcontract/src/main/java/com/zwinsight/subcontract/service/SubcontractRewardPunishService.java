package com.zwinsight.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.subcontract.domain.BizSubcontractRewardPunish;
import com.zwinsight.subcontract.mapper.BizSubcontractRewardPunishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 分包奖罚服务
 */
@Service
@RequiredArgsConstructor
public class SubcontractRewardPunishService {

    private final BizSubcontractRewardPunishMapper rewardPunishMapper;

    public PageResult<BizSubcontractRewardPunish> page(int page, int size, Long projectId, Long contractId) {
        Page<BizSubcontractRewardPunish> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSubcontractRewardPunish> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizSubcontractRewardPunish::getProjectId, projectId)
                .eq(contractId != null, BizSubcontractRewardPunish::getContractId, contractId)
                .orderByDesc(BizSubcontractRewardPunish::getCreatedAt);
        return PageResult.of(rewardPunishMapper.selectPage(pageParam, wrapper));
    }

    public void save(BizSubcontractRewardPunish rewardPunish) {
        // P2 守卫（2026-08-12，批次二取证枚举）：奖罚类型白名单 + 金额必须大于 0，
        // 负金额会经结算汇总反向扭曲分包结算金额
        if (rewardPunish.getRpType() == null
                || (!"REWARD".equals(rewardPunish.getRpType()) && !"PUNISH".equals(rewardPunish.getRpType()))) {
            throw new BusinessException("非法的奖罚类型：" + rewardPunish.getRpType());
        }
        if (rewardPunish.getAmount() == null || rewardPunish.getAmount().signum() <= 0) {
            throw new BusinessException("奖罚金额必须大于0");
        }
        rewardPunishMapper.insert(rewardPunish);
    }

    public void delete(Long id) {
        rewardPunishMapper.deleteById(id);
    }
}
