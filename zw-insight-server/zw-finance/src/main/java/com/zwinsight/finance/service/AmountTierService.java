package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.SysAmountTierConfig;
import com.zwinsight.finance.mapper.SysAmountTierConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 金额分级审批配置服务
 * <p>档位匹配规则：金额 ∈ [minAmount, maxAmount) 且 enabled=1；
 * 未匹配到任何档位时返回 null（模块未启用分级，走默认审批链，不静默造默认档）。
 * 匹配结果（tierLevel）由 PaymentApplyService.submit 作为流程变量 approvalTier
 * 传入 Flowable，由 BPMN 条件网关路由。</p>
 */
@Service
@RequiredArgsConstructor
public class AmountTierService {

    private final SysAmountTierConfigMapper tierConfigMapper;

    /**
     * 查询模块的档位配置列表（按等级升序）
     */
    public List<SysAmountTierConfig> listByModule(String module) {
        return tierConfigMapper.selectList(new LambdaQueryWrapper<SysAmountTierConfig>()
                .eq(SysAmountTierConfig::getModule, module)
                .orderByAsc(SysAmountTierConfig::getTierLevel));
    }

    /**
     * 新增档位（等级在模块内唯一；区间下限必须小于上限）
     */
    public void save(SysAmountTierConfig config) {
        validate(config);
        Long count = tierConfigMapper.selectCount(new LambdaQueryWrapper<SysAmountTierConfig>()
                .eq(SysAmountTierConfig::getModule, config.getModule())
                .eq(SysAmountTierConfig::getTierLevel, config.getTierLevel()));
        if (count > 0) {
            throw new BusinessException(400, "模块[" + config.getModule() + "]已存在等级 " + config.getTierLevel() + " 的档位");
        }
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }
        tierConfigMapper.insert(config);
    }

    /**
     * 修改档位（等级与模块不允许改，避免破坏流程变量语义；maxAmount 传 null 即"无上限"）
     */
    public void update(SysAmountTierConfig config) {
        SysAmountTierConfig existing = tierConfigMapper.selectById(config.getId());
        if (existing == null) {
            throw new BusinessException(404, "档位配置不存在");
        }
        existing.setTierName(config.getTierName());
        existing.setMinAmount(config.getMinAmount());
        existing.setMaxAmount(config.getMaxAmount());
        existing.setEnabled(config.getEnabled() != null ? config.getEnabled() : existing.getEnabled());
        validate(existing);
        tierConfigMapper.updateById(existing);
    }

    /**
     * 匹配档位：金额 ∈ [minAmount, maxAmount) 且启用
     *
     * @return 匹配的档位；模块无启用配置时返回 null（走默认审批链）
     */
    public SysAmountTierConfig matchTier(String module, BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        List<SysAmountTierConfig> tiers = listByModule(module).stream()
                .filter(t -> t.getEnabled() != null && t.getEnabled() == 1)
                .toList();
        for (SysAmountTierConfig tier : tiers) {
            boolean geMin = amount.compareTo(tier.getMinAmount()) >= 0;
            boolean ltMax = tier.getMaxAmount() == null || amount.compareTo(tier.getMaxAmount()) < 0;
            if (geMin && ltMax) {
                return tier;
            }
        }
        return null;
    }

    private void validate(SysAmountTierConfig config) {
        if (config.getModule() == null || config.getModule().isBlank()) {
            throw new BusinessException(400, "业务模块不能为空");
        }
        if (config.getTierName() == null || config.getTierName().isBlank()) {
            throw new BusinessException(400, "档位名称不能为空");
        }
        if (config.getTierLevel() == null || config.getTierLevel() < 1 || config.getTierLevel() > 9) {
            throw new BusinessException(400, "档位等级不合法，需在1-9之间");
        }
        if (config.getMinAmount() == null || config.getMinAmount().signum() < 0) {
            throw new BusinessException(400, "金额下限不合法，需≥0");
        }
        if (config.getMaxAmount() != null && config.getMaxAmount().compareTo(config.getMinAmount()) <= 0) {
            throw new BusinessException(400, "金额上限必须大于下限");
        }
    }
}
