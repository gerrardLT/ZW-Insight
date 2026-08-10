package com.zwinsight.workflow.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfProcessDef;
import com.zwinsight.workflow.mapper.WfProcessDefMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ProcessDefinitionService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ProcessDefinitionServiceTest {

    @Mock private RepositoryService repositoryService;
    @Mock private WfProcessDefMapper processDefMapper;

    @InjectMocks
    private ProcessDefinitionService processDefinitionService;

    @Test
    @DisplayName("按租户列出流程定义：委托 mapper.selectList")
    void testListByTenant_delegates() {
        when(processDefMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<WfProcessDef> result = processDefinitionService.listByTenant(1L);

        assertThat(result).isEmpty();
        verify(processDefMapper).selectList(any());
    }

    @Test
    @DisplayName("历史版本列表：委托 mapper.selectList")
    void testGetHistoryVersions_delegates() {
        when(processDefMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<WfProcessDef> result = processDefinitionService.getHistoryVersions("budget_approval", 1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("获取流程图：流程定义不存在抛异常")
    void testGetProcessImage_notFound() {
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionId(anyString())).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> processDefinitionService.getProcessImage("pd-x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程定义不存在");
    }

    @Test
    @DisplayName("部署流程：部署后未找到流程定义抛异常")
    void testDeploy_processDefinitionNotFound() {
        DeploymentBuilder builder = mock(DeploymentBuilder.class);
        Deployment deployment = mock(Deployment.class);
        when(repositoryService.createDeployment()).thenReturn(builder);
        when(builder.addBytes(anyString(), any(byte[].class))).thenReturn(builder);
        when(builder.name(anyString())).thenReturn(builder);
        when(builder.tenantId(anyString())).thenReturn(builder);
        when(builder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("dep-1");

        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.deploymentId("dep-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        assertThatThrownBy(() ->
                processDefinitionService.deploy("test", 1L, "<bpmn/>".getBytes()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程部署失败");
        verify(processDefMapper, never()).insert(any());
    }

    @Test
    @DisplayName("部署流程：成功时保存扩展表")
    void testDeploy_success_savesExt() {
        DeploymentBuilder builder = mock(DeploymentBuilder.class);
        Deployment deployment = mock(Deployment.class);
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(repositoryService.createDeployment()).thenReturn(builder);
        when(builder.addBytes(anyString(), any(byte[].class))).thenReturn(builder);
        when(builder.name(anyString())).thenReturn(builder);
        when(builder.tenantId(anyString())).thenReturn(builder);
        when(builder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("dep-1");

        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.deploymentId("dep-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);
        when(pd.getKey()).thenReturn("budget_approval");
        when(pd.getId()).thenReturn("pd-1");
        when(pd.getVersion()).thenReturn(1);

        WfProcessDef result = processDefinitionService.deploy("预算审批", 1L, "<bpmn/>".getBytes());

        assertThat(result.getProcessKey()).isEqualTo("budget_approval");
        assertThat(result.getStatus()).isEqualTo(1);
        verify(processDefMapper).insert(any(WfProcessDef.class));
    }
}
