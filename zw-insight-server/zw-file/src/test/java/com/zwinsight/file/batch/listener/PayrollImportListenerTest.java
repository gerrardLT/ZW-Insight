package com.zwinsight.file.batch.listener;

import com.zwinsight.file.batch.dto.PayrollExcelDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PayrollImportListener 单元测试（工资单导入校验：班组解析/周期校验/文件内去重）
 */
class PayrollImportListenerTest {

    private PayrollExcelDTO validRow() {
        PayrollExcelDTO row = new PayrollExcelDTO();
        row.setTeamName("木工一班");
        row.setOrderType("固定");
        row.setPeriodStart("2026-04-01");
        row.setPeriodEnd("2026-04-30");
        return row;
    }

    private PayrollImportListener listener(List<PayrollExcelDTO> saved, boolean overlap) {
        return new PayrollImportListener(name -> "木工一班".equals(name) ? 66L : null,
                (teamId, start, end) -> overlap, saved::addAll);
    }

    private String firstError(PayrollImportListener listener, PayrollExcelDTO row) {
        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);
        return listener.getImportResult().getErrors().get(0).getErrorMessage();
    }

    @Test
    @DisplayName("班组名称为空/班组不存在 - 分别行级拒绝")
    void teamValidation_rejected() {
        PayrollExcelDTO blank = validRow();
        blank.setTeamName(null);
        assertThat(firstError(listener(new ArrayList<>(), false), blank)).contains("班组名称不能为空");

        PayrollExcelDTO unknown = validRow();
        unknown.setTeamName("不存在班组");
        assertThat(firstError(listener(new ArrayList<>(), false), unknown)).contains("不存在");
    }

    @Test
    @DisplayName("用工类型非法 - 行级拒绝；FIXED/TEMPORARY 英文值放行")
    void orderTypeValidation() {
        PayrollExcelDTO bad = validRow();
        bad.setOrderType("长期");
        assertThat(firstError(listener(new ArrayList<>(), false), bad)).contains("用工类型错误");

        List<PayrollExcelDTO> saved = new ArrayList<>();
        PayrollExcelDTO english = validRow();
        english.setOrderType("FIXED");
        PayrollImportListener listener = listener(saved, false);
        listener.invoke(english, null);
        listener.doAfterAllAnalysed(null);
        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
    }

    @Test
    @DisplayName("周期日期缺失/格式错误/倒置 - 分别行级拒绝")
    void periodValidation_rejected() {
        PayrollExcelDTO noStart = validRow();
        noStart.setPeriodStart(" ");
        assertThat(firstError(listener(new ArrayList<>(), false), noStart)).contains("周期开始日期不能为空");

        PayrollExcelDTO noEnd = validRow();
        noEnd.setPeriodEnd(null);
        assertThat(firstError(listener(new ArrayList<>(), false), noEnd)).contains("周期结束日期不能为空");

        PayrollExcelDTO badStart = validRow();
        badStart.setPeriodStart("2026/04/01");
        assertThat(firstError(listener(new ArrayList<>(), false), badStart)).contains("周期开始日期格式错误");

        PayrollExcelDTO badEnd = validRow();
        badEnd.setPeriodEnd("月底");
        assertThat(firstError(listener(new ArrayList<>(), false), badEnd)).contains("周期结束日期格式错误");

        PayrollExcelDTO reversed = validRow();
        reversed.setPeriodStart("2026-05-01");
        reversed.setPeriodEnd("2026-04-01");
        assertThat(firstError(listener(new ArrayList<>(), false), reversed)).contains("周期结束日期不能早于开始日期");
    }

    @Test
    @DisplayName("同文件内重复 班组+周期 行 - 第二行拒绝")
    void duplicatedPeriodInFile_rejected() {
        PayrollImportListener listener = listener(new ArrayList<>(), false);

        listener.invoke(validRow(), null);
        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().getSuccessRows()).isEqualTo(1);
        assertThat(listener.getImportResult().getFailedRows()).isEqualTo(1);
        assertThat(listener.getImportResult().getErrors().get(0).getErrorMessage()).contains("重复周期行");
    }

    @Test
    @DisplayName("与已有工资单周期重叠 - 行级拒绝")
    void periodOverlap_rejected() {
        assertThat(firstError(listener(new ArrayList<>(), true), validRow())).contains("周期重叠");
    }

    @Test
    @DisplayName("正常行 - teamId 回填并委托批量保存")
    void validRow_teamIdResolvedAndSaved() {
        List<PayrollExcelDTO> saved = new ArrayList<>();
        PayrollImportListener listener = listener(saved, false);
        PayrollExcelDTO row = validRow();

        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTeamId()).isEqualTo(66L);
    }
}
