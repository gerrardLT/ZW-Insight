package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizTaxRate;
import com.zwinsight.finance.domain.dto.TaxRateDTO;
import com.zwinsight.finance.mapper.BizTaxRateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 税率字典管理服务
 */
@Service
@RequiredArgsConstructor
public class TaxRateService {

    private final BizTaxRateMapper taxRateMapper;

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    /**
     * 新增税率
     *
     * @param name      税率名称
     * @param rateValue 税率数值
     * @return 完整税率记录
     */
    public TaxRateDTO create(String name, BigDecimal rateValue) {
        validateName(name);
        validateRateValue(rateValue);
        checkNameUniqueness(name, null);

        BizTaxRate taxRate = new BizTaxRate();
        taxRate.setName(name);
        taxRate.setRateValue(rateValue);
        taxRate.setStatus(STATUS_ENABLED);
        taxRateMapper.insert(taxRate);

        return toDTO(taxRate);
    }

    /**
     * 修改税率
     *
     * @param id        税率ID
     * @param name      新名称
     * @param rateValue 新数值
     * @return 更新后的税率记录
     */
    public TaxRateDTO update(Long id, String name, BigDecimal rateValue) {
        BizTaxRate existing = taxRateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("税率记录不存在");
        }

        validateName(name);
        validateRateValue(rateValue);
        checkNameUniqueness(name, id);

        existing.setName(name);
        existing.setRateValue(rateValue);
        taxRateMapper.updateById(existing);

        return toDTO(existing);
    }

    /**
     * 停用税率（逻辑删除，状态变为 DISABLED）
     *
     * @param id 税率ID
     */
    public void delete(Long id) {
        BizTaxRate existing = taxRateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("税率记录不存在");
        }

        existing.setStatus(STATUS_DISABLED);
        taxRateMapper.updateById(existing);
    }

    /**
     * 查询所有启用状态的税率列表，按创建时间升序
     *
     * @return 启用税率列表
     */
    public List<TaxRateDTO> listEnabled() {
        LambdaQueryWrapper<BizTaxRate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTaxRate::getStatus, STATUS_ENABLED)
                .orderByAsc(BizTaxRate::getCreatedAt);
        List<BizTaxRate> list = taxRateMapper.selectList(wrapper);
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 查询全部税率列表（含停用），按创建时间升序
     *
     * @return 全部税率列表
     */
    public List<TaxRateDTO> listAll() {
        LambdaQueryWrapper<BizTaxRate> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BizTaxRate::getCreatedAt);
        List<BizTaxRate> list = taxRateMapper.selectList(wrapper);
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /**
     * 校验税率名称：非空且长度 1-30 字符
     */
    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 30) {
            throw new BusinessException(400, "税率名称不合法，需1-30个字符");
        }
    }

    /**
     * 校验税率数值：0.01 ≤ value ≤ 99.99，且不超过2位小数
     */
    private void validateRateValue(BigDecimal rateValue) {
        if (rateValue == null) {
            throw new BusinessException(400, "税率数值不合法，需在0.01-99.99之间且不超过2位小数");
        }

        BigDecimal min = new BigDecimal("0.01");
        BigDecimal max = new BigDecimal("99.99");

        if (rateValue.compareTo(min) < 0 || rateValue.compareTo(max) > 0) {
            throw new BusinessException(400, "税率数值不合法，需在0.01-99.99之间且不超过2位小数");
        }

        // 检查小数位数不超过2位：stripTrailingZeros后scale应≤2
        if (rateValue.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(400, "税率数值不合法，需在0.01-99.99之间且不超过2位小数");
        }
    }

    /**
     * 检查名称唯一性（含停用状态记录）
     * 注意：tenant_id 条件由 TenantLineInnerInterceptor 自动注入，无需手动添加
     *
     * @param name      税率名称
     * @param excludeId 排除的记录ID（更新时排除自身，新增时为 null）
     */
    private void checkNameUniqueness(String name, Long excludeId) {
        LambdaQueryWrapper<BizTaxRate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTaxRate::getName, name);
        if (excludeId != null) {
            wrapper.ne(BizTaxRate::getId, excludeId);
        }
        // 查询所有状态（ENABLED + DISABLED）的记录
        Long count = taxRateMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(400, "税率名称[" + name + "]已存在");
        }
    }

    /**
     * 实体转 DTO
     */
    private TaxRateDTO toDTO(BizTaxRate entity) {
        TaxRateDTO dto = new TaxRateDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setRateValue(entity.getRateValue());
        dto.setStatus(entity.getStatus());
        dto.setCreateTime(entity.getCreatedAt());
        return dto;
    }
}
