package com.zwinsight.file.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.domain.FileInfo;
import com.zwinsight.file.mapper.FileInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileService 单元测试
 * 
 * 覆盖场景:
 * - 正常文件上传流程 (PDF/JPG/PNG)
 * - 异常路径：空文件、null 文件、无扩展名文件
 * - 文件删除操作
 * - 按业务查询文件列表
 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock
    private MinioService minioService;

    @Mock
    private FileInfoMapper fileInfoMapper;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(minioService, fileInfoMapper);
    }

    // ==================== 文件上传测试 ====================

    @Nested
    @DisplayName("文件上传场景")
    class UploadTests {

        @Test
        @DisplayName("正常上传 PDF 文件")
        void upload_PDF_normalFlow() {
            // Given
            MockMultipartFile pdfFile = new MockMultipartFile(
                "document",
                "test.pdf",
                "application/pdf",
                "PDF 文件内容".getBytes()
            );

            String expectedPath = "project/docs/" + System.currentTimeMillis() + ".pdf";

            when(minioService.upload(pdfFile, "project/docs/")).thenReturn(expectedPath);
            when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

            // When
            FileInfo result = fileService.upload(pdfFile, "PROJECT_DOCUMENT", 100L, 1L);

            // Then
            assertNotNull(result);
            assertThat(result.getOriginalName()).isEqualTo("test.pdf");
            assertThat(result.getFileName()).contains(".pdf");
            assertThat(result.getFilePath()).isEqualTo(expectedPath);
            assertThat(result.getFileSize()).isEqualTo((long) pdfFile.getContentLength());
            assertThat(result.getFileType()).isEqualTo("application/pdf");
            assertThat(result.getBusinessType()).isEqualTo("PROJECT_DOCUMENT");
            assertThat(result.getBusinessId()).isEqualTo(100L);
            assertThat(result.getProjectId()).isEqualTo(1L);

            verify(minioService).upload(pdfFile, "project/docs/");
            verify(fileInfoMapper).insert(any(FileInfo.class));
        }

        @Test
        @DisplayName("正常上传 JPG 图片文件")
        void upload_JPG_image_normalFlow() {
            // Given
            MockMultipartFile jpgFile = new MockMultipartFile(
                "image",
                "photo.jpg",
                "image/jpeg",
                "JPG 图片内容".getBytes()
            );

            String expectedPath = "project/docs/" + System.currentTimeMillis() + ".jpg";

            when(minioService.upload(jpgFile, "project/docs/")).thenReturn(expectedPath);
            when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

            // When
            FileInfo result = fileService.upload(jpgFile, "CONTRACT_IMAGE", 200L, 1L);

            // Then
            assertNotNull(result);
            assertEquals("photo.jpg", result.getOriginalName());
            assertEquals("image/jpeg", result.getFileType());
            assertEquals("CONTRACT_IMAGE", result.getBusinessType());
        }

        @Test
        @DisplayName("正常上传 PNG 图片文件")
        void upload_PNG_image_normalFlow() {
            // Given
            MockMultipartFile pngFile = new MockMultipartFile(
                "image",
                "screenshot.png",
                "image/png",
                "PNG 图片内容".getBytes()
            );

            String expectedPath = "project/docs/" + System.currentTimeMillis() + ".png";

            when(minioService.upload(pngFile, "project/docs/")).thenReturn(expectedPath);
            when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

            // When
            FileInfo result = fileService.upload(pngFile, null, null, 1L);

            // Then
            assertNotNull(result);
            assertEquals("screenshot.png", result.getOriginalName());
            assertEquals("image/png", result.getFileType());
            assertEquals(null, result.getBusinessType());
            assertEquals(null, result.getBusinessId());
        }

        @Test
        @DisplayName("上传空文件时抛出异常")
        void upload_emptyFile_throwsException() {
            // Given
            MultipartFile emptyFile = mock(MultipartFile.class);
            when(emptyFile.isEmpty()).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> fileService.upload(emptyFile, "TEST", 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上传文件不能为空");
        }

        @Test
        @DisplayName("上传 null 文件时抛出异常")
        void upload_nullFile_throwsException() {
            // When & Then
            assertThatThrownBy(() -> fileService.upload(null, "TEST", 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上传文件不能为空");
        }

        @Test
        @DisplayName("上传无扩展名文件时正确处理")
        void upload_fileWithoutExtension_normalFlow() {
            // Given
            MockMultipartFile noExtFile = new MockMultipartFile(
                "data",
                "file_without_extension",
                "application/octet-stream",
                "二进制数据".getBytes()
            );

            String expectedPath = "project/docs/" + System.currentTimeMillis();

            when(minioService.upload(noExtFile, "project/docs/")).thenReturn(expectedPath);
            when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

            // When
            FileInfo result = fileService.upload(noExtFile, "DATA_UPLOAD", 300L, 1L);

            // Then
            assertNotNull(result);
            assertEquals("file_without_extension", result.getFileName());
            assertEquals(expectedPath, result.getFilePath());
        }

        @Test
        @DisplayName("上传含特殊字符的文件名")
        void upload_fileWithSpecialChars_normalFlow() {
            // Given
            MockMultipartFile specialFile = new MockMultipartFile(
                "file",
                "测试_文档_v2.0(最终版).pdf",
                "application/pdf",
                "内容".getBytes()
            );

            String expectedPath = "project/docs/" + System.currentTimeMillis() + ".pdf";

            when(minioService.upload(specialFile, "project/docs/")).thenReturn(expectedPath);
            when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

            // When
            FileInfo result = fileService.upload(specialFile, "PROJECT_ATTACHMENT", 500L, 1L);

            // Then
            assertNotNull(result);
            assertEquals("测试_文档_v2.0(最终版).pdf", result.getOriginalName());
        }
    }

    // ==================== 文件删除测试 ====================

    @Nested
    @DisplayName("文件删除场景")
    class DeleteTests {

        @Test
        @DisplayName("正常删除存在的文件")
        void delete_existingFile_success() {
            // Given
            Long fileId = 1L;
            FileInfo existingFile = new FileInfo();
            existingFile.setId(fileId);
            existingFile.setFilePath("project/docs/test.pdf");

            when(fileInfoMapper.selectById(fileId)).thenReturn(existingFile);
            doNothing().when(minioService).delete("project/docs/test.pdf");
            doNothing().when(fileInfoMapper).deleteById(fileId);

            // When
            fileService.delete(fileId);

            // Then
            verify(minioService).delete("project/docs/test.pdf");
            verify(fileInfoMapper).deleteById(fileId);
        }

        @Test
        @DisplayName("删除不存在的文件时抛出异常")
        void delete_nonExistingFile_throwsException() {
            // Given
            Long nonExistentId = 999L;

            when(fileInfoMapper.selectById(nonExistentId)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> fileService.delete(nonExistentId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件不存在");
        }

        @Test
        @DisplayName("删除文件时 MinIO 删除失败仍会抛出异常")
        void delete_minioDeleteFails_throwsException() {
            // Given
            Long fileId = 2L;
            FileInfo fileInfo = new FileInfo();
            fileInfo.setId(fileId);
            fileInfo.setFilePath("project/docs/fail.pdf");

            when(fileInfoMapper.selectById(fileId)).thenReturn(fileInfo);
            doThrow(new RuntimeException("MinIO 删除失败"))
                .when(minioService).delete("project/docs/fail.pdf");

            // When & Then
            assertThatThrownBy(() -> fileService.delete(fileId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MinIO 删除失败");
        }
    }

    // ==================== 文件查询测试 ====================

    @Nested
    @DisplayName("文件查询场景")
    class QueryTests {

        @Test
        @DisplayName("按业务类型和 ID 查询文件列表")
        void getByBusiness_returnsList() {
            // Given
            String businessType = "PROJECT_DOCUMENT";
            Long businessId = 100L;

            List<FileInfo> mockFiles = List.of(
                createFileInfo(1L, businessType, businessId),
                createFileInfo(2L, businessType, businessId),
                createFileInfo(3L, businessType, businessId)
            );

            when(fileInfoMapper.selectList(any())).thenReturn(mockFiles);

            // When
            List<FileInfo> result = fileService.getByBusiness(businessType, businessId);

            // Then
            assertNotNull(result);
            assertEquals(3, result.size());
            for (FileInfo fileInfo : result) {
                assertEquals(businessType, fileInfo.getBusinessType());
                assertEquals(businessId, fileInfo.getBusinessId());
            }

            verify(fileInfoMapper).selectList(any());
        }

        @Test
        @DisplayName("查询无文件的业务返回空列表")
        void getByBusiness_noFiles_returnsEmptyList() {
            // Given
            String businessType = "NON_EXISTENT";
            Long businessId = 999L;

            when(fileInfoMapper.selectList(any())).thenReturn(List.of());

            // When
            List<FileInfo> result = fileService.getByBusiness(businessType, businessId);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("查询结果按创建时间倒序排列")
        void getByBusiness_orderedByCreatedAt_desc() {
            // Given
            String businessType = "TEST";
            Long businessId = 1L;

            List<FileInfo> mockFiles = List.of(
                createFileInfo(3L, businessType, businessId, 1000L), // 最新
                createFileInfo(1L, businessType, businessId, 800L),   // 最早
                createFileInfo(2L, businessType, businessId, 900L)    // 中间
            );

            when(fileInfoMapper.selectList(any())).thenReturn(mockFiles);

            // When
            List<FileInfo> result = fileService.getByBusiness(businessType, businessId);

            // Then
            assertEquals(3L, result.get(0).getId()); // 最新的应该在第一位
            assertEquals(1L, result.get(2).getId()); // 最早的应该在最后一位
        }
    }

    // ==================== 辅助方法 ====================

    private FileInfo createFileInfo(Long id, String businessType, Long businessId) {
        return createFileInfo(id, businessType, businessId, System.currentTimeMillis());
    }

    private FileInfo createFileInfo(Long id, String businessType, Long businessId, long createdAt) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(id);
        fileInfo.setOriginalName("test.pdf");
        fileInfo.setFileName("test.pdf");
        fileInfo.setFilePath("/path/to/test.pdf");
        fileInfo.setFileSize(1024L);
        fileInfo.setFileType("application/pdf");
        fileInfo.setStorageType("MINIO");
        fileInfo.setBusinessType(businessType);
        fileInfo.setBusinessId(businessId);
        fileInfo.setProjectId(1L);
        fileInfo.setCreatedAt(createdAt);
        return fileInfo;
    }
}
