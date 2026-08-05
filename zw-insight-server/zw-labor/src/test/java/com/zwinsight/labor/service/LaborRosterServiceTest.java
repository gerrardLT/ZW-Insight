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

            PageResult<BizLaborRoster> result = service.page(1, 10, 1L, null, null, "不存在的班组", null);

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

            PageResult<BizLaborRoster> result = service.page(1, 10, 1L, null, null, null, null);

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
