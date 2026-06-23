package com.zwinsight.workflow.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.workflow.domain.WfProcessDef;
import com.zwinsight.workflow.service.ProcessDefinitionService;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequestMapping("/api/v1/workflow/process")
@RequiredArgsConstructor
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
    public void getProcessImage(@PathVariable String id, HttpServletResponse response) throws Exception {
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        try (InputStream inputStream = processDefinitionService.getProcessImage(id);
             OutputStream outputStream = response.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
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
}
