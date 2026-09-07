package com.zwinsight.budget.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CBS 成本账户服务（成本分解结构）。
 * <p>
 * 承载「目标成本 → 当前预算 → 已承诺 → 实际成本 → 完工预测 → 偏差」的完整主线。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostAccountService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_CLOSED = "CLOSED";

    private final BizCostAccountMapper costAccountMapper;

    // ==================== 查询 ====================

    /**
     * 分页查询成本账户
     */
    public PageResult<BizCostAccount> page(int page, int size, Long projectId,
                                           Long parentId, Long wbsNodeId,
                                           String costCategory, String status) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        Page<BizCostAccount> p = new Page<>(page, size);
        return PageResult.of(
                costAccountMapper.selectAccountPage(p, projectId, parentId, wbsNodeId, costCategory, status, null));
    }

    /**
     * 树形查询（一次性取全量后内存装配，O(n)）
     */
    public List<BizCostAccount> getTree(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        List<BizCostAccount> all = costAccountMapper.selectByProject(projectId);
        Map<Long, BizCostAccount> byId = new HashMap<>(all.size() * 2);
        for (BizCostAccount acc : all) {
            acc.setChildren(new ArrayList<>());
            byId.put(acc.getId(), acc);
        }
        List<BizCostAccount> roots = new ArrayList<>();
        for (BizCostAccount acc : all) {
            Long pid = acc.getParentId();
            BizCostAccount parent = pid != null ? byId.get(pid) : null;
            if (parent != null && !pid.equals(acc.getId())) {
                parent.getChildren().add(acc);
            } else {
                roots.add(acc);
            }
        }
        return roots;
    }

    /**
     * 查询节点详情
     */
    public BizCostAccount getById(Long id) {
        BizCostAccount account = costAccountMapper.selectById(id);
        if (account == null) {
            throw new BusinessException("成本账户不存在：" + id);
        }
        return account;
    }

    /**
     * 按费用类别汇总（一次 SQL 出六维指标）
     */
    public List<BizCostAccountMapper.CostAccountCategorySum> sumByCategory(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        return costAccountMapper.sumByCategory(projectId);
    }

    // ==================== 写入 ====================

    /**
     * 新增成本账户（level 由父节点推导，调用方无需指定）
     */
    @Transactional(rollbackFor = Exception.class)
    public BizCostAccount create(BizCostAccount account) {
        validateBasic(account, true);

        if (costAccountMapper.selectCount(new LambdaQueryWrapper<BizCostAccount>()
                        .eq(BizCostAccount::getProjectId, account.getProjectId())
                        .eq(BizCostAccount::getAccountCode, account.getAccountCode())) > 0) {
            throw new BusinessException("成本账户编号已存在：" + account.getAccountCode());
        }

        deriveLevel(account);

        if (StrUtil.isBlank(account.getStatus())) {
            account.setStatus(STATUS_ACTIVE);
        }

        initAmounts(account);

        costAccountMapper.insert(account);
        log.info("成本账户创建成功，id={}, code={}", account.getId(), account.getAccountCode());
        return account;
    }

    /**
     * 更新成本账户
     */
    @Transactional(rollbackFor = Exception.class)
    public BizCostAccount update(Long id, BizCostAccount patch) {
        BizCostAccount existing = getById(id);

        if (patch.getAccountCode() != null && !patch.getAccountCode().equals(existing.getAccountCode())) {
            assertUnique(existing.getProjectId(), patch.getAccountCode(), id);
            existing.setAccountCode(patch.getAccountCode());
        }
        if (patch.getAccountName() != null) {
            existing.setAccountName(patch.getAccountName());
        }
        if (patch.getCostSubcategory() != null) {
            existing.setCostSubcategory(patch.getCostSubcategory());
        }
        if (patch.getRemark() != null) {
            existing.setRemark(patch.getRemark());
        }
        if (patch.getStatus() != null) {
            existing.setStatus(patch.getStatus());
        }
        if (patch.getWbsNodeId() != null && !patch.getWbsNodeId().equals(existing.getWbsNodeId())) {
            existing.setWbsNodeId(patch.getWbsNodeId());
        }

        validateDateRange(null, null, existing.getAccountCode()); // TODO: add date fields to entity if needed

        costAccountMapper.updateById(existing);
        log.info("成本账户更新成功，id={}, code={}", id, existing.getAccountCode());
        return existing;
    }

    /**
     * 锁定成本账户（禁止进一步修改金额）
     */
    @Transactional(rollbackFor = Exception.class)
    public void lock(Long id) {
        BizCostAccount account = getById(id);
        if ("CLOSED".equals(account.getStatus())) {
            throw new BusinessException("账户已关闭，无法锁定");
        }
        account.setStatus(STATUS_LOCKED);
        costAccountMapper.updateById(account);
        log.info("成本账户锁定成功，id={}, code={}", id, account.getAccountCode());
    }

    /**
     * 关闭成本账户（归档；解锁后允许改状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        BizCostAccount account = getById(id);
        if ("LOCKED".equals(account.getStatus())) {
            account.setStatus(STATUS_ACTIVE);
            costAccountMapper.updateById(account);
        }
        account.setStatus(STATUS_CLOSED);
        costAccountMapper.updateById(account);
        log.info("成本账户关闭成功，id={}, code={}", id, account.getAccountCode());
    }

    /**
     * 删除成本账户（逻辑删除；有子账户时拒绝）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizCostAccount account = getById(id);
        Long childCount = costAccountMapper.selectCount(
                new LambdaQueryWrapper<BizCostAccount>().eq(BizCostAccount::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("该账户下还有 " + childCount + " 个子账户，请先删除子账户："
                    + account.getAccountCode());
        }
        costAccountMapper.deleteById(id);
        log.info("成本账户删除成功，id={}, code={}", id, account.getAccountCode());
    }

    // ==================== 成本主线：变更事件审批通过后的传导 ====================

    /**
     * 处理变更事件审批通过的回调，调整对应成本账户的 current_amount。
     * <p>
     * changeEvent.getAffectedAccounts() 是 [{accountId, deltaType="INCREASE"/"DECREASE", deltaAmount}]
     * 我们直接走 adjustCurrentAmount 原子自增，避免并发冲突。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleChangeEventApproval(List<Long> accountIds, BigDecimal totalDelta) {
        log.info("变更事件审批通过，调整成本账户：ids={}, totalDelta={}", accountIds, totalDelta);

        for (Long accountId : accountIds) {
            long affected = costAccountMapper.adjustCurrentAmount(accountId, totalDelta);
            if (affected == 0) {
                // 要么不存在，要么调整后为负数被拒绝
                log.warn("调整成本账户失败，id={}, totalDelta={}, affected={}", accountId, totalDelta, affected);
                throw new BusinessException("成本账户 [" + accountId + "] 调整失败");
            }
        }
    }

    /**
     * 从源模块同步金额（合同承诺/采购/材料结算等）
     *
     * @param accountId        成本账户 ID
     * @param commitmentDelta  承诺金额变更值
     * @param actualDelta      实际金额变更值
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncFromSource(Long accountId, BigDecimal commitmentDelta, BigDecimal actualDelta) {
        BizCostAccount account = getById(accountId);

        if (commitmentDelta != null && commitmentDelta.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal newCommitment = nvl(account.getCommitmentAmount()).add(commitmentDelta);
            if (newCommitment.signum() < 0) {
                throw new BusinessException("承诺金额不能为负");
            }
            account.setCommitmentAmount(newCommitment);
            log.info("同步承诺金额，accountId={}, delta={}, new={}", accountId, commitmentDelta, newCommitment);
        }

        if (actualDelta != null && actualDelta.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal newActual = nvl(account.getActualAmount()).add(actualDelta);
            if (newActual.signum() < 0) {
                throw new BusinessException("实际金额不能为负");
            }
            account.setActualAmount(newActual);
            log.info("同步实际金额，accountId={}, delta={}, new={}", accountId, actualDelta, newActual);
        }

        costAccountMapper.updateById(account);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ==================== 内部工具 ====================

    private void validateBasic(BizCostAccount account, boolean isCreate) {
        if (isCreate && account.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (StrUtil.isBlank(account.getAccountCode())) {
            throw new BusinessException("成本账户编号不能为空");
        }
        if (account.getAccountCode().length() > 50) {
            throw new BusinessException("成本账户编号长度不得超过 50 字符");
        }
        if (StrUtil.isBlank(account.getAccountName())) {
            throw new BusinessException("成本账户名称不能为空");
        }
        if (account.getAccountName().length() > 200) {
            throw new BusinessException("成本账户名称长度不得超过 200 字符");
        }
        if (StrUtil.isBlank(account.getCostCategory())) {
            throw new BusinessException("费用类别不能为空");
        }
    }

    private void deriveLevel(BizCostAccount account) {
        // MVP 阶段：成本账户层级仅用于树展示，不涉及复杂的 level 深度校验
        // 如有需要，可在 BizCostAccount 增加 nodeLevel 字段并实现层级自增逻辑
    }

    private void initAmounts(BizCostAccount account) {
        if (account.getBaselineAmount() == null) account.setBaselineAmount(BigDecimal.ZERO);
        if (account.getCurrentAmount() == null) account.setCurrentAmount(BigDecimal.ZERO);
        if (account.getCommitmentAmount() == null) account.setCommitmentAmount(BigDecimal.ZERO);
        if (account.getActualAmount() == null) account.setActualAmount(BigDecimal.ZERO);
        if (account.getForecastAmount() == null) account.setForecastAmount(BigDecimal.ZERO);
    }

    private void assertUnique(Long projectId, String code, Long excludeId) {
        Long count = costAccountMapper.selectCount(
                new LambdaQueryWrapper<BizCostAccount>()
                        .eq(BizCostAccount::getProjectId, projectId)
                        .eq(BizCostAccount::getAccountCode, code)
                        .ne(excludeId != null, BizCostAccount::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("成本账户编号已存在：" + code);
        }
    }

    private void validateDateRange(LocalDate start, LocalDate end, String code) {
        // TBD: entity 目前无 start/end 日期，跳过
    }
}
