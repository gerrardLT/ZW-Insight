package com.zwinsight.file.preview;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.domain.FileInfo;
import com.zwinsight.file.mapper.FileInfoMapper;
import com.zwinsight.file.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 文件预览服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilePreviewService {

    private final MinioService minioService;
    private final FileInfoMapper fileInfoMapper;
    private final FilePreviewConfig previewConfig;

    /**
     * 可直接预览的图片扩展名
     */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
    );

    /**
     * 生成文件预览 URL
     *
     * @param fileId 文件ID
     * @return 预览URL（图片返回 presigned URL，其他返回 KKFileView URL）
     */
    public String getPreviewUrl(Long fileId) {
        // 1. 从文件表获取文件信息
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new BusinessException("文件不存在");
        }

        // 2. 生成 MinIO presigned URL（30分钟有效）
        String presignedUrl = minioService.getPresignedUrl(fileInfo.getFilePath(), 30, TimeUnit.MINUTES);

        // 3. 判断是否为图片类型，图片直接返回 presigned URL
        String extension = getFileExtension(fileInfo.getOriginalName());
        if (isImage(extension)) {
            log.debug("图片文件直接预览: fileId={}, ext={}", fileId, extension);
            return presignedUrl;
        }

        // 4. 非图片文件：编码为 base64 → 拼接 KKFileView URL
        String base64Url = Base64.getEncoder().encodeToString(presignedUrl.getBytes());
        String kkfileviewUrl = previewConfig.getBaseUrl() + "/onlinePreview?url=" + base64Url;
        log.debug("KKFileView 预览: fileId={}, url={}", fileId, kkfileviewUrl);
        return kkfileviewUrl;
    }

    /**
     * 提取文件扩展名（不含点号，小写）
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 判断扩展名是否为图片
     */
    private boolean isImage(String extension) {
        return IMAGE_EXTENSIONS.contains(extension);
    }
}
