package com.zwinsight.file.controller;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.file.template.PrintRenderRequest;
import com.zwinsight.file.template.PrintTemplateService;
import com.zwinsight.file.template.SysTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * 打印模板管理接口
 *
 * <p>提供打印模板的 CRUD、分页查询、详情，以及基于 Thymeleaf 的 HTML 渲染与
 * wkhtmltopdf PDF 导出。CRUD/渲染接口返回统一响应体 {@link R}，PDF 导出返回原始字节流。</p>
 *
 * <p>渲染契约：{@code /render} 与 {@code /export-pdf} 接收 {@link PrintRenderRequest}
 * （{@code templateId} + 真实业务变量 {@code variables}），不伪造业务数据。</p>
 */
@RestController
@RequestMapping("/api/v1/print-template")
@RequiredArgsConstructor
public class PrintTemplateController {

    private final PrintTemplateService printTemplateService;

    /**
     * 创建打印模板（含名称 + 业务类型唯一性校验）
     */
    @PostMapping
    public R<SysTemplate> create(@RequestBody SysTemplate template) {
        return R.ok(printTemplateService.create(template));
    }

    /**
     * 更新打印模板
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysTemplate template) {
        printTemplateService.update(id, template);
        return R.ok();
    }

    /**
     * 逻辑删除打印模板
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        printTemplateService.delete(id);
        return R.ok();
    }

    /**
     * 分页查询打印模板列表，可按 moduleCode / businessType / templateType 筛选
     */
    @GetMapping("/list")
    public R<PageResult<SysTemplate>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String templateType) {
        return R.ok(printTemplateService.list(page, size, moduleCode, businessType, templateType));
    }

    /**
     * 查询模板详情（含模板内容）
     */
    @GetMapping("/{id}")
    public R<SysTemplate> detail(@PathVariable Long id) {
        return R.ok(printTemplateService.getById(id));
    }

    /**
     * 渲染模板为 HTML（templateId + variables）
     */
    @PostMapping("/render")
    public R<String> render(@RequestBody PrintRenderRequest request) {
        if (request.getTemplateId() == null) {
            throw new BusinessException(400, "模板ID不能为空");
        }
        return R.ok(printTemplateService.render(request.getTemplateId(), request.getVariables()));
    }

    /**
     * 渲染模板并导出 PDF（templateId + variables），返回可下载的 PDF 字节流
     */
    @PostMapping("/export-pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody PrintRenderRequest request) {
        if (request.getTemplateId() == null) {
            throw new BusinessException(400, "模板ID不能为空");
        }
        byte[] pdfBytes = printTemplateService.exportPdf(request.getTemplateId(), request.getVariables());
        String fileName = URLEncoder.encode("print-" + request.getTemplateId() + ".pdf", StandardCharsets.UTF_8)
                .replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(pdfBytes.length);
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}
