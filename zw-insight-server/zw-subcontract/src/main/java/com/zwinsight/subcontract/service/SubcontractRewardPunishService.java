package com.zwinsight.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
        rewardPunishMapper.insert(rewardPunish);
    }

    public void delete(Long id) {
        rewardPunishMapper.deleteById(id);
    }
}
