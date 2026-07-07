package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizContractTemplate;
import com.zwinsight.contract.mapper.BizContractTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合同模板管理服务
 * <p>
 * 支持模板的 CRUD、变量占位符替换、从模板快速创建合同。
 * </p>
 * <p>
 * 变量占位符格式：${变量名}
 * 内置变量：
 * <ul>
 *   <li>${projectName} - 项目名称</li>
 *   <li>${partyAName} - 甲方名称</li>
 *   <li>${partyBName} - 乙方名称</li>
 *   <li>${contractAmount} - 合同金额</li>
 *   <li>${signingDate} - 签订日期</li>
 *   <li>${startDate} - 开工日期</li>
 *   <li>${endDate} - 竣工日期</li>
 *   <li>${taxRate} - 税率</li>
 *   <li>${paymentTerms} - 付款条件</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractTemplateService {

    private final BizContractTemplateMapper templateMapper;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 分页查询模板
     */
    public PageResult<BizContractTemplate> page(int page, int size, String contractCategory) {
        Page<BizContractTemplate> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizContractTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(contractCategory != null && !contractCategory.isBlank(),
                        BizContractTemplate::getContractCategory, contractCategory)
                .eq(BizContractTemplate::getStatus, 1)
                .orderByDesc(BizContractTemplate::getUsageCount);
        return PageResult.of(templateMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 根据ID查询模板
     */
    public BizContractTemplate getById(Long id) {
        BizContractTemplate template = templateMapper.selectById(id);
        if (template == null) throw new BusinessException("模板不存在");
        return template;
    }

    /**
     * 新增模板
     */
    public void save(BizContractTemplate template) {
        // 校验模板编码唯一性
        Long count = templateMapper.selectCount(
                new LambdaQueryWrapper<BizContractTemplate>()
                        .eq(BizContractTemplate::getTemplateCode, template.getTemplateCode()));
        if (count > 0) {
            throw new BusinessException("模板编码已存在");
        }
        template.setUsageCount(0);
        template.setStatus(1);
        templateMapper.insert(template);
    }

    /**
     * 更新模板
     */
    public void update(BizContractTemplate template) {
        BizContractTemplate existing = templateMapper.selectById(template.getId());
        if (existing == null) throw new BusinessException("模板不存在");
        templateMapper.updateById(template);
    }

    /**
     * 删除模板（逻辑删除）
     */
    public void delete(Long id) {
        BizContractTemplate existing = templateMapper.selectById(id);
        if (existing == null) throw new BusinessException("模板不存在");
        templateMapper.deleteById(id);
    }

    /**
     * 从模板生成合同内容（变量替换）
     * <p>
     * 将模板中的 ${变量名} 占位符替换为实际值。
     * </p>
     *
     * @param templateId 模板ID
     * @param variables  变量键值对 {projectName: "xxx", partyAName: "xxx", ...}
     * @return 替换后的合同内容
     */
    public String renderTemplate(Long templateId, Map<String, String> variables) {
        BizContractTemplate template = getById(templateId);
        String content = template.getTemplateContent();

        if (content == null || content.isBlank()) {
            throw new BusinessException("模板内容为空");
        }

        // 替换变量
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = variables.getOrDefault(varName, "${" + varName + "}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        // 增加使用次数
        template.setUsageCount(template.getUsageCount() + 1);
        templateMapper.updateById(template);

        return result.toString();
    }

    /**
     * 预览模板效果（用示例数据替换变量）
     */
    public String previewTemplate(Long templateId) {
        Map<String, String> sampleData = new HashMap<>();
        sampleData.put("projectName", "XX市政道路工程");
        sampleData.put("partyAName", "XX建设投资有限公司");
        sampleData.put("partyBName", "中维建设集团有限公司");
        sampleData.put("contractAmount", "5,000,000.00");
        sampleData.put("signingDate", "2026-07-01");
        sampleData.put("startDate", "2026-08-01");
        sampleData.put("endDate", "2027-06-30");
        sampleData.put("taxRate", "9%");
        sampleData.put("paymentTerms", "月进度款支付80%，竣工验收后30天内支付至95%");
        return renderTemplate(templateId, sampleData);
    }
}
