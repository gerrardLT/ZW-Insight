package com.zwinsight.file.preview;

import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件预览接口
 * <p>权限豁免说明：文件预览为全模块公共能力（同 FileController 策略），
 * 业务归属由宿主业务接口守卫，不加 {@code @RequiresPermission}。</p>
 */
@RestController
@RequestMapping("/api/v1/file")
@RequiredArgsConstructor
public class FilePreviewController {

    private final FilePreviewService previewService;

    /**
     * 获取文件预览 URL
     * - 图片文件（jpg/png/gif 等）直接返回 MinIO presigned URL
     * - 其他文件返回 KKFileView 在线预览 URL
     *
     * @param fileId 文件ID
     * @return 预览 URL
     */
    @GetMapping("/preview-url")
    public R<String> getPreviewUrl(@RequestParam Long fileId) {
        return R.ok(previewService.getPreviewUrl(fileId));
    }
}
