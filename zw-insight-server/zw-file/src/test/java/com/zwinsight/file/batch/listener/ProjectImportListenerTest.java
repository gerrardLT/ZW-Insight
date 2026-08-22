package com.zwinsight.file.batch.listener;

import com.zwinsight.file.batch.dto.ProjectExcelDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProjectImportListener 单元测试（项目报备导入校验与批量保存委托）
 */
class ProjectImportListenerTest {

    private ProjectExcelDTO validRow() {
        ProjectExcelDTO row = new ProjectExcelDTO();
        row.setProjectName("  滨江花园二期  ");
        row.setBudgetAmount("1000.50");
        return row;
    }

    @Test
    @DisplayName("项目名称为空 - 行级拒绝")
    void blankProjectName_rejected() {
        ProjectImportListener listener = new ProjectImportListener(name -> false, list -> { });
        ProjectExcelDTO row = validRow();
        row.setProjectName("  ");

        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().getFailedRows()).isEqualTo(1);
        assertThat(listener.getImportResult().getErrors().get(0).getErrorMessage()).contains("项目名称不能为空");
    }

    @Test
    @DisplayName("预算金额格式错误 - 行级拒绝")
    void invalidBudgetAmount_rejected() {
        ProjectImportListener listener = new ProjectImportListener(name -> false, list -> { });
        ProjectExcelDTO row = validRow();
        row.setBudgetAmount("一千万");

        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().getFailedRows()).isEqualTo(1);
        assertThat(listener.getImportResult().getErrors().get(0).getErrorMessage()).contains("预算金额格式错误");
    }

    @Test
    @DisplayName("项目名称已存在 - 行级拒绝")
    void duplicatedProjectName_rejected() {
        ProjectImportListener listener = new ProjectImportListener(name -> true, list -> { });

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().getFailedRows()).isEqualTo(1);
        assertThat(listener.getImportResult().getErrors().get(0).getErrorMessage()).contains("已存在");
    }

    @Test
    @DisplayName("正常行 - 校验通过并委托批量保存")
    void validRow_delegatedToBatchSave() {
        List<ProjectExcelDTO> saved = new ArrayList<>();
        ProjectImportListener listener = new ProjectImportListener(name -> false, saved::addAll);

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
        assertThat(listener.getImportResult().getSuccessRows()).isEqualTo(1);
        assertThat(saved).hasSize(1);
    }

    @Test
    @DisplayName("批量保存抛异常 - 该批数据记为失败且不虚增成功数")
    void batchSaveFailure_recordedAsFailed() {
        ProjectImportListener listener = new ProjectImportListener(
                name -> false, list -> { throw new IllegalStateException("DB down"); });

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().getSuccessRows()).isZero();
        assertThat(listener.getImportResult().getFailedRows()).isEqualTo(1);
        assertThat(listener.getImportResult().getErrors().get(0).getErrorMessage()).contains("批量保存失败");
    }
}
