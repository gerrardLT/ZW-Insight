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
}
