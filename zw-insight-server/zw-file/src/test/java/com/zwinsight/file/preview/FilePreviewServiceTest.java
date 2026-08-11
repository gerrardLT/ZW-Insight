package com.zwinsight.file.preview;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.domain.FileInfo;
import com.zwinsight.file.mapper.FileInfoMapper;
import com.zwinsight.file.service.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 文件预览服务单元测试（图片直连 presigned URL / 非图片走 KKFileView）
 */
@ExtendWith(MockitoExtension.class)
class FilePreviewServiceTest {

    @Mock private MinioService minioService;
    @Mock private FileInfoMapper fileInfoMapper;

    private FilePreviewConfig previewConfig;
    private FilePreviewService previewService;

    @BeforeEach
    void setUp() {
        previewConfig = new FilePreviewConfig();
        previewConfig.setBaseUrl("http://kkfileview:8012");
        previewService = new FilePreviewService(minioService, fileInfoMapper, previewConfig);
    }

    private FileInfo file(String originalName) {
        FileInfo info = new FileInfo();
        info.setId(1L);
        info.setOriginalName(originalName);
        info.setFilePath("uploads/" + originalName);
        return info;
    }

    @Test
    @DisplayName("文件不存在：抛异常")
    void testGetPreviewUrl_fileNotFound() {
        when(fileInfoMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> previewService.getPreviewUrl(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    @DisplayName("图片文件：直接返回 MinIO presigned URL")
    void testGetPreviewUrl_imageReturnsPresigned() {
        when(fileInfoMapper.selectById(1L)).thenReturn(file("photo.PNG"));
        when(minioService.getPresignedUrl(anyString(), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn("http://minio/presigned/photo");

        String url = previewService.getPreviewUrl(1L);

        assertThat(url).isEqualTo("http://minio/presigned/photo");
    }

    @Test
    @DisplayName("非图片文件：返回 KKFileView URL（presigned URL base64 编码）")
    void testGetPreviewUrl_nonImageReturnsKkfileview() {
        when(fileInfoMapper.selectById(1L)).thenReturn(file("report.pdf"));
        when(minioService.getPresignedUrl(anyString(), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn("http://minio/presigned/report");

        String url = previewService.getPreviewUrl(1L);

        String expectedBase64 = Base64.getEncoder().encodeToString("http://minio/presigned/report".getBytes());
        assertThat(url).isEqualTo("http://kkfileview:8012/onlinePreview?url=" + expectedBase64);
    }

    @Test
    @DisplayName("无扩展名文件：按非图片走 KKFileView")
    void testGetPreviewUrl_noExtension() {
        when(fileInfoMapper.selectById(1L)).thenReturn(file("noext"));
        when(minioService.getPresignedUrl(anyString(), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn("http://minio/presigned/noext");

        String url = previewService.getPreviewUrl(1L);

        assertThat(url).startsWith("http://kkfileview:8012/onlinePreview?url=");
    }
}
