package com.zwinsight.file.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.file.domain.FileInfo;
import com.zwinsight.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理接口
 */
// 权限豁免：文件上传/下载为全模块公共能力，业务归属由宿主接口守卫（权限守卫修复 2026-08-21，不加 @RequiresPermission）
@RestController
@RequestMapping("/api/v1/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public R<FileInfo> upload(@RequestParam("file") MultipartFile file,
                              @RequestParam(required = false) String businessType,
                              @RequestParam(required = false) Long businessId,
                              @RequestParam(required = false) Long projectId) {
        return R.ok(fileService.upload(file, businessType, businessId, projectId));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return R.ok();
    }

    @GetMapping("/list")
    public R<List<FileInfo>> list(@RequestParam String businessType,
                                  @RequestParam Long businessId) {
        return R.ok(fileService.getByBusiness(businessType, businessId));
    }

    /**
     * 批量回填绑定业务主体（解决表单新建阶段先传文件再生成业务ID的时序断裂）
     */
    @PutMapping("/bind-business")
    public R<Integer> bindBusiness(@RequestBody BindBusinessRequest request) {
        return R.ok(fileService.bindBusiness(
                request.getFileIds(), request.getBusinessType(), request.getBusinessId(), request.getProjectId()));
    }

    /**
     * 流式打包下载指定项目的全卷归档附件（ZIP 格式）
     */
    @GetMapping("/package/project/{projectId}")
    public void packageProjectFiles(@PathVariable Long projectId, jakarta.servlet.http.HttpServletResponse response) {
        fileService.packageProjectFiles(projectId, response);
    }

    @lombok.Data
    public static class BindBusinessRequest {
        private List<Long> fileIds;
        private String businessType;
        private Long businessId;
        private Long projectId;
    }
}
