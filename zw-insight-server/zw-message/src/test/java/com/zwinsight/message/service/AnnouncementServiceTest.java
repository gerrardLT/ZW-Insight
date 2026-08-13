package com.zwinsight.message.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.message.domain.MsgAnnouncement;
import com.zwinsight.message.mapper.MsgAnnouncementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock private MsgAnnouncementMapper announcementMapper;

    @InjectMocks
    private AnnouncementService announcementService;

    @Test
    @DisplayName("新增公告：默认DRAFT状态")
    void testSave() {
        MsgAnnouncement ann = new MsgAnnouncement();
        ann.setTitle("系统升级公告");
        when(announcementMapper.insert(any())).thenReturn(1);

        announcementService.save(ann);

        assertThat(ann.getStatus()).isEqualTo("DRAFT");
        verify(announcementMapper).insert(ann);
    }

    @Test
    @DisplayName("发布公告：状态改为PUBLISHED")
    void testPublish() {
        MsgAnnouncement ann = new MsgAnnouncement();
        ann.setId(1L);
        ann.setStatus("DRAFT");
        when(announcementMapper.selectById(1L)).thenReturn(ann);

        announcementService.publish(1L);

        assertThat(ann.getStatus()).isEqualTo("PUBLISHED");
        assertThat(ann.getPublishTime()).isNotNull();
    }

    @Test
    @DisplayName("发布公告：不存在抛异常")
    void testPublish_notFound() {
        when(announcementMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> announcementService.publish(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("公告不存在");
    }

    @Test
    @DisplayName("撤回公告：状态改为REVOKED")
    void testRevoke() {
        MsgAnnouncement ann = new MsgAnnouncement();
        ann.setId(1L);
        ann.setStatus("PUBLISHED");
        when(announcementMapper.selectById(1L)).thenReturn(ann);

        announcementService.revoke(1L);

        assertThat(ann.getStatus()).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(announcementMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> announcementService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("公告不存在");
    }

    @Test
    @DisplayName("状态机守卫（P2）：已发布不可编辑/删除/重复发布，草稿不可撤回")
    void testStatusGuards() {
        MsgAnnouncement published = new MsgAnnouncement();
        published.setId(1L);
        published.setStatus("PUBLISHED");
        when(announcementMapper.selectById(1L)).thenReturn(published);

        MsgAnnouncement upd = new MsgAnnouncement();
        upd.setId(1L);
        assertThatThrownBy(() -> announcementService.update(upd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿/已撤回公告可编辑");
        assertThatThrownBy(() -> announcementService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先撤回");
        assertThatThrownBy(() -> announcementService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿/已撤回公告可发布");

        MsgAnnouncement draft = new MsgAnnouncement();
        draft.setId(2L);
        draft.setStatus("DRAFT");
        when(announcementMapper.selectById(2L)).thenReturn(draft);
        assertThatThrownBy(() -> announcementService.revoke(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已发布公告可撤回");

        verify(announcementMapper, never()).updateById(any());
        verify(announcementMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("编辑公告：草稿可编辑且 status/publishTime 剥离防篡改（P2）")
    void testUpdate_draftGuardsAndStripping() {
        MsgAnnouncement existing = new MsgAnnouncement();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(announcementMapper.selectById(1L)).thenReturn(existing);

        MsgAnnouncement upd = new MsgAnnouncement();
        upd.setId(1L);
        upd.setTitle("新标题");
        upd.setStatus("PUBLISHED"); // 恶意携带

        announcementService.update(upd);

        verify(announcementMapper).updateById(argThat(a ->
                a.getStatus() == null && a.getPublishTime() == null));
    }
}
