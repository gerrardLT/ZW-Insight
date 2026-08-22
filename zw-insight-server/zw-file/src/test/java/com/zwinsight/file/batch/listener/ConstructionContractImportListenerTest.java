package com.zwinsight.file.batch.listener;

import com.zwinsight.file.batch.dto.ConstructionContractExcelDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConstructionContractImportListener 单元测试（施工合同导入校验分支）
 */
class ConstructionContractImportListenerTest {

    private ConstructionContractExcelDTO validRow() {
        ConstructionContractExcelDTO row = new ConstructionContractExcelDTO();
        row.setProjectName("滨江花园一期");
        row.setPartyAName("甲方单位");
        row.setContractAmount("5000000");
        row.setTaxRate("0.09");
        row.setSigningDate("2026-01-10");
        row.setStartDate("2026-02-01");
        row.setEndDate("2026-12-31");
        return row;
    }

    private ConstructionContractImportListener listener(List<ConstructionContractExcelDTO> saved, boolean projectExists) {
        return new ConstructionContractImportListener(name -> projectExists, saved::addAll);
    }

    private String firstError(ConstructionContractImportListener listener, ConstructionContractExcelDTO row) {
        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);
        return listener.getImportResult().getErrors().get(0).getErrorMessage();
    }

    @Test
    @DisplayName("项目名称为空 - 行级拒绝")
    void blankProjectName_rejected() {
        ConstructionContractImportListener listener = listener(new ArrayList<>(), true);
        ConstructionContractExcelDTO row = validRow();
        row.setProjectName(null);
        assertThat(firstError(listener, row)).contains("项目名称不能为空");
    }

    @Test
    @DisplayName("项目不存在 - 行级拒绝")
    void projectNotFound_rejected() {
        ConstructionContractImportListener listener = listener(new ArrayList<>(), false);
        assertThat(firstError(listener, validRow())).contains("不存在");
    }

    @Test
    @DisplayName("合同金额为空 - 行级拒绝")
    void blankAmount_rejected() {
        ConstructionContractImportListener listener = listener(new ArrayList<>(), true);
        ConstructionContractExcelDTO row = validRow();
        row.setContractAmount(" ");
        assertThat(firstError(listener, row)).contains("合同金额不能为空");
    }

    @Test
    @DisplayName("合同金额格式错误 - 行级拒绝")
    void invalidAmount_rejected() {
        ConstructionContractImportListener listener = listener(new ArrayList<>(), true);
        ConstructionContractExcelDTO row = validRow();
        row.setContractAmount("五百万");
        assertThat(firstError(listener, row)).contains("合同金额格式错误");
    }

    @Test
    @DisplayName("税率格式错误 - 行级拒绝")
    void invalidTaxRate_rejected() {
        ConstructionContractImportListener listener = listener(new ArrayList<>(), true);
        ConstructionContractExcelDTO row = validRow();
        row.setTaxRate("9%");
        assertThat(firstError(listener, row)).contains("税率格式错误");
    }

    @Test
    @DisplayName("签订/开工/竣工日期格式错误 - 分别行级拒绝")
    void invalidDates_rejected() {
        ConstructionContractExcelDTO signing = validRow();
        signing.setSigningDate("2026/01/10");
        assertThat(firstError(listener(new ArrayList<>(), true), signing)).contains("签订日期格式错误");

        ConstructionContractExcelDTO start = validRow();
        start.setStartDate("20260201");
        assertThat(firstError(listener(new ArrayList<>(), true), start)).contains("开工日期格式错误");

        ConstructionContractExcelDTO end = validRow();
        end.setEndDate("年底");
        assertThat(firstError(listener(new ArrayList<>(), true), end)).contains("竣工日期格式错误");
    }

    @Test
    @DisplayName("竣工早于开工 - 行级拒绝")
    void endBeforeStart_rejected() {
        ConstructionContractImportListener listener = listener(new ArrayList<>(), true);
        ConstructionContractExcelDTO row = validRow();
        row.setStartDate("2026-06-01");
        row.setEndDate("2026-03-01");
        assertThat(firstError(listener, row)).contains("竣工日期不能早于开工日期");
    }

    @Test
    @DisplayName("正常行 - 校验通过并委托批量保存")
    void validRow_delegatedToBatchSave() {
        List<ConstructionContractExcelDTO> saved = new ArrayList<>();
        ConstructionContractImportListener listener = listener(saved, true);

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
        assertThat(listener.getImportResult().getSuccessRows()).isEqualTo(1);
        assertThat(saved).hasSize(1);
    }
}
