package com.zwinsight.tender.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.tender.domain.BizOpenBidRecord;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizOpenBidRecordMapper;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OpenBidRecordService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class OpenBidRecordServiceTest {

    @Mock private BizOpenBidRecordMapper openBidRecordMapper;
    @Mock private BizTenderRegisterMapper registerMapper;
    @Mock private BizProjectMapper projectMapper;

    @InjectMocks
    private OpenBidRecordService openBidRecordService;

    @Test
    @DisplayName("新增开标记录（中标）：投标登记状态→WON，项目状态→WON")
    void testSave_won_updatesBothRegisterAndProject() {
        BizOpenBidRecord record = new BizOpenBidRecord();
        record.setRegisterId(10L);
        record.setProjectId(100L);
        record.setIsWon(1);

        BizTenderRegister register = new BizTenderRegister();
        register.setId(10L);
        register.setStatus("SUBMITTED");

        BizProject project = new BizProject();
        project.setId(100L);
        project.setStatus("TENDERING");

        when(openBidRecordMapper.insert(any(BizOpenBidRecord.class))).thenReturn(1);
        when(registerMapper.selectById(10L)).thenReturn(register);
        when(projectMapper.selectById(100L)).thenReturn(project);

        openBidRecordService.save(record);

        verify(registerMapper).updateById(argThat(r -> "WON".equals(r.getStatus())));
        verify(projectMapper).updateById(argThat(p -> "WON".equals(p.getStatus())));
    }

    @Test
    @DisplayName("新增开标记录（未中标）：投标登记状态→LOST，项目不变")
    void testSave_lost_updatesRegisterOnly() {
        BizOpenBidRecord record = new BizOpenBidRecord();
        record.setRegisterId(10L);
        record.setProjectId(100L);
        record.setIsWon(0);

        BizTenderRegister register = new BizTenderRegister();
        register.setId(10L);

        when(openBidRecordMapper.insert(any(BizOpenBidRecord.class))).thenReturn(1);
        when(registerMapper.selectById(10L)).thenReturn(register);

        openBidRecordService.save(record);

        verify(registerMapper).updateById(argThat(r -> "LOST".equals(r.getStatus())));
        verify(projectMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("新增开标记录：投标登记不存在抛异常")
    void testSave_registerNotFound_throws() {
        BizOpenBidRecord record = new BizOpenBidRecord();
        record.setRegisterId(999L);

        when(openBidRecordMapper.insert(any(BizOpenBidRecord.class))).thenReturn(1);
        when(registerMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> openBidRecordService.save(record))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投标登记不存在");
    }
}
