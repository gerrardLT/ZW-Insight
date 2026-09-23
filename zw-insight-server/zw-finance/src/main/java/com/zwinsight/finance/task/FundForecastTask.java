package com.zwinsight.finance.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.finance.service.FundPlanService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.security.service.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 滚动资金预测每日刷新任务（V2026_56/57 数据源重做后配套）
 * <p>
 * 现状缺口：generateRollingForecast 仅手动触发，老板资金看板 rollingGaps 读的是过期快照。
 * 本任务每日 01:15 自动重生成「公司整体 + 在建项目」两级快照（覆盖式写入，与手动触发同一实现，
 * 无第二套口径）。数据源：付款侧 = APPROVED 且 UNPAID 付款申请按付款日落月；
 * 收款侧 = 应收台账 OPEN 余额按到期日落月（无台账回退月度计划）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundForecastTask {

    private final FundPlanService fundPlanService;
    private final BizProjectMapper projectMapper;
    private final TenantTaskRunner tenantTaskRunner;

    /** 预测窗口月数（与前端默认一致，可配置） */
    @Value("${zw.finance.forecast.months:6}")
    private int forecastMonths;

    /** 参与项目级预测的项目状态（在建/竣工未结阶段才有资金预测意义） */
    private static final List<String> FORECAST_PROJECT_STATUSES =
            List.of("CONSTRUCTION", "COMPLETED", "CLOSING");

    /**
     * 每日 01:15 执行（错开成本归集等凌晨任务）
     */
    @Scheduled(cron = "0 15 1 * * ?")
    public void execute() {
        log.info("滚动资金预测刷新任务开始执行, months={}", forecastMonths);
        tenantTaskRunner.runForActiveTenants("滚动资金预测刷新", tenantId -> doExecute());
    }

    void doExecute() {
        // 1. 公司整体快照（projectId=null）
        boolean companyFailed = false;
        try {
            fundPlanService.generateRollingForecast(null, forecastMonths);
        } catch (Exception e) {
            companyFailed = true;
            log.error("公司级滚动预测刷新失败", e);
        }

        // 2. 在建项目逐个刷新（单项目失败不中断整体，错误如实记录）
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .in(BizProject::getStatus, FORECAST_PROJECT_STATUSES));
        int success = 0;
        int failed = 0;
        for (BizProject project : projects) {
            try {
                fundPlanService.generateRollingForecast(project.getId(), forecastMonths);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("项目滚动预测刷新失败, projectId={}", project.getId(), e);
            }
        }

        // 部分失败必须让 TenantTaskRunner 计入失败：否则任务层报「成功 N/N」掩盖实质空转
        // （2026-09-24 线上实证：数据权限异常导致成功 0/2，但汇总日志只是 INFO）。
        // 已成功写入的快照不受影响（每次 generateRollingForecast 独立事务）。
        if (companyFailed || failed > 0) {
            throw new IllegalStateException(String.format(
                    "滚动资金预测部分失败：公司级=%s，项目失败 %d/%d",
                    companyFailed ? "失败" : "成功", failed, success + failed));
        }
        log.info("滚动资金预测刷新完成, 项目成功{}个", success);
    }
}
