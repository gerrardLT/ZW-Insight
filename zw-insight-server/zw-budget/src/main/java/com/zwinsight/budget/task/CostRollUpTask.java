package com.zwinsight.budget.task;

import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.budget.service.CostRollUpService;
import com.zwinsight.security.service.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 成本归集定时任务 —— 让成本主线自动保持新鲜。
 *
 * <h3>为什么必须定时跑</h3>
 * <p>
 * 归集若只能手工触发，实际结果就是「没人记得点」：合同签了一周，
 * 看板上的承诺额还是旧数，项目经理据此做的判断全是错的。
 * 成本数据的价值在于<b>及时</b>，因此必须有兜底的自动对账。
 * </p>
 * <p>
 * 同时保留手工触发入口（{@code POST /api/v1/budget/cost-account/rollup}），
 * 供业务人员在关键节点（月度结算、竣工核算）立即对账。
 * </p>
 *
 * <h3>为什么可以放心重复跑</h3>
 * <p>
 * 归集算的是「目标绝对值」，与库内当前值做差后写流水，幂等键编码状态跃迁
 * （{@code ROLLUP:{accountId}:{amountType}:{from}->{to}}）。因此：
 * </p>
 * <ul>
 *   <li>无变化 → delta=0 → 不写流水，零副作用</li>
 *   <li>重复跑 → 同一跃迁键 → 唯一索引拦截，不会双记</li>
 *   <li>源单据变化 → 新跃迁 → 正常记账</li>
 * </ul>
 *
 * <h3>集群安全</h3>
 * <p>Redis 分布式锁保证多实例下只有一台执行；租户上下文由 {@link TenantTaskRunner}
 * 逐个设置（无上下文时租户拦截器会注入 tenant_id=0 导致查空，历史踩坑见 ContractExpiryTask）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostRollUpTask {

    /** 分布式锁 Key */
    private static final String LOCK_KEY = "task:cost-rollup:lock";

    /** 锁 TTL（分钟）：单轮归集涉及多项目多次 SQL，给足余量防中途锁失效 */
    private static final long LOCK_TTL_MINUTES = 30;

    /** 单轮最多处理项目数，防止项目量大时单轮跑太久（下轮继续，最终一致） */
    private static final int MAX_PROJECTS_PER_RUN = 200;

    private final CostRollUpService costRollUpService;
    private final BizCostAccountMapper costAccountMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final TenantTaskRunner tenantTaskRunner;

    /**
     * 每日 02:30 执行成本归集对账。
     * <p>
     * 选在凌晨：避开业务高峰，且此时前一日单据基本审批完毕，归集结果最完整。
     * cron 可通过 {@code task.cost-rollup.cron} 覆盖，便于按租户作息调整。
     * </p>
     */
    @Scheduled(cron = "${task.cost-rollup.cron:0 30 2 * * ?}")
    public void execute() {
        log.info("成本归集定时任务开始执行");

        if (!acquireLock()) {
            log.info("成本归集未获取到分布式锁，跳过本轮（可能已有其他实例在执行）");
            return;
        }

        try {
            tenantTaskRunner.runForActiveTenants("成本归集", tenantId -> doExecute());
        } finally {
            releaseLock();
        }
    }

    /**
     * 单租户内的归集逻辑（已在租户上下文中）。
     */
    void doExecute() {
        List<Long> projectIds;
        try {
            projectIds = costAccountMapper.selectProjectIdsWithAccounts();
        } catch (Exception e) {
            log.error("查询待归集项目失败，本租户跳过", e);
            return;
        }

        if (projectIds == null || projectIds.isEmpty()) {
            log.debug("本租户无已建 CBS 账户的项目，跳过归集");
            return;
        }

        if (projectIds.size() > MAX_PROJECTS_PER_RUN) {
            log.warn("待归集项目数 {} 超过单轮上限 {}，本轮只处理前 {} 个（下轮继续）",
                    projectIds.size(), MAX_PROJECTS_PER_RUN, MAX_PROJECTS_PER_RUN);
            projectIds = projectIds.subList(0, MAX_PROJECTS_PER_RUN);
        }

        int okProjects = 0;
        int failedProjects = 0;
        int totalPosted = 0;
        int totalUnmapped = 0;

        for (Long projectId : projectIds) {
            try {
                CostRollUpService.RollupReport report = costRollUpService.rollup(projectId);
                okProjects++;
                totalPosted += report.getPostedCount();
                totalUnmapped += report.getUnmapped().size();

                // 待绑定单据是需要人工介入的信号，单独 WARN 出来便于运维发现
                if (!report.getUnmapped().isEmpty()) {
                    log.warn("项目 {} 有 {} 张单据未能归集（科目下多账户需显式绑定）",
                            projectId, report.getUnmapped().size());
                }
                if (!report.getFailed().isEmpty()) {
                    log.warn("项目 {} 有 {} 笔归集记账失败：{}",
                            projectId, report.getFailed().size(),
                            report.getFailed().get(0).getReason());
                }
            } catch (Exception e) {
                // 单项目失败不影响其余项目：归集是尽力而为的对账，下轮会重试
                failedProjects++;
                log.error("项目 {} 成本归集失败，继续下一个项目", projectId, e);
            }
        }

        log.info("成本归集任务完成：成功 {} 个项目，失败 {} 个，记账 {} 笔，待绑定单据 {} 张",
                okProjects, failedProjects, totalPosted, totalUnmapped);
    }

    private boolean acquireLock() {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "locked", LOCK_TTL_MINUTES, TimeUnit.MINUTES);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            // Redis 不可用时选择「不执行」而非「裸跑」：
            // 多实例并发归集虽有幂等键兜底不会记错账，但会产生大量无谓的 DB 压力
            log.error("获取成本归集分布式锁异常，本轮跳过", e);
            return false;
        }
    }

    private void releaseLock() {
        try {
            stringRedisTemplate.delete(LOCK_KEY);
        } catch (Exception e) {
            log.error("释放成本归集分布式锁异常（TTL 到期会自动释放）", e);
        }
    }
}
