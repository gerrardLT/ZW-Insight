package com.zwinsight.site.sign;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 签到管理接口
 */
@RestController
@RequestMapping("/api/v1/site/sign")
@RequiredArgsConstructor
public class SignController {

    private final LocationSignService signService;

    /**
     * 签到
     * 传入项目ID、经纬度、地址，自动计算是否在范围内
     */
    @PostMapping
    public R<BizSignRecord> sign(@RequestBody SignRequestDTO request) {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(signService.sign(userId, request));
    }

    /**
     * 查询签到记录（按项目和月份）
     *
     * @param projectId 项目ID
     * @param month     月份（yyyy-MM 格式，如 2025-03）
     */
    @GetMapping("/records")
    public R<List<BizSignRecord>> records(
            @RequestParam Long projectId,
            @RequestParam String month) {
        return R.ok(signService.getRecords(projectId, month));
    }

    /**
     * 月度签到日历（某用户某项目某月）
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @param month     月份（yyyy-MM 格式）
     */
    @GetMapping("/monthly")
    public R<MonthlySignVO> monthly(
            @RequestParam Long projectId,
            @RequestParam Long userId,
            @RequestParam String month) {
        return R.ok(signService.getMonthlyCalendar(projectId, userId, month));
    }

    /**
     * 项目全员签到统计
     *
     * @param projectId 项目ID
     * @param month     月份（yyyy-MM 格式）
     */
    @GetMapping("/statistics")
    public R<SignStatisticsVO> statistics(
            @RequestParam Long projectId,
            @RequestParam String month) {
        return R.ok(signService.getStatistics(projectId, month));
    }

    /**
     * 配置签到范围（项目中心坐标 + 半径）
     *
     * @param projectId 项目ID
     * @param config    签到范围配置
     */
    @PutMapping("/config")
    public R<Void> updateConfig(
            @RequestParam Long projectId,
            @RequestBody SignConfigDTO config) {
        signService.updateSignConfig(projectId, config);
        return R.ok();
    }

    /**
     * 获取项目签到范围配置
     *
     * @param projectId 项目ID
     */
    @GetMapping("/config")
    public R<SignConfigDTO> getConfig(@RequestParam Long projectId) {
        return R.ok(signService.getSignConfig(projectId));
    }

}
