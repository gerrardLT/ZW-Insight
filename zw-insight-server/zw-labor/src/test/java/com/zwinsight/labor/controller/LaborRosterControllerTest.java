package com.zwinsight.labor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.service.LaborRosterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LaborRosterController 单元测试（委托逻辑与参数透传）
 */
@ExtendWith(MockitoExtension.class)
class LaborRosterControllerTest {

    @Mock
    private LaborRosterService rosterService;

    @InjectMocks
    private LaborRosterController controller;

    @Test
    @DisplayName("page - 参数透传含进退场状态筛选")
    void page_delegatesWithEntryStatus() {
        when(rosterService.page(1, 10, 2L, 3L, "甲", "木工班", "木工", "ON_SITE"))
                .thenReturn(PageResult.of(new Page<>(1, 10)));

        R<PageResult<BizLaborRoster>> result =
                controller.page(1, 10, 2L, 3L, "甲", "木工班", "木工", "ON_SITE");

        assertThat(result.getCode()).isEqualTo(200);
        verify(rosterService).page(1, 10, 2L, 3L, "甲", "木工班", "木工", "ON_SITE");
    }

    @Test
    @DisplayName("save - 委托服务保存")
    void save_delegates() {
        BizLaborRoster roster = new BizLaborRoster();

        R<Void> result = controller.save(roster);

        assertThat(result.getCode()).isEqualTo(200);
        verify(rosterService).save(roster);
    }

    @Test
    @DisplayName("update - 路径参数 id 回填后委托更新")
    void update_setsIdFromPath() {
        BizLaborRoster roster = new BizLaborRoster();

        controller.update(8L, roster);

        assertThat(roster.getId()).isEqualTo(8L);
        verify(rosterService).update(roster);
    }

    @Test
    @DisplayName("delete - 委托服务删除")
    void delete_delegates() {
        controller.delete(5L);

        verify(rosterService).delete(5L);
    }

    @Test
    @DisplayName("entry/exit - 进退场登记委托（P0 Req5）")
    void entryExit_delegates() {
        controller.entry(6L);
        controller.exit(7L);

        verify(rosterService).entry(6L);
        verify(rosterService).exit(7L);
    }

    @Test
    @DisplayName("batchImport - 返回导入条数与提示文案")
    void batchImport_returnsCount() {
        when(rosterService.batchImport(any(), eq(1L), eq(10L))).thenReturn(3);

        R<Integer> result = controller.batchImport(
                new MockMultipartFile("file", "roster.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1}),
                1L, 10L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo(3);
        assertThat(result.getMessage()).contains("成功导入3条");
    }
}
