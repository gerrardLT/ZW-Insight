package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.PayrollExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 工资单导入监听器
 * <p>
 * 工资单金额由周期内已审批用工单自动汇总，导入仅创建单据头。
 * 班组按名称解析为 teamId 回填；同班组周期重叠校验交由业务保存逻辑。
 * </p>
 */
@Slf4j
public class PayrollImportListener extends AbstractImportListener<PayrollExcelDTO> {

    /**
     * 周期重叠检查器：teamId + 起止日期 → 是否重叠
     */
    @FunctionalInterface
    public interface PeriodOverlapChecker {
        boolean isOverlap(Long teamId, LocalDate periodStart, LocalDate periodEnd);
    }

    private final Function<String, Long> teamIdResolver;
    private final PeriodOverlapChecker periodOverlapChecker;
    private final Consumer<List<PayrollExcelDTO>> batchSaveAction;

    /** 当前导入批次内已出现的 班组+周期 组合（防同一文件内重复行） */
    private final java.util.Set<String> seenPeriodKeys = new java.util.HashSet<>();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * @param teamIdResolver     班组名称 → teamId（不存在返回 null）
     * @param periodOverlapChecker 同班组周期重叠检查
     * @param batchSaveAction    批量保存动作
     */
    public PayrollImportListener(
            Function<String, Long> teamIdResolver,
            PeriodOverlapChecker periodOverlapChecker,
            Consumer<List<PayrollExcelDTO>> batchSaveAction) {
        this.teamIdResolver = teamIdResolver;
        this.periodOverlapChecker = periodOverlapChecker;
        this.batchSaveAction = batchSaveAction;
    }

    @Override
    protected String validate(PayrollExcelDTO data) {
        if (StrUtil.isBlank(data.getTeamName())) {
            return "班组名称不能为空";
        }
        Long teamId = teamIdResolver.apply(data.getTeamName().trim());
        if (teamId == null) {
            return "班组 [" + data.getTeamName() + "] 不存在";
        }
        data.setTeamId(teamId);

        if (StrUtil.isNotBlank(data.getOrderType())) {
            String ot = data.getOrderType().trim();
            if (!"固定".equals(ot) && !"临时".equals(ot) && !"FIXED".equals(ot) && !"TEMPORARY".equals(ot)) {
                return "用工类型错误，应为 固定/临时";
            }
        }
        if (StrUtil.isBlank(data.getPeriodStart())) {
            return "周期开始日期不能为空";
        }
        if (StrUtil.isBlank(data.getPeriodEnd())) {
            return "周期结束日期不能为空";
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(data.getPeriodStart().trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return "周期开始日期格式错误，应为 yyyy-MM-dd";
        }
        try {
            end = LocalDate.parse(data.getPeriodEnd().trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return "周期结束日期格式错误，应为 yyyy-MM-dd";
        }
        if (end.isBefore(start)) {
            return "周期结束日期不能早于开始日期";
        }
        String periodKey = teamId + "|" + start + "|" + end;
        if (!seenPeriodKeys.add(periodKey)) {
            return "班组 [" + data.getTeamName() + "] 在文件内存在重复周期行";
        }
        if (periodOverlapChecker.isOverlap(teamId, start, end)) {
            return "班组 [" + data.getTeamName() + "] 在该周期内已有工资单，周期重叠";
        }
        return null;
    }

    @Override
    protected void batchSave(List<PayrollExcelDTO> dataList) {
        batchSaveAction.accept(dataList);
        log.info("工资单批量导入 {} 条", dataList.size());
    }
}
