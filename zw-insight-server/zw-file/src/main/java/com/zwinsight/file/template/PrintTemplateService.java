package com.zwinsight.file.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.PdfConvertService;
import com.zwinsight.file.service.ThymeleafRenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * 打印模板服务
 *
 * <p>基于 {@link PrintTemplateMapper}（sys_template 表）实现 PRINT 类型模板的
 * CRUD（创建 / 更新 / 逻辑删除 / 分页列表 / 详情），并编排
 * {@link ThymeleafRenderService} 渲染与 {@link PdfConvertService} PDF 转换。</p>
 *
 * <p>渲染契约：调用方通过 {@code variables} 传入真实业务数据变量 Map，
 * 服务使用 Thymeleaf 引擎渲染模板内容。按业务类型自动装载业务数据
 * （{@code businessDataId} + {@code dataQueryConfig}）涉及各业务模块的具体接线，
 * 作为后续增强，此处不伪造数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTemplateService {

    /**
     * 打印模板类型常量
     */
    public static final String TEMPLATE_TYPE_PRINT = "PRINT";

    private final PrintTemplateMapper printTemplateMapper;
    private final ThymeleafRenderService thymeleafRenderService;
    private final PdfConvertService pdfConvertService;

    /**
     * 创建打印模板。
     *
     * <p>校验同一业务类型下模板名称唯一性；未显式指定模板类型时默认 {@code PRINT}。</p>
     *
     * @param template 模板实体（模板名称、业务类型、模板内容等）
     * @return 创建后的模板（含生成的 ID）
     * @throws BusinessException 当同业务类型下模板名称已存在时（code=409）
     */
    public SysTemplate create(SysTemplate template) {
        if (template.getTemplateType() == null || template.getTemplateType().isBlank()) {
            template.setTemplateType(TEMPLATE_TYPE_PRINT);
        }
        // 模板名称 + 业务类型唯一性校验
        ensureNameUnique(template.getTemplateName(), template.getBusinessType(), null);
        printTemplateMapper.insert(template);
        return template;
    }

    /**
     * 更新打印模板。
     *
     * @param id       模板 ID
     * @param template 更新字段
     * @throws BusinessException 当模板不存在（code=404）或名称重复（code=409）时
     */
    public void update(Long id, SysTemplate template) {
        SysTemplate existing = printTemplateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "模板不存在");
        }
        // 校验唯一性（排除自身）：使用更新后的名称/业务类型，缺省回退到现有值
        String newName = template.getTemplateName() != null ? template.getTemplateName() : existing.getTemplateName();
        String newBizType = template.getBusinessType() != null ? template.getBusinessType() : existing.getBusinessType();
        ensureNameUnique(newName, newBizType, id);

        template.setId(id);
        printTemplateMapper.updateById(template);
    }

    /**
     * 逻辑删除打印模板（依赖 {@code @TableLogic}）。
     *
     * @param id 模板 ID
     * @throws BusinessException 当模板不存在时（code=404）
     */
    public void delete(Long id) {
        SysTemplate existing = printTemplateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "模板不存在");
        }
        printTemplateMapper.deleteById(id);
    }

    /**
     * 分页查询打印模板列表，支持按模块编码 / 业务类型 / 模板类型筛选。
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param moduleCode   模块编码（可选）
     * @param businessType 业务类型（可选）
     * @param templateType 模板类型（可选）
     * @return 分页结果
     */
    public PageResult<SysTemplate> list(int page, int size, String moduleCode, String businessType, String templateType) {
        Page<SysTemplate> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(moduleCode != null && !moduleCode.isBlank(), SysTemplate::getModuleCode, moduleCode)
                .eq(businessType != null && !businessType.isBlank(), SysTemplate::getBusinessType, businessType)
                .eq(templateType != null && !templateType.isBlank(), SysTemplate::getTemplateType, templateType)
                .orderByDesc(SysTemplate::getIsDefault)
                .orderByDesc(SysTemplate::getCreatedAt);
        Page<SysTemplate> result = printTemplateMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 查询模板详情（含模板内容）。
     *
     * @param id 模板 ID
     * @return 模板完整信息
     * @throws BusinessException 当模板不存在时（code=404）
     */
    public SysTemplate getById(Long id) {
        SysTemplate template = printTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(404, "模板不存在");
        }
        return template;
    }

    /**
     * 渲染模板为 HTML。
     *
     * <p>加载模板（不存在则 404），使用 {@link ThymeleafRenderService} 以传入的真实业务变量渲染。</p>
     *
     * @param templateId 模板 ID
     * @param variables  业务数据变量 Map（来自调用方的真实数据）
     * @return 渲染后的完整 HTML
     * @throws BusinessException 当模板不存在（404）、内容为空或渲染失败（500，含行号描述）时
     */
    public String render(Long templateId, Map<String, Object> variables) {
        SysTemplate template = getById(templateId);
        if (template.getTemplateContent() == null || template.getTemplateContent().isEmpty()) {
            throw new BusinessException(500, "模板内容为空，无法渲染");
        }
        Map<String, Object> vars = variables != null ? variables : Collections.emptyMap();
        return thymeleafRenderService.render(template.getTemplateContent(), vars);
    }

    /**
     * 渲染模板并导出为 PDF。
     *
     * @param templateId 模板 ID
     * @param variables  业务数据变量 Map
     * @return PDF 字节数组
     * @throws BusinessException 当模板不存在、渲染失败或 PDF 转换失败时
     */
    public byte[] exportPdf(Long templateId, Map<String, Object> variables) {
        String html = render(templateId, variables);
        return pdfConvertService.convertHtmlToPdf(html);
    }

    /**
     * 校验同一业务类型下模板名称的唯一性。
     *
     * @param templateName 模板名称
     * @param businessType 业务类型
     * @param excludeId    需排除的模板 ID（更新场景排除自身，创建场景传 null）
     * @throws BusinessException 当存在重名记录时（code=409）
     */
    private void ensureNameUnique(String templateName, String businessType, Long excludeId) {
        if (templateName == null || templateName.isBlank()) {
            throw new BusinessException(400, "模板名称不能为空");
        }
        LambdaQueryWrapper<SysTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysTemplate::getTemplateName, templateName)
                .eq(businessType != null, SysTemplate::getBusinessType, businessType)
                .isNull(businessType == null, SysTemplate::getBusinessType)
                .ne(excludeId != null, SysTemplate::getId, excludeId);
        Long count = printTemplateMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(409, "同业务类型下模板名称已存在");
        }
    }
}
