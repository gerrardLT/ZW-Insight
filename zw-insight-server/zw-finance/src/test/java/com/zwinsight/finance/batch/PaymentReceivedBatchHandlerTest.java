package com.zwinsight.finance.batch;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.dto.PaymentReceivedExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PaymentReceivedBatchHandler 单元测试（回款登记导出处理器）
 */
@ExtendWith(MockitoExtension.class)
class PaymentReceivedBatchHandlerTest {

    @Mock
    private BizPaymentReceivedMapper paymentReceivedMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private PaymentReceivedBatchHandler handler;

    @Test
    @DisplayName("supports - 仅支持 PAYMENT_RECEIVED 模块")
    void supports_onlyPaymentReceived() {
        assertThat(handler.supports(ModuleCode.PAYMENT_RECEIVED)).isTrue();
        ModuleCode other = Arrays.stream(ModuleCode.values())
                .filter(c -> c != ModuleCode.PAYMENT_RECEIVED)
                .findFirst()
                .orElseThrow();
        assertThat(handler.supports(other)).isFalse();
    }

    @Test
    @DisplayName("createImportListener - 回款登记不支持导入，抛业务异常")
    void createImportListener_throws() {
        assertThatThrownBy(() -> handler.createImportListener(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持批量导入");
    }

    @Test
    @DisplayName("queryExportData - 实体转 DTO（回填项目名+已审批状态标签）")
    void queryExportData_mapsToDto() {
        BizPaymentReceived entity = new BizPaymentReceived();
        entity.setProjectId(10L);
        entity.setReceiveDate(LocalDate.of(2026, 3, 15));
        entity.setReceiveAmount(new BigDecimal("200000.00"));
        entity.setReceiver("对公转账");
        entity.setReceiveType("进度款");
        entity.setReceiveBankAccount("6222****1234");
        entity.setStatus("APPROVED");
        when(paymentReceivedMapper.selectList(any())).thenReturn(Collections.singletonList(entity));

        BizProject project = new BizProject();
        project.setId(10L);
        project.setProjectName("测试项目");
        when(projectMapper.selectList(null)).thenReturn(Collections.singletonList(project));

        List<?> result = handler.queryExportData(Collections.emptyMap());

        assertThat(result).hasSize(1);
        PaymentReceivedExcelDTO dto = (PaymentReceivedExcelDTO) result.get(0);
        assertThat(dto.getProjectName()).isEqualTo("测试项目");
        assertThat(dto.getReceiveDate()).isEqualTo("2026-03-15");
        assertThat(dto.getReceiveAmount()).isEqualTo("200000.00");
        assertThat(dto.getReceiver()).isEqualTo("对公转账");
        assertThat(dto.getReceiveType()).isEqualTo("进度款");
        assertThat(dto.getReceiveBankAccount()).isEqualTo("6222****1234");
        assertThat(dto.getStatus()).isEqualTo("已审批");
    }

    @Test
    @DisplayName("queryExportData - 非审批状态映射为草稿")
    void queryExportData_draftStatus() {
        BizPaymentReceived entity = new BizPaymentReceived();
        entity.setProjectId(99L);
        entity.setStatus("DRAFT");
        when(paymentReceivedMapper.selectList(any())).thenReturn(Collections.singletonList(entity));
        when(projectMapper.selectList(null)).thenReturn(Collections.emptyList());

        List<?> result = handler.queryExportData(Collections.emptyMap());

        PaymentReceivedExcelDTO dto = (PaymentReceivedExcelDTO) result.get(0);
        assertThat(dto.getStatus()).isEqualTo("草稿");
        assertThat(dto.getProjectName()).isEmpty();
        assertThat(dto.getReceiveAmount()).isEmpty();
    }
}
