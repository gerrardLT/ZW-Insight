package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InvoiceReceivedService 单元测试（阶段四批 1 补测）
 * <p>收票登记：分页回填项目名；新增置 APPROVED 并回写其他合同累计收票。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceReceivedService — 收票登记")
class InvoiceReceivedServiceTest {

    @Mock
    private BizInvoiceReceivedMapper invoiceReceivedMapper;

    @Mock
    private BizOtherContractMapper otherContractMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private InvoiceReceivedService service;

    @Test
    @DisplayName("page - 记录回填项目名称")
    void page_fillsProjectName() {
        BizInvoiceReceived record = new BizInvoiceReceived();
        record.setProjectId(1L);
        Page<BizInvoiceReceived> page = new Page<>(1, 10);
        page.setRecords(List.of(record));
        page.setTotal(1);
        when(invoiceReceivedMapper.selectPage(any(), any())).thenReturn(page);

        BizProject project = new BizProject();
        project.setId(1L);
        project.setProjectName("滨江花园一期");
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(project));

        PageResult<BizInvoiceReceived> result = service.page(1, 10, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getProjectName()).isEqualTo("滨江花园一期");
    }

    @Test
    @DisplayName("page - 空结果不查询项目名")
    void page_empty_skipsNameFill() {
        Page<BizInvoiceReceived> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(invoiceReceivedMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<BizInvoiceReceived> result = service.page(1, 10, 99L);

        assertThat(result.getRecords()).isEmpty();
        verify(projectMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("save - 置 APPROVED 并回写合同累计收票（null 按 0 起算）")
    void save_writesBackContractCumulative() {
        BizInvoiceReceived invoice = new BizInvoiceReceived();
        invoice.setContractId(5L);
        invoice.setInvoiceAmount(new BigDecimal("300"));

        BizOtherContract contract = new BizOtherContract();
        contract.setId(5L);
        contract.setCumulativeInvoice(null);
        when(otherContractMapper.selectById(5L)).thenReturn(contract);

        service.save(invoice);

        assertThat(invoice.getStatus()).isEqualTo("APPROVED");
        verify(invoiceReceivedMapper).insert(invoice);
        ArgumentCaptor<BizOtherContract> captor = ArgumentCaptor.forClass(BizOtherContract.class);
        verify(otherContractMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCumulativeInvoice()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("save - 合同已有累计收票时叠加")
    void save_addsToExistingCumulative() {
        BizInvoiceReceived invoice = new BizInvoiceReceived();
        invoice.setContractId(5L);
        invoice.setInvoiceAmount(new BigDecimal("200"));

        BizOtherContract contract = new BizOtherContract();
        contract.setId(5L);
        contract.setCumulativeInvoice(new BigDecimal("800"));
        when(otherContractMapper.selectById(5L)).thenReturn(contract);

        service.save(invoice);

        ArgumentCaptor<BizOtherContract> captor = ArgumentCaptor.forClass(BizOtherContract.class);
        verify(otherContractMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCumulativeInvoice()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("save - 合同不存在时仅落收票记录不回写")
    void save_contractMissing_noWriteBack() {
        BizInvoiceReceived invoice = new BizInvoiceReceived();
        invoice.setContractId(5L);
        invoice.setInvoiceAmount(new BigDecimal("100"));
        when(otherContractMapper.selectById(5L)).thenReturn(null);

        service.save(invoice);

        verify(invoiceReceivedMapper).insert(invoice);
        verify(otherContractMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("save - 无合同关联时跳过回写")
    void save_noContract_skipWriteBack() {
        BizInvoiceReceived invoice = new BizInvoiceReceived();
        invoice.setContractId(null);
        invoice.setInvoiceAmount(new BigDecimal("100"));

        service.save(invoice);

        verify(invoiceReceivedMapper).insert(invoice);
        verify(otherContractMapper, never()).selectById(any());
    }
}
