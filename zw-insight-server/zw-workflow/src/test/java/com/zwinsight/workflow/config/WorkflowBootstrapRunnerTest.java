package com.zwinsight.workflow.config;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.workflow.domain.WfProcessDef;
import com.zwinsight.workflow.mapper.WfProcessDefMapper;
import com.zwinsight.workflow.service.ProcessDefinitionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * WorkflowBootstrapRunner 单元测试
 * <p>回归：启动线程无租户上下文时，wf_process_def 查询被注入 tenant_id=0、插入被拒绝，
 * 整笔部署回滚，默认租户的内置流程一条都落不下来（日志仅见“失败: null”）。</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowBootstrapRunnerTest {

    @Mock private ProcessDefinitionService processDefinitionService;
    @Mock private WfProcessDefMapper processDefMapper;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("启动自检：查询与部署均带默认租户(1)上下文，结束后清理线程上下文")
    void run_deploysUnderDefaultTenantContext() {
        List<Long> tenantSeenOnCount = new ArrayList<>();
        List<Long> tenantSeenOnDeploy = new ArrayList<>();
        List<String> deployedKeys = new ArrayList<>();
        when(processDefMapper.selectCount(any())).thenAnswer(inv -> {
            tenantSeenOnCount.add(SecurityContextHolder.getTenantId());
            return 0L;
        });
        when(processDefinitionService.deploy(any(String.class), any(Long.class), any(byte[].class)))
                .thenAnswer(inv -> {
                    deployedKeys.add(inv.getArgument(0));
                    tenantSeenOnDeploy.add(SecurityContextHolder.getTenantId());
                    return new WfProcessDef();
                });

        new WorkflowBootstrapRunner(processDefinitionService, processDefMapper).run(null);

        assertThat(deployedKeys).contains("bootstrap_probe");
        assertThat(tenantSeenOnCount).isNotEmpty().containsOnly(1L);
        assertThat(tenantSeenOnDeploy).isNotEmpty().containsOnly(1L);
        assertThat(SecurityContextHolder.getTenantId()).isNull();
        assertThat(SecurityContextHolder.isSystemTask()).isFalse();
    }

    @Test
    @DisplayName("启动自检：单个流程部署失败不影响其余流程，且上下文仍被清理")
    void run_deployFailureDoesNotAbortAndStillClearsContext() {
        when(processDefMapper.selectCount(any())).thenReturn(0L);
        when(processDefinitionService.deploy(any(String.class), any(Long.class), any(byte[].class)))
                .thenThrow(new IllegalStateException());

        new WorkflowBootstrapRunner(processDefinitionService, processDefMapper).run(null);

        assertThat(SecurityContextHolder.getTenantId()).isNull();
    }
}
