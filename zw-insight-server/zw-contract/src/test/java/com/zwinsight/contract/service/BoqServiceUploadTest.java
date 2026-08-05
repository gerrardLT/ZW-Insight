package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizBoqItem;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.dto.BoqExcelRow;
import com.zwinsight.contract.mapper.BizBoqItemMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.file.domain.FileInfo;
import com.zwinsight.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * BoqService 上传/删除守卫与层级构建补充测试
 * <p>与 BoqServiceTest 互补：本类覆盖 uploadBoq/deleteBoq 的全部守卫分支与 buildHierarchyAndInsert 层级规则。</p>
 */
@ExtendWith(MockitoExtension.class)
class BoqServiceUploadTest {

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

    private BizConstructionContract contract(String status) {
        BizConstructionContract c = new BizConstructionContract();
        c.setId(1L);
        c.setProjectId(10L);
        c.setStatus(status);
        return c;
    }

    private BoqExcelRow row(String code, String name, String totalPrice) {
        BoqExcelRow r = new BoqExcelRow();
        r.setItemCode(code);
        r.setItemName(name);
        r.setUnit("m3");
        r.setQuantity(new BigDecimal("10"));
        r.setUnitPrice(new BigDecimal(totalPrice).divide(new BigDecimal("10")));
        r.setTotalPrice(new BigDecimal(totalPrice));
        return r;
    }

    // ── uploadBoq 守卫 ──────────────────────────────────

    @Test
    @DisplayName("uploadBoq - 合同不存在/状态不允许抛异常")
    void uploadBoq_contractGuards_throws() {
        when(contractMapper.selectById(1L)).thenReturn(null);
        MultipartFile file = mock(MultipartFile.class);
        assertThatThrownBy(() -> service.uploadBoq(1L, file))
                .hasMessageContaining("合同不存在");

        when(contractMapper.selectById(2L)).thenReturn(contract("DRAFT"));
        assertThatThrownBy(() -> service.uploadBoq(2L, file))
                .hasMessageContaining("当前合同状态不允许上传清单");
    }

    @Test
    @DisplayName("uploadBoq - 文件为空/超 20MB 抛异常")
    void uploadBoq_fileGuards_throws() {
        when(contractMapper.selectById(1L)).thenReturn(contract("EFFECTIVE"));

        assertThatThrownBy(() -> service.uploadBoq(1L, null))
                .hasMessageContaining("上传文件不能为空");

        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        assertThatThrownBy(() -> service.uploadBoq(1L, empty))
                .hasMessageContaining("上传文件不能为空");

        MultipartFile tooLarge = mock(MultipartFile.class);
        when(tooLarge.isEmpty()).thenReturn(false);
        when(tooLarge.getSize()).thenReturn(21L * 1024 * 1024);
        assertThatThrownBy(() -> service.uploadBoq(1L, tooLarge))
                .hasMessageContaining("不能超过20MB");
    }

    @Test
    @DisplayName("uploadBoq - 已被产值上报引用拒绝覆盖")
    void uploadBoq_referenced_throws() {
        when(contractMapper.selectById(1L)).thenReturn(contract("EFFECTIVE"));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(outputReportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.uploadBoq(1L, file))
                .hasMessageContaining("已被产值上报引用");
    }

    @Test
    @DisplayName("uploadBoq - IO 异常包装为业务异常")
    void uploadBoq_ioException_wrapped() throws IOException {
        when(contractMapper.selectById(1L)).thenReturn(contract("EFFECTIVE"));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(outputReportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(fileService.upload(any(), anyString(), anyLong(), anyLong())).thenReturn(new FileInfo());
        when(file.getInputStream()).thenThrow(new IOException("流损坏"));

        assertThatThrownBy(() -> service.uploadBoq(1L, file))
                .hasMessageContaining("文件读取失败");
    }

    // ── buildHierarchyAndInsert ──────────────────────────────────

    @Test
    @DisplayName("buildHierarchyAndInsert - 层级计算、父编码推导、超 4 级截断、孤儿父级兜底")
    void buildHierarchy_levelAndParentRules() {
        AtomicLong idSeq = new AtomicLong(100);
        // 模拟 ASSIGN_ID：insert 时回填 id
        doAnswer(inv -> {
            BizBoqItem item = inv.getArgument(0);
            item.setId(idSeq.getAndIncrement());
            return 1;
        }).when(boqItemMapper).insert(any(BizBoqItem.class));

        List<BoqExcelRow> rows = Arrays.asList(
                row("1", "一级A", "1000"),
                row("1.1", "二级A", "600"),
                row("1.1.1", "三级A", "300"),
                row("1.1.1.1", "四级A", "100"),
                row("1.1.1.1.1", "五段编码截断为4级", "50"),
                row("9.9", "孤儿（父编码9不存在）", "200"));

        List<BizBoqItem> items = service.buildHierarchyAndInsert(rows, 1L);

        assertThat(items).hasSize(6);
        // 层级：1,2,3,4,4(截断),2
        assertThat(items).extracting(BizBoqItem::getLevel)
                .containsExactly(1, 2, 3, 4, 4, 2);
        // 父级关系：1.1 的父是 1（id=100）；1.1.1 的父是 1.1（id=101）
        assertThat(items.get(0).getParentId()).isZero();
        assertThat(items.get(1).getParentId()).isEqualTo(100L);
        assertThat(items.get(2).getParentId()).isEqualTo(101L);
        // 孤儿父级不存在 → parentId=0
        assertThat(items.get(5).getParentId()).isZero();
        // 排序号递增
        assertThat(items.get(3).getSortOrder()).isEqualTo(4);
        // 完成量初始 0
        assertThat(items.get(0).getCompletedQuantity()).isEqualByComparingTo("0");
    }

    // ── getParentCode ──────────────────────────────────

    @Test
    @DisplayName("getParentCode - 编码层级推导规则")
    void getParentCode_rules() {
        assertThat(service.getParentCode("1.2.3")).isEqualTo("1.2");
        assertThat(service.getParentCode("1.2")).isEqualTo("1");
        assertThat(service.getParentCode("1")).isNull();
    }

    // ── deleteBoq ──────────────────────────────────

    @Test
    @DisplayName("deleteBoq - 合同不存在/状态不允许/已引用分别抛异常")
    void deleteBoq_guardCases_throws() {
        when(contractMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.deleteBoq(1L)).hasMessageContaining("合同不存在");

        when(contractMapper.selectById(2L)).thenReturn(contract("SETTLED"));
        assertThatThrownBy(() -> service.deleteBoq(2L)).hasMessageContaining("不允许操作清单");

        when(contractMapper.selectById(3L)).thenReturn(contract("EFFECTIVE"));
        when(outputReportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.deleteBoq(3L)).hasMessageContaining("已被产值上报引用");
    }

    @Test
    @DisplayName("deleteBoq - 正常删除")
    void deleteBoq_success() {
        when(contractMapper.selectById(1L)).thenReturn(contract("CHANGING"));
        when(outputReportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.deleteBoq(1L);

        verify(boqItemMapper).deleteByContractId(1L);
    }

    // ── 查询透传 ──────────────────────────────────

    @Test
    @DisplayName("getBoqTree / getBoqFlat - 查询透传")
    void query_delegates() {
        when(boqItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(new BizBoqItem()));

        assertThat(service.getBoqTree(1L)).hasSize(1);
        assertThat(service.getBoqFlat(1L)).hasSize(1);
    }
}
