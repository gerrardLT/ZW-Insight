package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfProcessDef;
import com.zwinsight.workflow.mapper.WfProcessDefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * 流程定义管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionService {

    private final RepositoryService repositoryService;
    private final WfProcessDefMapper processDefMapper;

    /**
     * 部署流程
     *
     * @param name      流程名称
     * @param tenantId  租户ID
     * @param bpmnBytes BPMN文件内容
     * @return 流程定义扩展信息
     */
    @Transactional(rollbackFor = Exception.class)
    public WfProcessDef deploy(String name, Long tenantId, byte[] bpmnBytes) {
        String resourceName = name + ".bpmn20.xml";

        // 部署流程到Flowable
        Deployment deployment = repositoryService.createDeployment()
                .addBytes(resourceName, bpmnBytes)
                .name(name)
                .tenantId(String.valueOf(tenantId))
                .deploy();

        // 获取流程定义
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        if (processDefinition == null) {
            throw new BusinessException("流程部署失败，未找到流程定义");
        }

        // 保存到扩展表
        WfProcessDef processDef = new WfProcessDef();
        processDef.setProcessKey(processDefinition.getKey());
        processDef.setProcessName(name);
        processDef.setResourceName(resourceName);
        processDef.setDeploymentId(deployment.getId());
        processDef.setProcessDefinitionId(processDefinition.getId());
        processDef.setVersionNum(processDefinition.getVersion());
        processDef.setStatus(1);
        processDefMapper.insert(processDef);

        log.info("流程部署成功, deploymentId={}, processKey={}, version={}",
                deployment.getId(), processDefinition.getKey(), processDefinition.getVersion());

        return processDef;
    }

    /**
     * 按租户列出流程定义
     *
     * @param tenantId 租户ID
     * @return 流程定义列表
     */
    public List<WfProcessDef> listByTenant(Long tenantId) {
        LambdaQueryWrapper<WfProcessDef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessDef::getTenantId, tenantId)
                .orderByDesc(WfProcessDef::getCreatedAt);
        return processDefMapper.selectList(wrapper);
    }

    /**
     * 获取流程图（PNG格式）
     *
     * @param processDefinitionId 流程定义ID
     * @return 流程图输入流
     */
    public InputStream getProcessImage(String processDefinitionId) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();

        if (processDefinition == null) {
            throw new BusinessException("流程定义不存在: " + processDefinitionId);
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        ProcessDiagramGenerator diagramGenerator = new DefaultProcessDiagramGenerator();

        return diagramGenerator.generateDiagram(
                bpmnModel,
                "png",
                Collections.emptyList(),
                Collections.emptyList(),
                "宋体",
                "宋体",
                "宋体",
                null,
                1.0,
                true
        );
    }

    /**
     * 获取历史版本列表
     *
     * @param processKey 流程标识
     * @param tenantId   租户ID
     * @return 版本列表
     */
    public List<WfProcessDef> getHistoryVersions(String processKey, Long tenantId) {
        LambdaQueryWrapper<WfProcessDef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessDef::getProcessKey, processKey)
                .eq(WfProcessDef::getTenantId, tenantId)
                .orderByDesc(WfProcessDef::getVersionNum);
        return processDefMapper.selectList(wrapper);
    }
}
