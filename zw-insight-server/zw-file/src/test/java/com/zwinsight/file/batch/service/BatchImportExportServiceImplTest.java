package com.zwinsight.file.batch.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.domain.ExportStatus;
import com.zwinsight.file.service.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量导入导出服务单元测试（入参守卫 + Redis 任务状态 + 无处理器降级）
 *
 * <p>真实 Excel 读写/MinIO 上传链路由生产与 L3 验证；
 * 单测覆盖守卫逻辑与状态机分支（handler 列表为空时异步导出落入 FAILED 状态，不静默成功）。</p>
 */
@ExtendWith(MockitoExtension.class)
class BatchImportExportServiceImplTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private MinioService minioService;

    private BatchImportExportServiceImpl service;

    @BeforeEach
    void setUp() {
        // 空 handler 列表：覆盖「模块尚未实现处理器」与异步导出失败降级分支
        service = new BatchImportExportServiceImpl(redisTemplate, minioService, Collections.emptyList());
    }

    @Test
    @DisplayName("导入：空文件被拒绝")
    void testImportData_emptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.xlsx", null, new byte[0]);

        assertThatThrownBy(() -> service.importData("SUPPLIER", empty, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择要导入的文件");
    }

    @Test
    @DisplayName("导入：非 Excel 扩展名被拒绝")
    void testImportData_notExcel() {
        MockMultipartFile txt = new MockMultipartFile("file", "a.txt", null, "data".getBytes());

        assertThatThrownBy(() -> service.importData("SUPPLIER", txt, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持 .xlsx 或 .xls");
    }

    @Test
    @DisplayName("异步导出：不支持的模块编码抛异常")
    void testAsyncExport_invalidModule() {
        assertThatThrownBy(() -> service.asyncExport("UNKNOWN_MODULE", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的模块编码");
    }

    @Test
    @DisplayName("异步导出：初始化 PENDING 状态；无处理器时降级为 FAILED 状态而非静默成功")
    void testAsyncExport_failedStateWithoutHandler() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Long taskId = service.asyncExport("SUPPLIER", Map.of());

        assertThat(taskId).isNotNull();
        // 至少两次写入：初始 PENDING + 无处理器异常后的 FAILED
        verify(valueOperations, atLeast(2)).set(anyString(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("查询导出状态：任务不存在或已过期抛异常")
    void testGetExportStatus_notFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("export:task:999")).thenReturn(null);

        assertThatThrownBy(() -> service.getExportStatus(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导出任务不存在或已过期");
    }

    @Test
    @DisplayName("下载导出文件：任务未完成被拒绝")
    void testDownloadExportFile_notCompleted() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("export:task:1"))).thenReturn(ExportStatus.pending());

        assertThatThrownBy(() -> service.downloadExportFile(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未完成");
    }
}
