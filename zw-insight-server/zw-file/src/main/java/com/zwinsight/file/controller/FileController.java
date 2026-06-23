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
}
