package com.zwinsight.file.service;

import com.zwinsight.file.config.MinioConfig;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MinioService 单元测试
 * 
 * 覆盖场景:
 * - 文件上传 (MultipartFile/InputStream)
 * - 文件下载
 * - 获取预签名 URL
 * - 文件删除
 * - 存储桶自动创建
 */
@ExtendWith(MockitoExtension.class)
class MinioServiceDownloadTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    private MinioService minioService;

    @BeforeEach
    void setUp() {
        minioService = new MinioService(minioClient, minioConfig);
        
        // Mock bucket name
        when(minioConfig.getBucket()).thenReturn("test-bucket");
    }

    // ==================== 文件下载测试 ====================

    @Nested
    @DisplayName("文件下载场景")
    class DownloadTests {

        @Test
        @DisplayName("正常下载文件返回输入流")
        void download_normalFlow_returnsStream() throws Exception {
            // Given
            String objectName = "project/docs/test.pdf";
            byte[] fileContent = "PDF content".getBytes();

            // getObject 返回类型为 GetObjectResponse，需构造真实实例（Mockito 运行时校验返回类型兼容）
            GetObjectResponse response = new GetObjectResponse(
                    new okhttp3.Headers.Builder().build(), "test-bucket", null, objectName,
                    new ByteArrayInputStream(fileContent));
            doReturn(response).when(minioClient).getObject(any(GetObjectArgs.class));

            // When
            InputStream result = minioService.download(objectName);

            // Then
            assertNotNull(result);
            assertEquals('P', result.read()); // 首字节为 'P'(80)
            verify(minioClient).getObject(argThat(args -> 
                args.bucket().equals("test-bucket") && 
                args.object().equals(objectName)
            ));
        }

        @Test
        @DisplayName("下载不存在的文件抛出异常")
        void download_nonExistingFile_throwsException() throws Exception {
            // Given
            String nonExistentObject = "nonexistent/file.pdf";
            
            when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new IOException("Not Found"));

            // When & Then
            assertThatThrownBy(() -> minioService.download(nonExistentObject))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件下载失败");
        }
    }

    // ==================== 文件上传测试 ====================

    @Nested
    @DisplayName("文件上传场景")
    class UploadTests {

        @Test
        @DisplayName("通过 MultipartFile 上传文件")
        void upload_byMultipartFile_success() throws Exception {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "PDF 内容".getBytes()
            );

            String path = "project/docs/";

            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

            // When
            String result = minioService.upload(file, path);

            // Then
            assertNotNull(result);
            assertTrue(result.contains(".pdf"));
            // contentType() 声明抛 IOException 无法在 argThat lambda 内处理，仅校验存储桶
            verify(minioClient).putObject(argThat(args -> 
                args.bucket().equals("test-bucket")
            ));
        }

        @Test
        @DisplayName("通过 InputStream 上传文件")
        void upload_byInputStream_success() throws Exception {
            // Given
            String objectName = "project/docs/manual.pdf";
            byte[] content = "Manual content".getBytes();
            InputStream inputStream = new ByteArrayInputStream(content);

            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

            // When
            String result = minioService.upload(objectName, inputStream, content.length, "application/pdf");

            // Then
            assertNotNull(result);
            assertEquals(objectName, result);
            verify(minioClient).putObject(argThat(args -> 
                args.object().equals(objectName)
            ));
        }

        @Test
        @DisplayName("上传失败时抛出运行时异常")
        void upload_fails_throwsRuntimeException() throws Exception {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "content".getBytes()
            );

            when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new IOException("Connection failed"));

            // When & Then
            assertThatThrownBy(() -> minioService.upload(file, "docs/"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件上传失败");
        }
    }

    // ==================== 预签名 URL 测试 ====================

    @Nested
    @DisplayName("预签名 URL 场景")
    class PresignedUrlTests {

        @Test
        @DisplayName("获取默认 7 天有效的预签名 URL")
        void getPresignedUrl_defaultExpiration_success() throws Exception {
            // Given
            String objectName = "project/docs/test.pdf";
            String mockUrl = "https://minio.example.com/test-bucket/project/docs/test.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&...";

            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(mockUrl);

            // When
            String result = minioService.getPresignedUrl(objectName);

            // Then
            assertNotNull(result);
            assertEquals(mockUrl, result);
        }

        @Test
        @DisplayName("获取指定有效期的预签名 URL")
        void getPresignedUrl_customExpiration_success() throws Exception {
            // Given
            String objectName = "docs/file.docx";
            String mockUrl = "https://minio.example.com/custom-url";

            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(mockUrl);

            // When
            String result = minioService.getPresignedUrl(objectName, 1, TimeUnit.HOURS);

            // Then
            assertNotNull(result);
            assertEquals(mockUrl, result);
        }

        @Test
        @DisplayName("获取预签名 URL 失败时抛出异常")
        void getPresignedUrl_fails_throwsException() throws Exception {
            // Given
            String objectName = "docs/file.pdf";
            
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new IOException("Invalid request"));

            // When & Then
            assertThatThrownBy(() -> minioService.getPresignedUrl(objectName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("获取预签名URL失败");
        }
    }

    // ==================== 文件删除测试 ====================

    @Nested
    @DisplayName("文件删除场景")
    class DeleteTests {

        @Test
        @DisplayName("成功删除文件")
        void delete_success() throws Exception {
            // Given：removeObject 为 void 方法，默认无桩即放行
            String objectName = "project/docs/temp.pdf";

            // When
            minioService.delete(objectName);

            // Then
            verify(minioClient).removeObject(argThat(args -> 
                args.bucket().equals("test-bucket") &&
                args.object().equals(objectName)
            ));
        }

        @Test
        @DisplayName("删除文件失败时抛出异常")
        void delete_fails_throwsException() throws Exception {
            // Given
            String objectName = "docs/fail.pdf";

            doThrow(new RuntimeException("Access Denied"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

            // When & Then
            assertThatThrownBy(() -> minioService.delete(objectName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件删除失败");
        }
    }
}
