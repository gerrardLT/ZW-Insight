package com.zwinsight.labor.batch;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.file.batch.dto.PayrollExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.PayrollImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.labor.service.LaborPayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 工资单批量导入导出处理器
 * <p>
 * 导入仅创建工资单头，结算金额由 {@link LaborPayrollService#save} 按周期内
 * 已审批用工单自动汇总。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class PayrollBatchHandler implements BatchModuleHandler {

    private final BizTeamMapper teamMapper;
    private final BizLaborPayrollMapper payrollMapper;
    private final LaborPayrollService payrollService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.PAYROLL == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return PayrollExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return new PayrollImportListener(
                // 班组名称 → teamId
                teamName -> {
                    BizTeam team = findTeam(teamName);
                    return team != null ? team.getId() : null;
                },
                // 同班组周期重叠检查（与 LaborPayrollService.save 同一判定口径）
                (teamId, periodStart, periodEnd) -> {
                    Long count = payrollMapper.selectCount(
                            new LambdaQueryWrapper<BizLaborPayroll>()
                                    .eq(BizLaborPayroll::getTeamId, teamId)
                                    .le(BizLaborPayroll::getPeriodStart, periodEnd)
                                    .ge(BizLaborPayroll::getPeriodEnd, periodStart)
                    );
                    return count != null && count > 0;
                },
                // 批量保存（走 LaborPayrollService.save：周期查重 + 工单汇总）
                dataList -> {
                    for (PayrollExcelDTO dto : dataList) {
                        BizTeam team = teamMapper.selectById(dto.getTeamId());
                        if (team == null) {
                            throw new BusinessException("班组 [" + dto.getTeamName() + "] 不存在");
                        }
                        BizLaborPayroll entity = new BizLaborPayroll();
                        entity.setTeamId(team.getId());
                        entity.setProjectId(projectId != null ? projectId : team.getProjectId());
                        entity.setOrderType(mapOrderType(dto.getOrderType()));
                        entity.setPeriodStart(LocalDate.parse(dto.getPeriodStart().trim(), DATE_FORMATTER));
                        entity.setPeriodEnd(LocalDate.parse(dto.getPeriodEnd().trim(), DATE_FORMATTER));
                        payrollService.save(entity);
                    }
                }
        );
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        LambdaQueryWrapper<BizLaborPayroll> wrapper = new LambdaQueryWrapper<>();
        if (params != null && params.get("projectId") != null) {
            wrapper.eq(BizLaborPayroll::getProjectId, Long.valueOf(params.get("projectId").toString()));
        }
        List<BizLaborPayroll> list = payrollMapper.selectList(wrapper);
        Map<Long, String> teamNames = teamMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(BizTeam::getId, BizTeam::getTeamName, (a, b) -> a));
        return list.stream().map(entity -> {
            PayrollExcelDTO dto = new PayrollExcelDTO();
            dto.setTeamName(teamNames.getOrDefault(entity.getTeamId(), ""));
            dto.setOrderType("FIXED".equals(entity.getOrderType()) ? "固定" : "临时".equals(entity.getOrderType()) ? "临时" : entity.getOrderType());
            dto.setPeriodStart(entity.getPeriodStart() != null ? entity.getPeriodStart().toString() : "");
            dto.setPeriodEnd(entity.getPeriodEnd() != null ? entity.getPeriodEnd().toString() : "");
            return dto;
        }).toList();
    }

    private BizTeam findTeam(String teamName) {
        return teamMapper.selectOne(
                new LambdaQueryWrapper<BizTeam>()
                        .eq(BizTeam::getTeamName, teamName)
                        .last("LIMIT 1")
        );
    }

    private String mapOrderType(String input) {
        if (StrUtil.isBlank(input)) {
            return "FIXED";
        }
        String ot = input.trim();
        return switch (ot) {
            case "固定", "FIXED" -> "FIXED";
            case "临时", "TEMPORARY" -> "TEMPORARY";
            default -> "FIXED";
        };
    }
}
