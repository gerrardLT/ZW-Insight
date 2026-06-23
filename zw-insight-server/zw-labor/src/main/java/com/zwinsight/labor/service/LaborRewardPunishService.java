package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborRewardPunish;
import com.zwinsight.labor.mapper.BizLaborRewardPunishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 劳务奖罚服务
 */
@Service
@RequiredArgsConstructor
public class LaborRewardPunishService {

    private final BizLaborRewardPunishMapper rewardPunishMapper;

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
        rewardPunishMapper.insert(rewardPunish);
    }

    /**
     * 删除
     */
    public void delete(Long id) {
        rewardPunishMapper.deleteById(id);
    }
}
