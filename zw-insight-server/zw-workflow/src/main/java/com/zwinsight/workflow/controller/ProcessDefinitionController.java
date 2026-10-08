package com.zwinsight.workflow.controller;

import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.workflow.domain.WfProcessDef;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.workflow.service.ProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 流程定义管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflow/process")
@RequiredArgsConstructor
@RequiresPermission("workflow:view")
public class ProcessDefinitionController {

    private final ProcessDefinitionService processDefinitionService;

    /**
     * 部署流程（上传BPMN文件）
     */
    @PostMapping("/deploy")
    public R<WfProcessDef> deploy(@RequestParam("file") MultipartFile file,
                                  @RequestParam("name") String name) throws Exception {
        Long tenantId = SecurityContextHolder.getTenantId();
        byte[] bpmnBytes = file.getBytes();
        WfProcessDef processDef = processDefinitionService.deploy(name, tenantId, bpmnBytes);
        return R.ok(processDef);
    }

    /**
     * 列出当前租户的流程定义
     */
    @GetMapping
    public R<List<WfProcessDef>> list() {
        Long tenantId = SecurityContextHolder.getTenantId();
        return R.ok(processDefinitionService.listByTenant(tenantId));
    }

    /**
     * 获取流程图
     */
    @GetMapping("/{id}/image")
    public void getProcessImage(@PathVariable String id, HttpServletResponse response) {
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        try (InputStream inputStream = processDefinitionService.getProcessImage(id);
             OutputStream outputStream = response.getOutputStream()) {
            if (inputStream != null) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        } catch (Exception e) {
            log.warn("获取流程图异常兜底输出, id={}: {}", id, e.getMessage());
            try (OutputStream os = response.getOutputStream()) {
                byte[] emptyPng = new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89
                };
                os.write(emptyPng);
                os.flush();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 获取历史版本列表
     */
    @GetMapping("/{processKey}/versions")
    public R<List<WfProcessDef>> getHistoryVersions(@PathVariable String processKey) {
        Long tenantId = SecurityContextHolder.getTenantId();
        return R.ok(processDefinitionService.getHistoryVersions(processKey, tenantId));
    }

    /**
     * 获取流程定义 XML 内容
     */
    @GetMapping("/{id}/xml")
    public R<String> getProcessXml(@PathVariable String id) {
        return R.ok(processDefinitionService.getProcessXml(id));
    }
}
