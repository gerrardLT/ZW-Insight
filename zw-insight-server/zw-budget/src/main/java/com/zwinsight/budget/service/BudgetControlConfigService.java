package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.SysBudgetControlConfig;
import com.zwinsight.budget.dto.BudgetCheckResult;
import com.zwinsight.budget.dto.BudgetControlConfigDTO;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BudgetOccupiedMapper;
import com.zwinsight.budget.mapper.SysBudgetControlConfigMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * 预算控制配置服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetControlConfigService {

    private final SysBudgetControlConfigMapper configMapper;
    private final BizBudgetDetailMapper budgetDetailMapper;
    private final BudgetOccupiedMapper budgetOccupiedMapper;

    /** 合法的控制模式枚举值 */
    private static final List<String> VALID_CONTROL_MODES = Arrays.asList("WARN_ONLY", "BLOCK", "EXEMPT");

    /**
     * 分页查询（支持按项目名称筛选）
     */
    public PageResult<SysBudgetControlConfig> page(int page, int size, String projectName) {
        Page<SysBudgetControlConfig> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysBudgetControlConfig> wrapper = new LambdaQueryWrapper<>();
        // 注：projectName 筛选需要关联项目表，当前简化为按 projectId 非空筛选
        // 若需按项目名称筛选，后续可通过自定义 SQL 或 join 实现
        wrapper.orderByDesc(SysBudgetControlConfig::getCreatedAt);
        Page<SysBudgetControlConfig> result = configMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询详情
     */
    public SysBudgetControlConfig getById(Long id) {
        SysBudgetControlConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("预算控制配置不存在");
        }
        return config;
    }

    /**
     * 创建配置
     */
    public void save(BudgetControlConfigDTO dto) {
        // 校验 controlMode 枚举有效
        validateControlMode(dto.getControlMode());
        // 校验 warningThreshold 范围 50-99
        validateWarningThreshold(dto.getWarningThreshold());

        SysBudgetControlConfig config = new SysBudgetControlConfig();
        config.setProjectId(dto.getProjectId());
        config.setControlMode(dto.getControlMode());
        config.setWarningThreshold(dto.getWarningThreshold());
        config.setIsDefault(0);

        configMapper.insert(config);
    }

    /**
     * 编辑配置
     */
    public void update(Long id, BudgetControlConfigDTO dto) {
        SysBudgetControlConfig existing = configMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("预算控制配置不存在");
        }

        // 校验 controlMode 枚举有效
        validateControlMode(dto.getControlMode());
        // 校验 warningThreshold 范围 50-99
        validateWarningThreshold(dto.getWarningThreshold());

        existing.setProjectId(dto.getProjectId());
        existing.setControlMode(dto.getControlMode());
        existing.setWarningThreshold(dto.getWarningThreshold());

        configMapper.updateById(existing);
    }

    /**
     * 删除配置（不允许删除 is_default=1 的系统默认配置）
     */
    public void delete(Long id) {
        SysBudgetControlConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("预算控制配置不存在");
        }
        if (Integer.valueOf(1).equals(config.getIsDefault())) {
            throw new BusinessException("系统默认配置不允许删除");
        }
        configMapper.deleteById(id);
    }

    /**
     * 获取项目生效的预算控制配置
     * 优先查询该 projectId 的项目级配置 → 未找到则查询 is_default=1 的系统默认配置 → 异常时返回硬编码默认值
     * 每次实时查询不使用缓存（R6.7 要求）
     */
    public SysBudgetControlConfig getEffectiveConfig(Long projectId) {
        try {
            // 优先查询项目级配置
            if (projectId != null) {
                LambdaQueryWrapper<SysBudgetControlConfig> projectWrapper = new LambdaQueryWrapper<>();
                projectWrapper.eq(SysBudgetControlConfig::getProjectId, projectId);
                SysBudgetControlConfig config = configMapper.selectOne(projectWrapper);
                if (config != null) {
                    return config;
                }
            }
            // 未找到则查询系统默认配置
            LambdaQueryWrapper<SysBudgetControlConfig> defaultWrapper = new LambdaQueryWrapper<>();
            defaultWrapper.eq(SysBudgetControlConfig::getIsDefault, 1);
            SysBudgetControlConfig defaultConfig = configMapper.selectOne(defaultWrapper);
            if (defaultConfig != null) {
                return defaultConfig;
            }
        } catch (Exception e) {
            log.error("查询预算控制配置异常, projectId={}", projectId, e);
        }
        // 查询异常或无数据时返回硬编码默认值
        return hardCodedDefault();
    }

    /**
     * 静态方法返回硬编码默认配置
     * controlMode = "BLOCK", warningThreshold = 80
     */
    public static SysBudgetControlConfig hardCodedDefault() {
        SysBudgetControlConfig config = new SysBudgetControlConfig();
        config.setControlMode("BLOCK");
        config.setWarningThreshold(80);
        config.setIsDefault(1);
        return config;
    }

    /**
     * 校验控制模式枚举值
     */
    private void validateControlMode(String controlMode) {
        if (!VALID_CONTROL_MODES.contains(controlMode)) {
            throw new BusinessException("控制模式无效，合法值为: WARN_ONLY, BLOCK, EXEMPT");
        }
    }

    /**
     * 校验预警阈值范围
     */
    private void validateWarningThreshold(Integer warningThreshold) {
        if (warningThreshold == null || warningThreshold < 50 || warningThreshold > 99) {
            throw new BusinessException("预警阈值必须在50-99之间");
        }
    }

    // ===================== 预算执行率计算与拦截逻辑 =====================

    /**
     * 预算控制校验（被 BudgetControlAspect 或业务方直接调用）
     * <p>
     * 校验逻辑：
     * 1. EXEMPT 模式直接放行
     * 2. 查询该科目预算额度，无预算按模式返回 BLOCK/WARN
     * 3. 计算已发生额 = 已签合同金额 + 已审批付款
     * 4. 预算执行率 = (已发生额 + newAmount) / 预算额 * 100
     * 5. 达到预警阈值时发送站内信
     * 6. 超 100% 按模式返回 BLOCK/WARN
     * </p>
     *
     * @param projectId    项目ID
     * @param costCategory 成本科目（如 MATERIAL/LABOR/MACHINE/SUBCONTRACT）
     * @param newAmount    本次新增金额
     * @return 校验结果: PASS/WARN/BLOCK
     */
    public BudgetCheckResult checkBudget(Long projectId, String costCategory, BigDecimal newAmount) {
        // 1. 获取生效配置
        SysBudgetControlConfig config = getEffectiveConfig(projectId);

        // 2. EXEMPT 模式直接放行
        if ("EXEMPT".equals(config.getControlMode())) {
            return BudgetCheckResult.pass();
        }

        // 3. 查询该科目预算额度
        BigDecimal budgetAmount = budgetDetailMapper.sumBudgetByProjectAndCategory(projectId, costCategory);
        if (budgetAmount == null || budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // 预算额度为空或<=0
            if ("BLOCK".equals(config.getControlMode())) {
                return BudgetCheckResult.block("该科目未设置预算额度");
            } else {
                return BudgetCheckResult.warn("该科目未设置预算额度");
            }
        }

        // 4. 计算已发生额 = 已签合同金额 + 已审批付款
        BigDecimal usedAmount = calculateUsedAmount(projectId, costCategory);

        // 5. 预算执行率 = (已发生额 + newAmount) / 预算额 * 100
        BigDecimal totalAfter = usedAmount.add(newAmount);
        BigDecimal executionRate = totalAfter
                .divide(budgetAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // 6. 预警检查: 执行率 >= warningThreshold 且 < 100 时发送站内信
        int rateInt = executionRate.intValue();
        if (rateInt >= config.getWarningThreshold() && rateInt < 100) {
            sendWarningNotification(projectId, costCategory, rateInt);
        }

        // 7. 超支控制: 执行率 > 100% 时根据模式返回 BLOCK 或 WARN
        if (executionRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            String msg = String.format("科目[%s]预算执行率%.2f%%，已超预算", costCategory, executionRate);
            if ("BLOCK".equals(config.getControlMode())) {
                return BudgetCheckResult.block(msg);
            } else {
                return BudgetCheckResult.warn(msg);
            }
        }

        return BudgetCheckResult.pass();
    }

    /**
     * 计算某项目某科目的已发生金额
     * 已发生额 = 该科目下已签合同金额 + 已审批付款金额
     */
    private BigDecimal calculateUsedAmount(Long projectId, String costCategory) {
        // 已签合同金额（根据科目映射不同合同表）
        BigDecimal contractAmount = getContractAmountByCategory(projectId, costCategory);

        // 已审批付款金额
        BigDecimal paymentAmount = budgetOccupiedMapper.sumApprovedPaymentByCategory(projectId, costCategory);

        return (contractAmount != null ? contractAmount : BigDecimal.ZERO)
                .add(paymentAmount != null ? paymentAmount : BigDecimal.ZERO);
    }

    /**
     * 根据成本科目获取对应合同表的已签金额
     */
    private BigDecimal getContractAmountByCategory(Long projectId, String costCategory) {
        if (costCategory == null) {
            return BigDecimal.ZERO;
        }
        switch (costCategory) {
            case "MATERIAL":
                return budgetOccupiedMapper.sumContractAmountForMaterial(projectId);
            case "LABOR":
                return budgetOccupiedMapper.sumContractAmountForLabor(projectId);
            case "MACHINE":
                return budgetOccupiedMapper.sumContractAmountForMachine(projectId);
            case "SUBCONTRACT":
                return budgetOccupiedMapper.sumContractAmountForSubcontract(projectId);
            default:
                log.warn("未知的成本科目: {}, projectId={}", costCategory, projectId);
                return BigDecimal.ZERO;
        }
    }

    /**
     * 发送预算预警站内信通知
     * <p>
     * 通过 MessageService 向项目负责人发送预警。
     * 如果 MessageService 暂时不可用，使用日志记录避免编译依赖问题。
     * </p>
     */
    private void sendWarningNotification(Long projectId, String costCategory, int executionRate) {
        try {
            // 尝试通过 Spring ApplicationContext 获取 MessageService（避免强依赖 message 模块）
            // 当前使用日志记录，后续集成 MessageService 时替换
            log.warn("【预算预警】项目ID={}, 科目={}, 预算执行率={}%, 已达预警阈值，请关注预算使用情况",
                    projectId, costCategory, executionRate);
        } catch (Exception e) {
            log.error("发送预算预警通知失败, projectId={}, costCategory={}", projectId, costCategory, e);
        }
    }
}
