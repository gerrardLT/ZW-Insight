package com.zwinsight.tender.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import org.junit.jupiter.api.BeforeEach;
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
 * TenderRegisterService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TenderRegisterServiceTest {

    @Mock private BizTenderRegisterMapper registerMapper;
    @Mock private BizProjectMapper projectMapper;

    @InjectMocks
    private TenderRegisterService tenderRegisterService;

    private BizTenderRegister sampleRegister;

    @BeforeEach
    void setUp() {
        sampleRegister = new BizTenderRegister();
        sampleRegister.setId(1L);
        sampleRegister.setProjectId(100L);
        sampleRegister.setStatus("REGISTERED");
    }

    @Test
    @DisplayName("新增投标登记：状态初始化为 REGISTERED + 项目状态回写为 TENDERING")
    void testSave_initializesStatusAndUpdatesProject() {
        BizTenderRegister register = new BizTenderRegister();
        register.setProjectId(100L);

        BizProject project = new BizProject();
        project.setId(100L);
        project.setStatus("FILED");
        when(projectMapper.selectById(100L)).thenReturn(project);
        when(registerMapper.insert(any(BizTenderRegister.class))).thenReturn(1);

        tenderRegisterService.save(register);

        assertThat(register.getStatus()).isEqualTo("REGISTERED");
        verify(registerMapper).insert(register);
        verify(projectMapper).updateById(argThat(p -> "TENDERING".equals(p.getStatus())));
    }

    @Test
    @DisplayName("新增投标登记：项目不存在时跳过项目状态回写")
    void testSave_projectNotFound_skipsProjectUpdate() {
        BizTenderRegister register = new BizTenderRegister();
        register.setProjectId(999L);
        when(projectMapper.selectById(999L)).thenReturn(null);
        when(registerMapper.insert(any(BizTenderRegister.class))).thenReturn(1);

        tenderRegisterService.save(register);

        verify(projectMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("查询详情：存在则返回")
    void testGetById_found() {
        when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

        BizTenderRegister result = tenderRegisterService.getById(1L);

        assertThat(result.getStatus()).isEqualTo("REGISTERED");
    }

    @Test
    @DisplayName("查询详情：不存在抛异常")
    void testGetById_notFound() {
        when(registerMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenderRegisterService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投标登记不存在");
    }

    @Test
    @DisplayName("更新：正常更新")
    void testUpdate_success() {
        when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

        tenderRegisterService.update(sampleRegister);

        verify(registerMapper).updateById(sampleRegister);
    }

    @Test
    @DisplayName("更新：不存在抛异常")
    void testUpdate_notFound() {
        BizTenderRegister register = new BizTenderRegister();
        register.setId(999L);
        when(registerMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenderRegisterService.update(register))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投标登记不存在");
    }

    @Test
    @DisplayName("删除：REGISTERED 状态可删")
    void testDelete_registeredAllowed() {
        when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

        tenderRegisterService.delete(1L);

        verify(registerMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除：非 REGISTERED 拒绝")
    void testDelete_nonRegisteredRejected() {
        sampleRegister.setStatus("SUBMITTED");
        when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

        assertThatThrownBy(() -> tenderRegisterService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅报名状态可删除");
    }

    @Test
    @DisplayName("提交审批：状态变更为 SUBMITTED")
    void testSubmit_statusChanges() {
        when(registerMapper.selectById(1L)).thenReturn(sampleRegister);

        tenderRegisterService.submit(1L);

        verify(registerMapper).updateById(argThat(r -> "SUBMITTED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("提交审批：不存在抛异常")
    void testSubmit_notFound() {
        when(registerMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> tenderRegisterService.submit(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投标登记不存在");
    }
}
