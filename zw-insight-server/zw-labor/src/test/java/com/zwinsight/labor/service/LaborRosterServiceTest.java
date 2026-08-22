package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * LaborRosterService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LaborRosterServiceTest {

    @Mock
    private BizLaborRosterMapper rosterMapper;

    @Mock
    private BizWorkOrderMapper workOrderMapper;

    @Mock
    private BizTeamMapper teamMapper;

    @InjectMocks
    private LaborRosterService service;

    private BizLaborRoster roster(Long id, Long teamId, String name) {
        BizLaborRoster r = new BizLaborRoster();
        r.setId(id);
        r.setTeamId(teamId);
        r.setWorkerName(name);
        return r;
    }

    @Nested
    @DisplayName("page 分页查询")
    class PageTests {

        @Test
        @DisplayName("按班组名筛选无匹配 - 直接返回空分页")
        void teamNameNoMatch_returnsEmpty() {
            when(teamMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            PageResult<BizLaborRoster> result = service.page(1, 10, 1L, null, null, "不存在的班组", null, null);

            assertThat(result.getRecords()).isEmpty();
            verify(rosterMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("正常分页 - 回填班组名称")
        void success_fillsTeamName() {
            Page<BizLaborRoster> page = new Page<>(1, 10);
            BizLaborRoster r1 = roster(1L, 10L, "工人甲");
            BizLaborRoster r2 = roster(2L, null, "工人乙");
            page.setRecords(new ArrayList<>(Arrays.asList(r1, r2)));
            page.setTotal(2L);
            when(rosterMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
            BizTeam team = new BizTeam();
            team.setId(10L);
            team.setTeamName("木工班");
            when(teamMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(team));

            PageResult<BizLaborRoster> result = service.page(1, 10, 1L, null, null, null, null, null);

            assertThat(result.getRecords().get(0).getTeamName()).isEqualTo("木工班");
            assertThat(result.getRecords().get(1).getTeamName()).isNull(); // teamId null 不回填
        }
    }

    @Test
    @DisplayName("save - 状态置 1 后插入")
    void save_setsActiveStatus() {
        BizLaborRoster r = roster(null, 10L, "工人丙");

        service.save(r);

        assertThat(r.getStatus()).isEqualTo(1);
        verify(rosterMapper).insert(r);
    }

    @Test
    @DisplayName("update - 记录不存在抛异常")
    void update_notFound_throws() {
        when(rosterMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(roster(99L, null, null)))
                .hasMessageContaining("花名册记录不存在");
    }

    @Nested
    @DisplayName("delete 删除")
    class DeleteTests {

        @Test
        @DisplayName("已有派工记录 - 禁止删除")
        void hasWorkOrders_throws() {
            when(workOrderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            assertThatThrownBy(() -> service.delete(1L))
                    .hasMessageContaining("已有派工记录，不可删除");
            verify(rosterMapper, never()).deleteById(any());
        }

        @Test
        @DisplayName("无派工记录 - 正常删除")
        void noWorkOrders_deletes() {
            when(workOrderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            service.delete(1L);

            verify(rosterMapper).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("进退场登记（P0 Req5）")
    class EntryExitTests {

        private BizLaborRoster withStatus(Integer status) {
            BizLaborRoster r = roster(1L, 10L, "工人甲");
            r.setStatus(status);
            return r;
        }

        @Test
        @DisplayName("进场 - 离岗转在岗：写进场日期与状态")
        void entry_offSiteToOnSite() {
            BizLaborRoster r = withStatus(0);
            when(rosterMapper.selectById(1L)).thenReturn(r);

            service.entry(1L);

            assertThat(r.getEntryDate()).isEqualTo(LocalDate.now());
            assertThat(r.getStatus()).isEqualTo(1);
            verify(rosterMapper).updateById(r);
        }

        @Test
        @DisplayName("进场 - 已在岗重复进场：拒绝")
        void entry_alreadyOnSite_rejected() {
            when(rosterMapper.selectById(1L)).thenReturn(withStatus(1));

            assertThatThrownBy(() -> service.entry(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已在岗，不能重复登记进场");
            verify(rosterMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("退场 - 在岗转离岗：写退场日期与状态")
        void exit_onSiteToOffSite() {
            BizLaborRoster r = withStatus(1);
            when(rosterMapper.selectById(1L)).thenReturn(r);

            service.exit(1L);

            assertThat(r.getExitDate()).isEqualTo(LocalDate.now());
            assertThat(r.getStatus()).isEqualTo(0);
            verify(rosterMapper).updateById(r);
        }

        @Test
        @DisplayName("退场 - 已离岗重复退场：拒绝")
        void exit_alreadyOffSite_rejected() {
            when(rosterMapper.selectById(1L)).thenReturn(withStatus(0));

            assertThatThrownBy(() -> service.exit(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已离岗，不能重复登记退场");
            verify(rosterMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("记录不存在：进场/退场均拒绝")
        void entryExit_notFound_throws() {
            when(rosterMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> service.entry(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("花名册记录不存在");
            assertThatThrownBy(() -> service.exit(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("花名册记录不存在");
        }
    }

    @Test
    @DisplayName("update - 记录存在则更新")
    void update_exists_updates() {
        BizLaborRoster existing = roster(9L, 10L, "旧名");
        when(rosterMapper.selectById(9L)).thenReturn(existing);
        BizLaborRoster updated = roster(9L, 10L, "新名");

        service.update(updated);

        verify(rosterMapper).updateById(updated);
    }

    @Test
    @DisplayName("page - 空记录与 teamId 全为空时不查班组表")
    void page_emptyOrNoTeamId_skipsTeamLookup() {
        Page<BizLaborRoster> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        when(rosterMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);
        PageResult<BizLaborRoster> result1 = service.page(1, 10, null, null, null, null, null, null);
        assertThat(result1.getRecords()).isEmpty();

        Page<BizLaborRoster> nullTeamPage = new Page<>(1, 10);
        nullTeamPage.setRecords(new ArrayList<>(Collections.singletonList(roster(3L, null, "无班组"))));
        when(rosterMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(nullTeamPage);
        PageResult<BizLaborRoster> result2 = service.page(1, 10, null, null, null, null, null, null);
        assertThat(result2.getRecords().get(0).getTeamName()).isNull();

        verify(teamMapper, never()).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("batchImport - 文件读取 IO 异常转业务异常")
    void batchImport_ioException_wrapped() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("流损坏"));

        assertThatThrownBy(() -> service.batchImport(file, 1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件读取失败");
    }
}
