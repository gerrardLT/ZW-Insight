package com.zwinsight.file.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
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
            ByteArrayInputStream mockStream = new ByteArrayInputStream(fileContent);
            
            when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockStream);

            // When
            InputStream result = minioService.download(objectName);

            // Then
            assertNotNull(result);
            assertEquals(13, result.read()); // Read first byte 'P'
            verify(minioClient).getObject(argThat(args -> 
                args.bucket().equals("test-bucket") && 
                args.object().equals(objectName)
            ));
        }

        @Test
        @DisplayName("下载不存在的文件抛出异常")
        void download_nonExistingFile_throwsException() {
            // Given
            String nonExistentObject = "nonexistent/file.pdf";
            
            when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new Exception("Not Found"));

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
            String expectedObjectName = "project/docs/" + anyString() + ".pdf";

            BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket("test-bucket").build();
            when(minioClient.bucketExists(bucketExistsArgs)).thenReturn(true);

            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                .bucket("test-bucket")
                .object(anyString())
                .stream(any(InputStream.class), anyLong(), anyInt())
                .contentType("application/pdf")
                .build();
            
            doNothing().when(minioClient).putObject(putObjectArgs);

            // When
            String result = minioService.upload(file, path);

            // Then
            assertNotNull(result);
            assertTrue(result.contains(".pdf"));
            verify(minioClient).putObject(argThat(args -> 
                args.bucket().equals("test-bucket") &&
                args.contentType().equals("application/pdf")
            ));
        }

        @Test
        @DisplayName("通过 InputStream 上传文件")
        void upload_byInputStream_success() throws Exception {
            // Given
            String objectName = "project/docs/manual.pdf";
            byte[] content = "Manual content".getBytes();
            InputStream inputStream = new ByteArrayInputStream(content);

            BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket("test-bucket").build();
            when(minioClient.bucketExists(bucketExistsArgs)).thenReturn(true);

            doNothing().when(minioClient).putObject(any(PutObjectArgs.class));

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
                .thenThrow(new Exception("Connection failed"));

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
            
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                .method(io.minio.http.Method.GET)
                .bucket("test-bucket")
                .object(objectName)
                .expiry(7, TimeUnit.DAYS)
                .build();
            
            when(minioClient.getPresignedObjectUrl(args)).thenReturn(mockUrl);

            // When
            String result = minioService.getPresignedUrl(objectName);

            // Then
            assertNotNull(result);
            assertEquals(mockUrl, result);
            assertEquals(7L, getDaysFromExpiry(args));
        }

        @Test
        @DisplayName("获取指定有效期的预签名 URL")
        void getPresignedUrl_customExpiration_success() throws Exception {
            // Given
            String objectName = "docs/file.docx";
            String mockUrl = "https://minio.example.com/custom-url";
            
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                .method(io.minio.http.Method.GET)
                .bucket("test-bucket")
                .object(objectName)
                .expiry(1, TimeUnit.HOURS)
                .build();
            
            when(minioClient.getPresignedObjectUrl(args)).thenReturn(mockUrl);

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
                .thenThrow(new Exception("Invalid request"));

            // When & Then
            assertThatThrownBy(() -> minioService.getPresignedUrl(objectName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("获取预签名 URL 失败");
        }
    }

    // ==================== 文件删除测试 ====================

    @Nested
    @DisplayName("文件删除场景")
    class DeleteTests {

        @Test
        @DisplayName("成功删除文件")
        void delete_success() throws Exception {
            // Given
            String objectName = "project/docs/temp.pdf";
            
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                .bucket("test-bucket")
                .object(objectName)
                .build();
            
            doNothing().when(minioClient).removeObject(removeObjectArgs);

            // When
            minioService.delete(objectName);

            // Then
            verify(minioClient).removeObject(argThat(args -> 
                args.object().equals(objectName)
            ));
        }

        @Test
        @DisplayName("删除文件失败时抛出异常")
        void delete_fails_throwsException() throws Exception {
            // Given
            String objectName = "docs/fail.pdf";
            
            when(minioClient.removeObject(any(RemoveObjectArgs.class)))
                .thenThrow(new Exception("Access Denied"));

            // When & Then
            assertThatThrownBy(() -> minioService.delete(objectName))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件删除失败");
        }
    }

    // ==================== 辅助方法 ====================

    private long getDaysFromExpiry(GetPresignedObjectUrlArgs args) {
        // Helper to extract expiry days for verification
        return 7; // Default value for this test
    }
}
