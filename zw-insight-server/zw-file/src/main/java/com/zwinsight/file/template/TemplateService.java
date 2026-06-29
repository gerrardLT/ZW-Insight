package com.zwinsight.file.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 模板管理服务
 */
@Slf4j
@Service("fileTemplateService")
@RequiredArgsConstructor
public class TemplateService {

    private final SysTemplateMapper templateMapper;

    /**
     * 按模块编码和模板类型查询模板列表
     *
     * @param moduleCode   模块编码
     * @param templateType 模板类型（IMPORT/EXPORT/PRINT）
     * @return 模板列表
     */
    public List<SysTemplate> listByModule(String moduleCode, String templateType) {
        LambdaQueryWrapper<SysTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(moduleCode != null, SysTemplate::getModuleCode, moduleCode)
                .eq(templateType != null, SysTemplate::getTemplateType, templateType)
                .orderByDesc(SysTemplate::getIsDefault)
                .orderByDesc(SysTemplate::getCreatedAt);
        return templateMapper.selectList(wrapper);
    }

    /**
     * 获取默认模板
     *
     * @param moduleCode   模块编码
     * @param templateType 模板类型
     * @return 默认模板（无则返回 null）
     */
    public SysTemplate getDefault(String moduleCode, String templateType) {
        LambdaQueryWrapper<SysTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTemplate::getModuleCode, moduleCode)
                .eq(SysTemplate::getTemplateType, templateType)
                .eq(SysTemplate::getIsDefault, 1)
                .last("LIMIT 1");
        return templateMapper.selectOne(wrapper);
    }

    /**
     * 创建模板
     */
    public SysTemplate create(SysTemplate template) {
        // 如果设为默认模板，需先取消同模块同类型的其他默认
        if (Integer.valueOf(1).equals(template.getIsDefault())) {
            clearDefault(template.getModuleCode(), template.getTemplateType());
        }
        templateMapper.insert(template);
        return template;
    }

    /**
     * 更新模板
     */
    public void update(Long id, SysTemplate template) {
        SysTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("模板不存在");
        }
        template.setId(id);
        // 如果设为默认模板，需先取消同模块同类型的其他默认
        if (Integer.valueOf(1).equals(template.getIsDefault())) {
            clearDefault(existing.getModuleCode(), existing.getTemplateType());
        }
        templateMapper.updateById(template);
    }

    /**
     * 删除模板
     */
    public void delete(Long id) {
        SysTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("模板不存在");
        }
        templateMapper.deleteById(id);
    }

    /**
     * 渲染打印模板（变量替换）
     * 模板中使用 {{fieldName}} 占位符，传入 variables 进行替换
     *
     * @param templateId 模板ID
     * @param variables  变量数据（key=占位符名, value=实际值）
     * @return 渲染后的 HTML 字符串
     */
    public String renderTemplate(Long templateId, Map<String, Object> variables) {
        SysTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        if (template.getTemplateContent() == null || template.getTemplateContent().isEmpty()) {
            throw new BusinessException("模板内容为空，无法渲染");
        }

        String html = template.getTemplateContent();
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
                html = html.replace(placeholder, value);
            }
        }
        return html;
    }

    /**
     * 清除同模块同类型的默认标记
     */
    private void clearDefault(String moduleCode, String templateType) {
        LambdaQueryWrapper<SysTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTemplate::getModuleCode, moduleCode)
                .eq(SysTemplate::getTemplateType, templateType)
                .eq(SysTemplate::getIsDefault, 1);
        List<SysTemplate> defaults = templateMapper.selectList(wrapper);
        for (SysTemplate t : defaults) {
            t.setIsDefault(0);
            templateMapper.updateById(t);
        }
    }
}
