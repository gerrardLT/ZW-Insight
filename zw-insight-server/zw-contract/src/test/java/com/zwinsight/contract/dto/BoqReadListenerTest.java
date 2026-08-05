package com.zwinsight.contract.dto;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.metadata.holder.ReadRowHolder;
import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BoqReadListener 单元测试
 * <p>Excel 行校验：必填校验、错误收集上限、条目上限、字段去空格。</p>
 */
class BoqReadListenerTest {

    private final BoqReadListener listener = new BoqReadListener();

    private AnalysisContext context(int rowIndex) {
        AnalysisContext ctx = mock(AnalysisContext.class);
        ReadRowHolder holder = mock(ReadRowHolder.class);
        when(holder.getRowIndex()).thenReturn(rowIndex);
        when(ctx.readRowHolder()).thenReturn(holder);
        return ctx;
    }

    private BoqExcelRow row(String code, String name) {
        BoqExcelRow r = new BoqExcelRow();
        r.setItemCode(code);
        r.setItemName(name);
        return r;
    }

    @Test
    @DisplayName("合法行 - 收集入 dataList 并去除首尾空格")
    void invoke_validRow_collected() {
        BoqExcelRow r = row(" 1.1 ", " 土方开挖 ");
        r.setUnit(" m3 ");

        listener.invoke(r, context(1));

        assertThat(listener.getDataList()).hasSize(1);
        assertThat(listener.getDataList().get(0).getItemCode()).isEqualTo("1.1");
        assertThat(listener.getDataList().get(0).getItemName()).isEqualTo("土方开挖");
        assertThat(listener.getDataList().get(0).getUnit()).isEqualTo("m3");
        assertThat(listener.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("编码/名称缺失 - 收集错误不入数据，行号转 1-based")
    void invoke_missingFields_collectsErrors() {
        listener.invoke(row(null, "名称"), context(4));
        listener.invoke(row("1", "  "), context(5));
        listener.invoke(row(null, null), context(6));

        assertThat(listener.getDataList()).isEmpty();
        assertThat(listener.hasErrors()).isTrue();
        assertThat(listener.getErrors()).hasSize(4); // 行5:1条，行6:1条，行7:2条
        assertThat(listener.getErrors().get(0)).contains("第5行").contains("项目编码不能为空");
        assertThat(listener.getErrors().get(1)).contains("第6行").contains("项目名称不能为空");
    }

    @Test
    @DisplayName("错误收集上限 100 条后不再追加")
    void invoke_errorCap_stopsAt100() {
        for (int i = 0; i < 120; i++) {
            listener.invoke(row(null, null), context(i));
        }

        assertThat(listener.getErrors()).hasSize(100);
        assertThat(listener.getDataList()).isEmpty();
    }

    @Test
    @DisplayName("条目上限 5000 条后抛异常")
    void invoke_itemCap_throws() {
        for (int i = 0; i < 5000; i++) {
            listener.invoke(row("1." + i, "条目" + i), context(i));
        }

        assertThatThrownBy(() -> listener.invoke(row("1.x", "溢出"), context(5001)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("清单条目超过上限");
    }

    @Test
    @DisplayName("doAfterAllAnalysed - 正常收尾不抛异常")
    void doAfterAllAnalysed_completes() {
        listener.invoke(row("1", "条目"), context(1));

        listener.doAfterAllAnalysed(context(2));

        assertThat(listener.getDataList()).hasSize(1);
    }
}
