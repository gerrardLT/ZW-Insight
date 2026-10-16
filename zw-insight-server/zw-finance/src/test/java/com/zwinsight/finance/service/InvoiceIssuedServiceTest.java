package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizInvoiceIssued;
import com.zwinsight.finance.mapper.BizInvoiceIssuedMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InvoiceIssuedService 单元测试（V2026_110，C3）——销项发票台账。
 */
@ExtendWith(MockitoExtension.class)
class InvoiceIssuedServiceTest {

    /** verify/markBookkeep 用 LambdaUpdateWrapper，需预初始化实体列缓存 */
    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), BizInvoiceIssued.class);
    }

    @Mock
    private BizInvoiceIssuedMapper issuedMapper;

    @InjectMocks
    private InvoiceIssuedService service;

    private BizInvoiceIssued blue() {
        BizInvoiceIssued i = new BizInvoiceIssued();
        i.setProjectId(10L);
        i.setContractId(20L);
        i.setInvoiceNumber("24331000000012345678");
        i.setIssueDate(LocalDate.of(2026, 10, 10));
        i.setDirection(BizInvoiceIssued.DIRECTION_BLUE);
        i.setAmount(new BigDecimal("109000"));
        i.setTaxRate(new BigDecimal("9"));
        return i;
    }

    @Test
    @DisplayName("save - 数电票：号码为身份（代码置空），税额/不含税差额法派生")
    void save_digitalInvoice_derivesTax() {
        BizInvoiceIssued i = blue();
        i.setInvoiceCode("1234567890"); // 数电票附码应被规范为空

        service.save(i);

        assertThat(i.getInvoiceCode()).isEmpty();
        assertThat(i.getAmountWithoutTax()).isEqualByComparingTo("100000.00");
        assertThat(i.getTaxAmount()).isEqualByComparingTo("9000.00");
        assertThat(i.getVerifyStatus()).isEqualTo(BizInvoiceIssued.VERIFY_UNVERIFIED);
        assertThat(i.getBookkeepStatus()).isEqualTo(BizInvoiceIssued.BOOK_UNBOOKED);
        verify(issuedMapper).insert(i);
    }

    @Test
    @DisplayName("save - 号码规则：非8/20位拒绝；8位无代码拒绝")
    void save_numberRules() {
        BizInvoiceIssued bad = blue();
        bad.setInvoiceNumber("12345");
        assertThatThrownBy(() -> service.save(bad)).hasMessageContaining("8位或20位");

        BizInvoiceIssued legacyNoCode = blue();
        legacyNoCode.setInvoiceNumber("12345678");
        legacyNoCode.setInvoiceCode("");
        assertThatThrownBy(() -> service.save(legacyNoCode)).hasMessageContaining("必须提供");
        verify(issuedMapper, never()).insert(any(BizInvoiceIssued.class));
    }

    @Test
    @DisplayName("save - 蓝字须正数；红字须负数且挂原票、同项目同合同、不超可红冲额")
    void save_directionAndRedRules() {
        BizInvoiceIssued negativeBlue = blue();
        negativeBlue.setAmount(new BigDecimal("-100"));
        assertThatThrownBy(() -> service.save(negativeBlue)).hasMessageContaining("蓝字");

        BizInvoiceIssued redNoOriginal = blue();
        redNoOriginal.setDirection(BizInvoiceIssued.DIRECTION_RED);
        redNoOriginal.setAmount(new BigDecimal("-5000"));
        assertThatThrownBy(() -> service.save(redNoOriginal)).hasMessageContaining("关联原");

        BizInvoiceIssued red = blue();
        red.setDirection(BizInvoiceIssued.DIRECTION_RED);
        red.setAmount(new BigDecimal("-5000"));
        red.setOriginalId(88L);
        BizInvoiceIssued original = blue();
        original.setId(88L);
        when(issuedMapper.selectById(88L)).thenReturn(original);
        when(issuedMapper.remainingRedAmount(eq(88L), isNull())).thenReturn(new BigDecimal("3000"));
        assertThatThrownBy(() -> service.save(red)).hasMessageContaining("剩余可红冲额");

        when(issuedMapper.remainingRedAmount(eq(88L), isNull())).thenReturn(new BigDecimal("109000"));
        service.save(red);
        // 红字税额取负
        assertThat(red.getTaxAmount()).isEqualByComparingTo(new BigDecimal("-412.84"));
        verify(issuedMapper).insert(red);
    }

    @Test
    @DisplayName("save - 红字挂非蓝字原票/跨项目拒绝")
    void save_redOriginalMismatch() {
        BizInvoiceIssued red = blue();
        red.setDirection(BizInvoiceIssued.DIRECTION_RED);
        red.setAmount(new BigDecimal("-5000"));
        red.setOriginalId(88L);
        BizInvoiceIssued redOriginal = blue();
        redOriginal.setId(88L);
        redOriginal.setDirection(BizInvoiceIssued.DIRECTION_RED);
        when(issuedMapper.selectById(88L)).thenReturn(redOriginal);
        assertThatThrownBy(() -> service.save(red)).hasMessageContaining("非蓝字");

        BizInvoiceIssued otherProject = blue();
        otherProject.setId(88L);
        otherProject.setProjectId(99L);
        when(issuedMapper.selectById(88L)).thenReturn(otherProject);
        assertThatThrownBy(() -> service.save(red)).hasMessageContaining("同项目同合同");
    }

    @Test
    @DisplayName("save - 身份冲突转 409（DB 唯一 guard 兜底）")
    void save_duplicateTranslates409() {
        BizInvoiceIssued i = blue();
        when(issuedMapper.insert(any(BizInvoiceIssued.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));
        assertThatThrownBy(() -> service.save(i))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已登记");
    }

    @Test
    @DisplayName("verify - 仅 VERIFIED/MISMATCH；不存在拒绝；CAS 失败拒绝")
    void verify_rules() {
        assertThatThrownBy(() -> service.verify(1L, "WHATEVER", 7L)).hasMessageContaining("VERIFIED/MISMATCH");

        when(issuedMapper.selectById(2L)).thenReturn(null);
        assertThatThrownBy(() -> service.verify(2L, "VERIFIED", 7L)).hasMessageContaining("不存在");

        when(issuedMapper.selectById(3L)).thenReturn(blue());
        when(issuedMapper.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.verify(3L, "VERIFIED", 7L)).hasMessageContaining("刷新重试");
    }

    @Test
    @DisplayName("markBookkeep - 纯状态登记，不动金额")
    void markBookkeep_toggles() {
        when(issuedMapper.selectById(1L)).thenReturn(blue());
        when(issuedMapper.update(isNull(), any())).thenReturn(1);

        service.markBookkeep(1L, true);

        verify(issuedMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("delete - 存在关联红字拒绝（防原票先删导致红冲悬空）")
    void delete_blockedByRedChildren() {
        when(issuedMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(1L)).hasMessageContaining("红字");
        verify(issuedMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("page - 分页透传（含方向/申请来源筛选）")
    void page_delegates() {
        Page<BizInvoiceIssued> page = new Page<>(1, 10);
        page.setRecords(List.of(blue()));
        page.setTotal(1L);
        when(issuedMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizInvoiceIssued> result = service.page(1, 10, 10L, 20L, "BLUE", 55L);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("sumTaxAmount - 蓝正红负净额；空台账为 0")
    void sumTaxAmount_net() {
        BizInvoiceIssued blueRow = blue(); // taxAmount 9000
        BizInvoiceIssued redRow = blue();
        redRow.setTaxAmount(new BigDecimal("-412.84"));
        when(issuedMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(blueRow, redRow));
        assertThat(service.sumTaxAmount(10L)).isEqualByComparingTo("8587.16");

        when(issuedMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        assertThat(service.sumTaxAmount(10L)).isEqualByComparingTo("0");
    }
}
