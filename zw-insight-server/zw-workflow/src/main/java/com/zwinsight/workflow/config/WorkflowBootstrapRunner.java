package com.zwinsight.workflow.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.workflow.domain.WfProcessDef;
import com.zwinsight.workflow.mapper.WfProcessDefMapper;
import com.zwinsight.workflow.service.ProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 工作流预设流程自动初始化 Runner
 * ponytail: 系统启动时扫描 classpath 下全部内置 processes/*.bpmn20.xml，
 * 若默认租户(1)缺失对应流程定义，则自动部署入库，实现开箱即用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowBootstrapRunner implements ApplicationRunner {

    private static final Long DEFAULT_TENANT_ID = 1L;
    private final ProcessDefinitionService processDefinitionService;
    private final WfProcessDefMapper processDefMapper;

    @Override
    public void run(ApplicationArguments args) {
        // 启动线程无租户上下文：不设置时 wf_process_def 查询被注入 tenant_id=0，
        // 且插入被 MetaObjectHandler 拒绝，整笔部署事务回滚，租户 1 一条流程定义都落不下来
        SecurityContextHolder.setTenantId(DEFAULT_TENANT_ID);
        SecurityContextHolder.markSystemTask();
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:processes/*.bpmn20.xml");
            if (resources == null || resources.length == 0) {
                return;
            }

            int deployedCount = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".bpmn20.xml")) {
                    continue;
                }
                String processKey = filename.replace(".bpmn20.xml", "");

                // 检查租户 1 是否已存在该流程定义记录
                Long count = processDefMapper.selectCount(new LambdaQueryWrapper<WfProcessDef>()
                        .eq(WfProcessDef::getTenantId, DEFAULT_TENANT_ID)
                        .eq(WfProcessDef::getProcessKey, processKey));

                if (count == null || count == 0) {
                    try {
                        byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
                        processDefinitionService.deploy(processKey, DEFAULT_TENANT_ID, bytes);
                        deployedCount++;
                        log.info("【工作流自检】默认租户(1)预设缺失流程成功: {}", processKey);
                    } catch (Exception ex) {
                        // 带异常对象：getMessage() 为 null 时旧日志只剩“失败: null”，无法定位根因
                        log.warn("【工作流自检】部署流程 {} 失败: {}", processKey, ex.toString(), ex);
                    }
                }
            }

            if (deployedCount > 0) {
                log.info("【工作流自检】本次启动共自动预设部署 {} 个缺失流程", deployedCount);
            }
        } catch (Exception e) {
            log.error("【工作流自检】扫描预设流程发生异常", e);
        } finally {
            SecurityContextHolder.clear();
        }
    }
}
