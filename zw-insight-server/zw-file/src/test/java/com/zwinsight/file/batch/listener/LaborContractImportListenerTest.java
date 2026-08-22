package com.zwinsight.file.batch.listener;

import com.zwinsight.file.batch.dto.LaborContractExcelDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LaborContractImportListener 单元测试（劳务合同导入校验分支）
 */
class LaborContractImportListenerTest {

    private LaborContractExcelDTO validRow() {
        LaborContractExcelDTO row = new LaborContractExcelDTO();
        row.setContractName("木工班组劳务合同");
        row.setContractCode("LB-2026-001");
        row.setContractAmount("800000");
        row.setSigningDate("2026-03-01");
        row.setStartDate("2026-03-05");
        row.setEndDate("2026-11-30");
        return row;
    }

    private String firstError(LaborContractImportListener listener, LaborContractExcelDTO row) {
        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);
        return listener.getImportResult().getErrors().get(0).getErrorMessage();
    }

    @Test
    @DisplayName("合同名称为空 - 行级拒绝")
    void blankContractName_rejected() {
        LaborContractImportListener listener = new LaborContractImportListener(code -> false, list -> { });
        LaborContractExcelDTO row = validRow();
        row.setContractName("");
        assertThat(firstError(listener, row)).contains("合同名称不能为空");
    }

    @Test
    @DisplayName("合同编号已存在 - 行级拒绝")
    void duplicatedContractCode_rejected() {
        LaborContractImportListener listener = new LaborContractImportListener(code -> true, list -> { });
        assertThat(firstError(listener, validRow())).contains("已存在");
    }

    @Test
    @DisplayName("未填合同编号 - 跳过编号查重并校验通过")
    void noContractCode_skipsDuplicateCheck() {
        List<LaborContractExcelDTO> saved = new ArrayList<>();
        LaborContractImportListener listener = new LaborContractImportListener(code -> true, saved::addAll);
        LaborContractExcelDTO row = validRow();
        row.setContractCode(null);

        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
        assertThat(saved).hasSize(1);
    }

    @Test
    @DisplayName("合同金额为空/格式错误 - 分别行级拒绝")
    void amountValidation_rejected() {
        LaborContractImportListener listener = new LaborContractImportListener(code -> false, list -> { });
        LaborContractExcelDTO blank = validRow();
        blank.setContractAmount(null);
        assertThat(firstError(listener, blank)).contains("合同金额不能为空");

        LaborContractImportListener listener2 = new LaborContractImportListener(code -> false, list -> { });
        LaborContractExcelDTO invalid = validRow();
        invalid.setContractAmount("八十万");
        assertThat(firstError(listener2, invalid)).contains("合同金额格式错误");
    }

    @Test
    @DisplayName("日期格式错误/结束早于开始 - 分别行级拒绝")
    void dateValidation_rejected() {
        LaborContractExcelDTO badDate = validRow();
        badDate.setSigningDate("2026.03.01");
        assertThat(firstError(new LaborContractImportListener(code -> false, list -> { }), badDate))
                .contains("签订日期格式错误");

        LaborContractExcelDTO badStart = validRow();
        badStart.setStartDate("三月");
        assertThat(firstError(new LaborContractImportListener(code -> false, list -> { }), badStart))
                .contains("开始日期格式错误");

        LaborContractExcelDTO badEnd = validRow();
        badEnd.setEndDate("2026/11/30");
        assertThat(firstError(new LaborContractImportListener(code -> false, list -> { }), badEnd))
                .contains("结束日期格式错误");

        LaborContractExcelDTO reversed = validRow();
        reversed.setStartDate("2026-08-01");
        reversed.setEndDate("2026-05-01");
        assertThat(firstError(new LaborContractImportListener(code -> false, list -> { }), reversed))
                .contains("结束日期不能早于开始日期");
    }

    @Test
    @DisplayName("正常行 - 校验通过并委托批量保存")
    void validRow_delegatedToBatchSave() {
        List<LaborContractExcelDTO> saved = new ArrayList<>();
        LaborContractImportListener listener = new LaborContractImportListener(code -> false, saved::addAll);

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
        assertThat(listener.getImportResult().getSuccessRows()).isEqualTo(1);
        assertThat(saved).hasSize(1);
    }
}
