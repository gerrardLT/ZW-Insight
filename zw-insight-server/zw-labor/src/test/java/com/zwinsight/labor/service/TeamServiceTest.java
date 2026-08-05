package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * TeamService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private BizTeamMapper teamMapper;

    @Mock
    private BizLaborRosterMapper rosterMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private TeamService service;

    private BizTeam team(Long id, String name) {
        BizTeam t = new BizTeam();
        t.setId(id);
        t.setProjectId(1L);
        t.setTeamName(name);
        return t;
    }

    @Test
    @DisplayName("page - 回填成员数（按花名册 team_id 聚合）")
    void page_fillsMemberCount() {
        Page<BizTeam> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>(Arrays.asList(team(10L, "木工班"), team(20L, "钢筋班"))));
        page.setTotal(2L);
        when(teamMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        // 木工班 2 人、钢筋班 0 人；另含一条 teamId 为 null 的脏数据（应被过滤）
        BizLaborRoster r1 = new BizLaborRoster();
        r1.setTeamId(10L);
        BizLaborRoster r2 = new BizLaborRoster();
        r2.setTeamId(10L);
        BizLaborRoster r3 = new BizLaborRoster();
        r3.setTeamId(null);
        when(rosterMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(r1, r2, r3));
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        PageResult<BizTeam> result = service.page(1, 10, 1L, null, null);

        assertThat(result.getRecords().get(0).getMemberCount()).isEqualTo(2);
        assertThat(result.getRecords().get(1).getMemberCount()).isZero();
    }

    @Test
    @DisplayName("save - 状态置 1")
    void save_setsActive() {
        BizTeam t = team(null, "新班组");

        service.save(t);

        assertThat(t.getStatus()).isEqualTo(1);
        verify(teamMapper).insert(t);
    }

    @Test
    @DisplayName("update - 班组不存在抛异常")
    void update_notFound_throws() {
        when(teamMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(team(99L, null)))
                .hasMessageContaining("班组不存在");
        verify(teamMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("delete - 委托 mapper 删除（引用校验由 @ReferenceCheck 切面负责）")
    void delete_delegates() {
        service.delete(1L);

        verify(teamMapper).deleteById(1L);
    }

    @Test
    @DisplayName("updateStatus - 不存在抛异常；正常更新状态")
    void updateStatus_variants() {
        when(teamMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.updateStatus(99L, 0))
                .hasMessageContaining("班组不存在");

        BizTeam t = team(1L, "木工班");
        when(teamMapper.selectById(1L)).thenReturn(t);
        service.updateStatus(1L, 0);
        assertThat(t.getStatus()).isZero();
        verify(teamMapper).updateById(t);
    }
}
