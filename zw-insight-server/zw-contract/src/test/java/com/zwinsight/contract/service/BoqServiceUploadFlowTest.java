package com.zwinsight.contract.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizBoqItem;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizBoqItemMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.contract.dto.BoqUploadResultVO;
import com.zwinsight.file.domain.FileInfo;
import com.zwinsight.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BoqService 上传主链路测试（P1 BOQ-06/08 补测，2026-08-13）
 * <p>
 * 使用真实 xlsx 文件走完整 EasyExcel 解析链路（不 mock 解析器），覆盖上传编排：
 * 解析结果为空拒绝（BOQ-06）、行校验失败拒绝、删旧→插入→顶层合计回写合同额（BOQ-08）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoqServiceUploadFlowTest {

    /** BOQ 模板列头（列0编码|列1名称|列2单位|列3数量|列4单价|列5合价） */
    private static final List<List<String>> BOQ_HEAD = List.of(
            List.of("项目编码"), List.of("项目名称"), List.of("单位"),
            List.of("工程数量"), List.of("综合单价"), List.of("合价"));

    @TempDir
    Path tempDir;

    @Mock
    private BizBoqItemMapper boqItemMapper;

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private BizOutputReportMapper outputReportMapper;

    @Mock
    private FileService fileService;

    @InjectMocks
    private BoqService service;

    /** 当前用例的合同对象（uploadWithExcel 注入，供断言回写字段） */
    private BizConstructionContract currentContract;

    /** 生成 BOQ xlsx 并包装为 MultipartFile（读完字节即删临时文件，避免 Windows 文件句柄残留） */
    private MultipartFile boqFile(List<List<Object>> dataRows) throws Exception {
        Path file = tempDir.resolve("boq-" + System.nanoTime() + ".xlsx");
        EasyExcel.write(file.toFile()).head(BOQ_HEAD).sheet().doWrite(dataRows);
        byte[] bytes = Files.readAllBytes(file);
        Files.delete(file);
        return new MockMultipartFile("file", "boq.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    /** 准备 EFFECTIVE 合同上下文并执行上传 */
    private BoqUploadResultVO uploadWithExcel(MultipartFile file) {
        currentContract = new BizConstructionContract();
        currentContract.setId(1L);
        currentContract.setProjectId(10L);
        currentContract.setStatus("EFFECTIVE");
        when(contractMapper.selectById(1L)).thenReturn(currentContract);
        when(outputReportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(fileService.upload(any(), anyString(), anyLong(), anyLong())).thenReturn(new FileInfo());

        return service.uploadBoq(1L, file);
    }

    @Test
    @DisplayName("解析结果为空 → 拒绝导入（P1 BOQ-06）")
    void emptyParseResult_rejected() throws Exception {
        // 仅列头无数据行
        MultipartFile file = boqFile(List.of());

        assertThatThrownBy(() -> uploadWithExcel(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("解析结果为空");

        verify(boqItemMapper, never()).deleteByContractId(anyLong());
    }

    @Test
    @DisplayName("行校验失败（编码缺失）→ 拒绝导入（P1 BOQ-06 关联分支）")
    void rowValidationError_rejected() throws Exception {
        MultipartFile file = boqFile(List.of(
                Arrays.asList(null, "缺编码行", "m3", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100"))));

        assertThatThrownBy(() -> uploadWithExcel(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("清单文件校验失败")
                .hasMessageContaining("项目编码不能为空");

        verify(boqItemMapper, never()).deleteByContractId(anyLong());
    }

    @Test
    @DisplayName("畸形文件（截断字节）→ 友好业务拒绝，不走全局兜底 500（台账数据态#2）")
    void corruptedFile_rejectedGracefully() throws Exception {
        // 截断正常 xlsx 字节：zip 结构破损，EasyExcel/POI 抛运行时解析异常
        Path file = tempDir.resolve("corrupt-" + System.nanoTime() + ".xlsx");
        EasyExcel.write(file.toFile()).head(BOQ_HEAD).sheet().doWrite(List.of(
                List.of("1", "土建", "m3", new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1000"))));
        byte[] full = Files.readAllBytes(file);
        Files.delete(file);
        byte[] truncated = Arrays.copyOf(full, Math.min(32, full.length / 4));
        MultipartFile corrupt = new MockMultipartFile("file", "boq.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", truncated);

        assertThatThrownBy(() -> uploadWithExcel(corrupt))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Excel文件解析失败");

        verify(boqItemMapper, never()).deleteByContractId(anyLong());
        verify(boqItemMapper, never()).insert(any(BizBoqItem.class));
    }

    @Test
    @DisplayName("上传主链路：删旧→插入→顶层合计回写合同额（P1 BOQ-08）")
    void uploadHappyPath_deleteInsertWriteBackTotal() throws Exception {
        MultipartFile file = boqFile(List.of(
                List.of("1", "土建", "m3", new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1000")),
                List.of("2", "安装", "m3", new BigDecimal("10"), new BigDecimal("250.0555"), new BigDecimal("2500.555"))));

        BoqUploadResultVO result = uploadWithExcel(file);

        // 删旧 + 逐条插入
        verify(boqItemMapper).deleteByContractId(1L);
        verify(boqItemMapper, times(2)).insert(any(BizBoqItem.class));
        // 顶层合计回写合同额（2位小数 HALF_UP：1000 + 2500.555 = 3500.56）
        verify(contractMapper).updateById(currentContract);
        assertThat(currentContract.getContractAmount()).isEqualByComparingTo("3500.56");
        // 返回结果
        assertThat(result.getTotalItems()).isEqualTo(2);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("3500.56");
        assertThat(result.getLevelCount()).isEqualTo(1);
    }
}
