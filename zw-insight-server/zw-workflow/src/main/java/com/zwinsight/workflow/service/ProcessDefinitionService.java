package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfProcessDef;
import com.zwinsight.workflow.mapper.WfProcessDefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.GraphicInfo;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        // enableDuplicateFiltering：BPMN 内容未变化时复用已有部署，不产生新版本
        // （2026-08-13 事故：CI 每轮全量跑 deploy-bpmn.sh 重复部署 23 个流程，
        // 累积 ACT_RE_DEPLOYMENT 992 行 / ACT_GE_BYTEARRAY 11万+行）
        Deployment deployment = repositoryService.createDeployment()
                .addBytes(resourceName, bpmnBytes)
                .name(name)
                .tenantId(String.valueOf(tenantId))
                .enableDuplicateFiltering()
                .deploy();

        // 获取流程定义
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        if (processDefinition == null) {
            throw new BusinessException("流程部署失败，未找到流程定义");
        }

        // 保存到扩展表：同一流程定义 ID 幂等 upsert（重复部署被过滤时
        // deployment 指向已有部署，避免 wf_process_def 重复插入）
        WfProcessDef existing = processDefMapper.selectOne(new LambdaQueryWrapper<WfProcessDef>()
                .eq(WfProcessDef::getProcessDefinitionId, processDefinition.getId())
                .last("LIMIT 1"));
        WfProcessDef processDef = existing != null ? existing : new WfProcessDef();
        processDef.setProcessKey(processDefinition.getKey());
        // 优先用 BPMN 内 <process name> 的中文名；脚本/自检传入的 name 常是流程标识
        String bpmnName = processDefinition.getName();
        processDef.setProcessName(bpmnName != null && !bpmnName.isBlank() ? bpmnName : name);
        processDef.setResourceName(resourceName);
        processDef.setDeploymentId(deployment.getId());
        processDef.setProcessDefinitionId(processDefinition.getId());
        processDef.setVersionNum(processDefinition.getVersion());
        processDef.setStatus(1);
        if (existing != null) {
            processDefMapper.updateById(processDef);
        } else {
            processDefMapper.insert(processDef);
        }

        log.info("流程部署成功, deploymentId={}, processKey={}, version={}",
                deployment.getId(), processDefinition.getKey(), processDefinition.getVersion());

        return processDef;
    }

    /**
     * 按租户列出流程定义（去重，只保留每个流程标识的最新版本）
     *
     * @param tenantId 租户ID
     * @return 流程定义列表（最新版本）
     */
    public List<WfProcessDef> listByTenant(Long tenantId) {
        LambdaQueryWrapper<WfProcessDef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessDef::getTenantId, tenantId)
                .orderByDesc(WfProcessDef::getVersionNum)
                .orderByDesc(WfProcessDef::getCreatedAt);
        List<WfProcessDef> all = processDefMapper.selectList(wrapper);
        Map<String, WfProcessDef> latestMap = new LinkedHashMap<>();
        for (WfProcessDef def : all) {
            if (def.getProcessKey() != null) {
                latestMap.putIfAbsent(def.getProcessKey(), def);
            }
        }
        return new ArrayList<>(latestMap.values());
    }

    /**
     * 获取流程图（PNG格式）
     *
     * @param idOrDefinitionId 流程扩展表ID或Flowable流程定义ID
     * @return 流程图输入流
     */
    public InputStream getProcessImage(String idOrDefinitionId) {
        String processDefinitionId = idOrDefinitionId;
        // 兼容传入 wf_process_def 表雪花 ID 的场景
        if (idOrDefinitionId != null && idOrDefinitionId.matches("^\\d+$")) {
            WfProcessDef wfDef = processDefMapper.selectById(Long.parseLong(idOrDefinitionId));
            if (wfDef != null && wfDef.getProcessDefinitionId() != null) {
                processDefinitionId = wfDef.getProcessDefinitionId();
            }
        }

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();

        if (processDefinition == null) {
            throw new BusinessException("流程定义不存在: " + processDefinitionId);
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        ensureGraphicalInformation(bpmnModel);

        try {
            ProcessDiagramGenerator diagramGenerator = new DefaultProcessDiagramGenerator();
            return diagramGenerator.generateDiagram(
                    bpmnModel,
                    "png",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    "Arial",
                    "Arial",
                    "Arial",
                    null,
                    1.0,
                    true
            );
        } catch (Exception ex) {
            log.warn("Flowable生成流程图异常，启用轻量绘图器兜底: {}", ex.getMessage());
            return generateFallbackDiagram(bpmnModel);
        }
    }

    /**
     * 为缺少 BPMNDI 坐标信息的模型自动补全水平流向坐标
     */
    private void ensureGraphicalInformation(BpmnModel bpmnModel) {
        if (bpmnModel == null) return;
        Map<String, GraphicInfo> locationMap = bpmnModel.getLocationMap();
        if (locationMap != null && !locationMap.isEmpty()) {
            return;
        }

        org.flowable.bpmn.model.Process process = bpmnModel.getMainProcess();
        if (process == null) return;

        double currentX = 50.0;
        double currentY = 100.0;
        double spacing = 80.0;

        List<FlowNode> nodes = new ArrayList<>();
        List<SequenceFlow> flows = new ArrayList<>();
        for (FlowElement el : process.getFlowElements()) {
            if (el instanceof FlowNode node) {
                nodes.add(node);
            } else if (el instanceof SequenceFlow flow) {
                flows.add(flow);
            }
        }

        nodes.sort((a, b) -> {
            if (a instanceof StartEvent) return -1;
            if (b instanceof StartEvent) return 1;
            if (a instanceof EndEvent) return 1;
            if (b instanceof EndEvent) return -1;
            return a.getId().compareTo(b.getId());
        });

        Map<String, GraphicInfo> nodeBounds = new HashMap<>();
        for (FlowNode node : nodes) {
            double width = (node instanceof StartEvent || node instanceof EndEvent) ? 36.0 : (node instanceof Gateway ? 50.0 : 100.0);
            double height = (node instanceof StartEvent || node instanceof EndEvent) ? 36.0 : (node instanceof Gateway ? 50.0 : 80.0);
            double yOffset = currentY - (height / 2.0);

            GraphicInfo gi = new GraphicInfo(currentX, yOffset, height, width);
            gi.setElement(node);
            bpmnModel.addGraphicInfo(node.getId(), gi);
            nodeBounds.put(node.getId(), gi);

            currentX += width + spacing;
        }

        for (SequenceFlow flow : flows) {
            GraphicInfo src = nodeBounds.get(flow.getSourceRef());
            GraphicInfo tgt = nodeBounds.get(flow.getTargetRef());
            if (src != null && tgt != null) {
                List<GraphicInfo> waypoints = new ArrayList<>();
                waypoints.add(new GraphicInfo(src.getX() + src.getWidth(), src.getY() + src.getHeight() / 2.0));
                waypoints.add(new GraphicInfo(tgt.getX(), tgt.getY() + tgt.getHeight() / 2.0));
                bpmnModel.addFlowGraphicInfoList(flow.getId(), waypoints);
            }
        }
    }

    /**
     * 当图形引擎不可用时生成简洁优雅的轻量流程图 PNG
     */
    private InputStream generateFallbackDiagram(BpmnModel bpmnModel) {
        try {
            org.flowable.bpmn.model.Process process = bpmnModel != null ? bpmnModel.getMainProcess() : null;
            List<String> nodeNames = new ArrayList<>();
            if (process != null) {
                for (FlowElement el : process.getFlowElements()) {
                    if (el instanceof FlowNode node) {
                        String name = node.getName();
                        if (name == null || name.isBlank()) {
                            name = (node instanceof StartEvent) ? "开始" : ((node instanceof EndEvent) ? "结束" : node.getId());
                        }
                        nodeNames.add(name);
                    }
                }
            }
            if (nodeNames.isEmpty()) {
                nodeNames.add("开始");
                nodeNames.add("审批");
                nodeNames.add("结束");
            }

            int nodeWidth = 120;
            int nodeHeight = 50;
            int gap = 50;
            int totalWidth = Math.max(600, 60 + nodeNames.size() * (nodeWidth + gap));
            int totalHeight = 160;

            BufferedImage image = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, totalWidth, totalHeight);

            int x = 40;
            int y = 55;
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
            g.setFont(font);

            for (int i = 0; i < nodeNames.size(); i++) {
                String name = nodeNames.get(i);
                boolean isEdgeNode = (i == 0 || i == nodeNames.size() - 1);

                g.setColor(isEdgeNode ? new Color(235, 245, 255) : Color.WHITE);
                g.fillRoundRect(x, y, nodeWidth, nodeHeight, 10, 10);
                g.setColor(isEdgeNode ? new Color(64, 158, 255) : new Color(200, 205, 215));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(x, y, nodeWidth, nodeHeight, 10, 10);

                g.setColor(new Color(48, 49, 51));
                FontMetrics fm = g.getFontMetrics();
                int textX = x + (nodeWidth - fm.stringWidth(name)) / 2;
                int textY = y + (nodeHeight + fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(name, Math.max(x + 5, textX), textY);

                if (i < nodeNames.size() - 1) {
                    int lineStartX = x + nodeWidth;
                    int lineEndX = lineStartX + gap;
                    int lineY = y + nodeHeight / 2;
                    g.setColor(new Color(160, 170, 185));
                    g.drawLine(lineStartX, lineY, lineEndX, lineY);
                    g.fillPolygon(
                            new int[]{lineEndX, lineEndX - 8, lineEndX - 8},
                            new int[]{lineY, lineY - 5, lineY + 5},
                            3
                    );
                }
                x += nodeWidth + gap;
            }
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (Exception e) {
            log.error("兜底流程图生成失败", e);
            byte[] emptyPng = new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                    0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89
            };
            return new ByteArrayInputStream(emptyPng);
        }
    }

    /**
     * 存量补名：流程名称曾被存成流程标识（英文），按 Flowable 中 BPMN 的中文名回填。幂等。
     *
     * @return 回填条数
     */
    public int backfillProcessNames() {
        int fixed = 0;
        List<WfProcessDef> all = processDefMapper.selectList(new LambdaQueryWrapper<WfProcessDef>()
                .apply("process_name = process_key"));
        for (WfProcessDef def : all) {
            if (def.getProcessDefinitionId() == null) continue;
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(def.getProcessDefinitionId()).singleResult();
            String n = pd == null ? null : pd.getName();
            if (n != null && !n.isBlank() && !n.equals(def.getProcessName())) {
                def.setProcessName(n);
                processDefMapper.updateById(def);
                fixed++;
            }
        }
        return fixed;
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

    /**
     * 获取流程定义 BPMN XML（含图形坐标）
     *
     * @param idOrDefinitionId 流程扩展表ID或Flowable流程定义ID
     * @return BPMN XML 字符串
     */
    public String getProcessXml(String idOrDefinitionId) {
        String processDefinitionId = idOrDefinitionId;
        if (idOrDefinitionId != null && idOrDefinitionId.matches("^\\d+$")) {
            WfProcessDef wfDef = processDefMapper.selectById(Long.parseLong(idOrDefinitionId));
            if (wfDef != null && wfDef.getProcessDefinitionId() != null) {
                processDefinitionId = wfDef.getProcessDefinitionId();
            }
        }

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();

        if (processDefinition == null) {
            throw new BusinessException("流程定义不存在: " + processDefinitionId);
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        ensureGraphicalInformation(bpmnModel);

        BpmnXMLConverter converter = new BpmnXMLConverter();
        byte[] xmlBytes = converter.convertToXML(bpmnModel, "UTF-8");
        return new String(xmlBytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
