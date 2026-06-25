package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizBoqItemMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BoqService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BoqServiceTest {

    @Mock
    private BizBoqItemMapper boqItemMapper;

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private BizOutputReportMapper outputReportMapper;

    @Mock
    private FileService fileService;

    @InjectMocks
    private BoqService boqService;

    @Test
    @DisplayName("上传BOQ - 合同非EFFECTIVE/CHANGING状态时拒绝")
    void testUploadBoq_rejectWhenContractStatusInvalid() {
        // Arrange
        Long contractId = 1L;
        MultipartFile file = mock(MultipartFile.class);

        BizConstructionContract contract = new BizConstructionContract();
        contract.setId(contractId);
        contract.setStatus("DRAFT");

        when(contractMapper.selectById(contractId)).thenReturn(contract);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boqService.uploadBoq(contractId, file));

        assertTrue(exception.getMessage().contains("当前合同状态不允许上传清单"));
        verify(contractMapper).selectById(contractId);
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("上传BOQ - 文件超过20MB时拒绝")
    void testUploadBoq_rejectWhenFileExceedsMaxSize() {
        // Arrange
        Long contractId = 1L;
        MultipartFile file = mock(MultipartFile.class);

        BizConstructionContract contract = new BizConstructionContract();
        contract.setId(contractId);
        contract.setStatus("EFFECTIVE");

        when(contractMapper.selectById(contractId)).thenReturn(contract);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(21 * 1024 * 1024L); // 21MB，超过限制

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boqService.uploadBoq(contractId, file));

        assertTrue(exception.getMessage().contains("上传文件大小不能超过20MB"));
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("上传BOQ - 有产值上报引用时拒绝覆盖")
    void testUploadBoq_rejectWhenOutputReportExists() {
        // Arrange
        Long contractId = 1L;
        MultipartFile file = mock(MultipartFile.class);

        BizConstructionContract contract = new BizConstructionContract();
        contract.setId(contractId);
        contract.setStatus("EFFECTIVE");

        when(contractMapper.selectById(contractId)).thenReturn(contract);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(5 * 1024 * 1024L); // 5MB，合规
        when(outputReportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boqService.uploadBoq(contractId, file));

        assertTrue(exception.getMessage().contains("已被产值上报引用"));
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("获取父编码 - '1.2.3'返回'1.2'")
    void testGetParentCode_returnsCorrectParent() {
        // Act
        String result = boqService.getParentCode("1.2.3");

        // Assert
        assertEquals("1.2", result);
    }

    @Test
    @DisplayName("获取父编码 - 顶层编码'1'返回null")
    void testGetParentCode_returnsNullForTopLevel() {
        // Act
        String result = boqService.getParentCode("1");

        // Assert
        assertNull(result);
    }
}
