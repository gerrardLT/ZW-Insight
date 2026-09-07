package com.zwinsight.budget.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.domain.BizCostAccountLink;
import com.zwinsight.budget.mapper.BizCostAccountLinkMapper;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 成本账户绑定服务 —— 维护「源单据 → CBS 账户」的显式映射。
 *
 * <h3>存在意义</h3>
 * <p>
 * 一个费用类别下存在多个成本账户时（如材料拆成「混凝土」「钢筋」），
 * 一份合同该记到哪个账户<b>无法从数据推断</b>。归集服务遇到这种歧义会报告 unmapped
 * 而不猜测，本服务就是业务人员消除歧义的入口：绑定一次，之后每次归集自动按绑定分配。
 * </p>
 *
 * <h3>与归集的关系</h3>
 * <p>
 * 绑定本身<b>不直接改金额</b>——它只是声明意图。金额由 {@link CostRollUpService#rollup}
 * 统一计算并写流水，保证「金额只有一个来源」（单一事实源），
 * 避免绑定与归集两条路径同时改账导致对不上。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostAccountLinkService {

    private final BizCostAccountLinkMapper linkMapper;
    private final BizCostAccountMapper costAccountMapper;

    /**
     * 查询某项目的全部绑定。
     */
    public List<BizCostAccountLink> listByProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        return linkMapper.selectByProject(projectId);
    }

    /**
     * 查询某账户的绑定明细（账户详情下钻：这个账户的钱来自哪些单据）。
     */
    public List<BizCostAccountLink> listByAccount(Long accountId) {
        if (accountId == null) {
            throw new BusinessException("成本账户ID不能为空");
        }
        return linkMapper.selectByAccount(accountId);
    }

    /**
     * 新增绑定。
     * <p>
     * 幂等：同一 (accountId, sourceType, sourceId) 已存在时返回既有记录，不报错——
     * 前端重复提交或归集报告批量绑定时不应因唯一键冲突而失败。
     * </p>
     *
     * @return 绑定记录（可能是既有记录）
     */
    @Transactional(rollbackFor = Exception.class)
    public BizCostAccountLink bind(BizCostAccountLink request) {
        validate(request);

        BizCostAccount account = costAccountMapper.selectById(request.getAccountId());
        if (account == null) {
            throw new BusinessException("成本账户不存在：" + request.getAccountId());
        }
        // 跨项目绑定会让归集把别的项目的钱记到本项目账户上，必须拦住
        if (request.getProjectId() != null && !request.getProjectId().equals(account.getProjectId())) {
            throw new BusinessException("成本账户不属于该项目，禁止跨项目绑定");
        }
        request.setProjectId(account.getProjectId());

        BizCostAccountLink existing = linkMapper.selectBySource(
                request.getAccountId(), request.getSourceType(), request.getSourceId());
        if (existing != null) {
            log.info("绑定已存在，幂等返回，accountId={}, source={}:{}",
                    request.getAccountId(), request.getSourceType(), request.getSourceId());
            return existing;
        }

        linkMapper.insert(request);
        log.info("成本账户绑定创建成功，accountId={}, source={}:{}, projectId={}",
                request.getAccountId(), request.getSourceType(), request.getSourceId(), request.getProjectId());
        return request;
    }

    /**
     * 解绑（逻辑删除）。
     * <p>
     * 解绑只影响<b>下一次</b>归集的分配，不会自动冲销已记的账——
     * 因为已记的账是当时正确决策的结果，冲销需要显式的反向操作并留痕。
     * 解绑后重跑归集，该单据会回到 unmapped 报告等待重新绑定。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long id) {
        BizCostAccountLink link = linkMapper.selectById(id);
        if (link == null) {
            throw new BusinessException("绑定记录不存在：" + id);
        }
        linkMapper.deleteById(id);
        log.info("成本账户绑定已解除，id={}, accountId={}, source={}:{}",
                id, link.getAccountId(), link.getSourceType(), link.getSourceId());
    }

    /**
     * 批量绑定（归集报告里一次性处理多张待绑定单据）。
     *
     * @return 实际新建的绑定数（已存在的按幂等跳过，不计入）
     */
    @Transactional(rollbackFor = Exception.class)
    public int bindBatch(List<BizCostAccountLink> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("绑定列表不能为空");
        }
        int created = 0;
        for (BizCostAccountLink request : requests) {
            BizCostAccountLink before = linkMapper.selectBySource(
                    request.getAccountId(), request.getSourceType(), request.getSourceId());
            bind(request);
            if (before == null) {
                created++;
            }
        }
        log.info("批量绑定完成，请求 {} 条，新建 {} 条", requests.size(), created);
        return created;
    }

    /**
     * 查询某项目下「尚未绑定且存在歧义」的单据数（工作台待办角标）。
     */
    public long countUnbound(Long projectId, String sourceType) {
        LambdaQueryWrapper<BizCostAccountLink> wrapper = new LambdaQueryWrapper<BizCostAccountLink>()
                .eq(BizCostAccountLink::getProjectId, projectId);
        if (StrUtil.isNotBlank(sourceType)) {
            wrapper.eq(BizCostAccountLink::getSourceType, sourceType);
        }
        Long bound = linkMapper.selectCount(wrapper);
        return bound != null ? bound : 0L;
    }

    private void validate(BizCostAccountLink request) {
        if (request == null) {
            throw new BusinessException("绑定信息不能为空");
        }
        if (request.getAccountId() == null) {
            throw new BusinessException("成本账户ID不能为空");
        }
        if (StrUtil.isBlank(request.getSourceType())) {
            throw new BusinessException("来源类型不能为空");
        }
        if (StrUtil.isBlank(request.getSourceId())) {
            throw new BusinessException("来源业务ID不能为空");
        }
        if (request.getSourceType().length() > 50) {
            throw new BusinessException("来源类型长度不得超过 50 字符");
        }
        if (request.getSourceId().length() > 100) {
            throw new BusinessException("来源业务ID长度不得超过 100 字符");
        }
    }
}
