package com.zwinsight.budget.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.budget.domain.BizCostAccountLink;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * CBS 成本账户绑定 Mapper。
 */
@Mapper
public interface BizCostAccountLinkMapper extends BaseMapper<BizCostAccountLink> {

    /**
     * 查询某项目的全部绑定关系（归集时一次性载入，避免逐单据查询 N+1）。
     */
    default List<BizCostAccountLink> selectByProject(Long projectId) {
        return selectList(new LambdaQueryWrapper<BizCostAccountLink>()
                .eq(BizCostAccountLink::getProjectId, projectId));
    }

    /**
     * 查询某账户的全部绑定关系（账户详情下钻用）。
     */
    default List<BizCostAccountLink> selectByAccount(Long accountId) {
        return selectList(new LambdaQueryWrapper<BizCostAccountLink>()
                .eq(BizCostAccountLink::getAccountId, accountId)
                .orderByDesc(BizCostAccountLink::getCreatedAt));
    }

    /**
     * 幂等探针：判断某源单据是否已绑定到某账户。
     */
    default BizCostAccountLink selectBySource(Long accountId, String sourceType, String sourceId) {
        return selectOne(new LambdaQueryWrapper<BizCostAccountLink>()
                .eq(BizCostAccountLink::getAccountId, accountId)
                .eq(BizCostAccountLink::getSourceType, sourceType)
                .eq(BizCostAccountLink::getSourceId, sourceId)
                .last("LIMIT 1"));
    }
}
