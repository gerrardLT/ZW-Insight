package com.zwinsight.site.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizInspectionDetail;
import com.zwinsight.site.dto.InspectionDetailDTO;
import com.zwinsight.site.dto.InspectionSchemeVO;
import com.zwinsight.site.dto.SchemeSnapshotDTO;
import com.zwinsight.site.mapper.BizInspectionDetailMapper;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.InspectionSchemeQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 检查方案关联服务
 * 负责方案列表查询、方案关联检查记录、生成方案快照
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionSchemeService {

    private final InspectionSchemeQueryMapper schemeQueryMapper;
    private final BizInspectionMapper inspectionMapper;
    private final BizInspectionDetailMapper inspectionDetailMapper;
    private final ObjectMapper objectMapper;

    /** 每页最大条数 */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 按检查类型筛选已启用方案列表（分页）
     *
     * @param inspectionType 检查类型（QUALITY/SAFETY）
     * @param page           页码（从1开始）
     * @param size           每页大小（最大50）
     * @return 分页结果
     */
    public PageResult<InspectionSchemeVO> listSchemes(String inspectionType, int page, int size) {
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        int offset = (page - 1) * effectiveSize;

        List<InspectionSchemeVO> records = schemeQueryMapper.listByType(inspectionType, offset, effectiveSize);
        long total = schemeQueryMapper.countByType(inspectionType);

        PageResult<InspectionSchemeVO> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(effectiveSize);
        result.setPages(total == 0 ? 0 : (total + effectiveSize - 1) / effectiveSize);
        return result;
    }

    /**
     * 关联方案到检查记录并生成快照
     * <p>
     * 操作流程：
     * 1. 校验检查记录存在
     * 2. 校验方案存在且 status=1（已启用）
     * 3. 读取方案检查项列表（从 content JSON 解析）
     * 4. 构建方案快照 JSON
     * 5. 清除现有检查明细
     * 6. 填充新方案检查项到 biz_inspection_detail
     * 7. 更新 biz_inspection 的 scheme_id 和 scheme_snapshot
     *
     * @param inspectionId 检查记录ID
     * @param schemeId     方案ID
     */
    @Transactional
    public void applyScheme(Long inspectionId, Long schemeId) {
        // 1. 校验检查记录存在
        BizInspection inspection = inspectionMapper.selectById(inspectionId);
        if (inspection == null) {
            throw new BusinessException("检查记录不存在");
        }

        // 2. 校验方案存在且已启用
        InspectionSchemeQueryMapper.SchemeRawDTO scheme = schemeQueryMapper.selectSchemeById(schemeId);
        if (scheme == null) {
            throw new BusinessException("检查方案不存在");
        }
        if (scheme.getStatus() == null || scheme.getStatus() != 1) {
            throw new BusinessException("检查方案已停用，无法关联");
        }

        // 3. 从 content JSON 解析检查项列表
        List<SchemeContentItem> schemeItems = parseSchemeContent(scheme.getContent());

        // 4. 构建方案快照 JSON
        SchemeSnapshotDTO snapshot = buildSnapshot(schemeId, scheme.getSchemeName(), schemeItems);
        String snapshotJson = serializeSnapshot(snapshot);

        // 5. 清除现有检查明细（逻辑删除）
        inspectionDetailMapper.deleteByInspectionId(inspectionId);

        // 6. 填充新方案检查项到 biz_inspection_detail
        List<BizInspectionDetail> details = buildInspectionDetails(inspectionId, inspection.getTenantId(), schemeItems);
        for (BizInspectionDetail detail : details) {
            inspectionDetailMapper.insert(detail);
        }

        // 7. 更新检查记录的 scheme_id 和 scheme_snapshot
        inspection.setSchemeId(schemeId);
        inspection.setSchemeSnapshot(snapshotJson);
        inspectionMapper.updateById(inspection);

        log.info("检查记录[{}]已关联方案[{}], 填充检查项{}条", inspectionId, schemeId, details.size());
    }

    // ======================== 检查明细编辑与手动填写 ========================

    /** 手动填写检查项最大条数 */
    private static final int MAX_MANUAL_DETAILS = 100;

    /**
     * 编辑已填充的检查项
     * <p>
     * 允许修改: checkStandard, checkMethod, checkResult, remark
     * 不允许修改: itemName（由方案带入不可改名）
     *
     * @param detailId   检查明细ID
     * @param updateData 需要更新的字段
     */
    @Transactional
    public void updateDetail(Long detailId, BizInspectionDetail updateData) {
        // 1. 校验 detailId 存在
        BizInspectionDetail existing = inspectionDetailMapper.selectById(detailId);
        if (existing == null) {
            throw new BusinessException("检查明细不存在");
        }

        // 2. 仅更新允许修改的字段
        existing.setCheckStandard(updateData.getCheckStandard());
        existing.setCheckMethod(updateData.getCheckMethod());
        existing.setCheckResult(updateData.getCheckResult());
        existing.setRemark(updateData.getRemark());

        inspectionDetailMapper.updateById(existing);
        log.info("检查明细[{}]已更新", detailId);
    }

    /**
     * 删除不适用的检查项（逻辑删除）
     *
     * @param detailId 检查明细ID
     */
    @Transactional
    public void deleteDetail(Long detailId) {
        // 校验 detailId 存在
        BizInspectionDetail existing = inspectionDetailMapper.selectById(detailId);
        if (existing == null) {
            throw new BusinessException("检查明细不存在");
        }

        inspectionDetailMapper.deleteById(detailId);
        log.info("检查明细[{}]已逻辑删除", detailId);
    }

    /**
     * 手动填写检查项（未选择方案时）
     * <p>
     * 校验规则：
     * - details.size() <= 100
     * - 每条: itemName ≤ 200 字符，checkStandard ≤ 500 字符
     * - 当前检查记录未关联方案（scheme_id == null）才允许新增方案外检查项
     * - 如果已关联方案，不允许新增方案外检查项（R7.3）
     *
     * @param inspectionId 检查记录ID
     * @param details      手动填写的检查项列表
     */
    @Transactional
    public void saveManualDetails(Long inspectionId, List<InspectionDetailDTO> details) {
        // 1. 校验检查记录存在
        BizInspection inspection = inspectionMapper.selectById(inspectionId);
        if (inspection == null) {
            throw new BusinessException("检查记录不存在");
        }

        // 2. R7.3: 已关联方案，不允许新增方案外检查项
        if (inspection.getSchemeId() != null) {
            throw new BusinessException("当前检查记录已关联方案，不允许新增方案外检查项");
        }

        // 3. 校验条数上限
        if (details == null || details.isEmpty()) {
            throw new BusinessException("检查项不能为空");
        }
        if (details.size() > MAX_MANUAL_DETAILS) {
            throw new BusinessException("手动填写检查项不能超过" + MAX_MANUAL_DETAILS + "条");
        }

        // 4. 逐条校验字段长度
        for (int i = 0; i < details.size(); i++) {
            InspectionDetailDTO dto = details.get(i);
            if (dto.getItemName() == null || dto.getItemName().isBlank()) {
                throw new BusinessException("第" + (i + 1) + "条检查项目名称不能为空");
            }
            if (dto.getItemName().length() > 200) {
                throw new BusinessException("第" + (i + 1) + "条项目名称不超过200字符");
            }
            if (dto.getCheckStandard() != null && dto.getCheckStandard().length() > 500) {
                throw new BusinessException("第" + (i + 1) + "条检查标准不超过500字符");
            }
        }

        // 5. 清除现有检查明细（逻辑删除）
        inspectionDetailMapper.deleteByInspectionId(inspectionId);

        // 6. 批量插入手动填写的检查项
        int sortOrder = 1;
        for (InspectionDetailDTO dto : details) {
            BizInspectionDetail detail = new BizInspectionDetail();
            detail.setInspectionId(inspectionId);
            detail.setItemName(dto.getItemName());
            detail.setCheckStandard(dto.getCheckStandard());
            detail.setCheckMethod(dto.getCheckMethod());
            detail.setCheckResult("NOT_CHECKED");
            detail.setSortOrder(sortOrder++);
            inspectionDetailMapper.insert(detail);
        }

        log.info("检查记录[{}]手动填写检查项{}条", inspectionId, details.size());
    }

    // ======================== 方案内容解析辅助方法 ========================

    /**
     * 解析方案 content JSON，获取检查项列表
     * <p>
     * content 为 JSON 数组格式: [{"itemName":"xxx", "checkStandard":"xxx", "checkMethod":"xxx"}, ...]
     */
    private List<SchemeContentItem> parseSchemeContent(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(content, new TypeReference<List<SchemeContentItem>>() {});
        } catch (JsonProcessingException e) {
            log.warn("方案检查项内容解析失败: {}", e.getMessage());
            throw new BusinessException("方案检查项内容格式异常，无法解析");
        }
    }

    /**
     * 构建方案快照 DTO
     */
    private SchemeSnapshotDTO buildSnapshot(Long schemeId, String schemeName, List<SchemeContentItem> items) {
        SchemeSnapshotDTO snapshot = new SchemeSnapshotDTO();
        snapshot.setSchemeId(schemeId);
        snapshot.setSchemeName(schemeName);
        snapshot.setItems(items.stream().map(item -> {
            SchemeSnapshotDTO.ItemDTO dto = new SchemeSnapshotDTO.ItemDTO();
            dto.setItemName(item.getItemName());
            dto.setCheckStandard(item.getCheckStandard());
            dto.setCheckMethod(item.getCheckMethod());
            return dto;
        }).collect(Collectors.toList()));
        return snapshot;
    }

    /**
     * 序列化快照为 JSON 字符串
     */
    private String serializeSnapshot(SchemeSnapshotDTO snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("方案快照序列化失败", e);
            throw new BusinessException("方案快照序列化失败");
        }
    }

    /**
     * 根据方案检查项构建 biz_inspection_detail 列表
     */
    private List<BizInspectionDetail> buildInspectionDetails(Long inspectionId, Long tenantId,
                                                             List<SchemeContentItem> schemeItems) {
        List<BizInspectionDetail> details = new ArrayList<>();
        int sortOrder = 1;
        for (SchemeContentItem item : schemeItems) {
            BizInspectionDetail detail = new BizInspectionDetail();
            detail.setInspectionId(inspectionId);
            detail.setItemName(item.getItemName());
            detail.setCheckStandard(item.getCheckStandard());
            detail.setCheckMethod(item.getCheckMethod());
            detail.setCheckResult("NOT_CHECKED");
            detail.setSortOrder(sortOrder++);
            details.add(detail);
        }
        return details;
    }

    /**
     * 方案 content JSON 中单个检查项的数据结构
     */
    private static class SchemeContentItem {
        private String itemName;
        private String checkStandard;
        private String checkMethod;

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getCheckStandard() { return checkStandard; }
        public void setCheckStandard(String checkStandard) { this.checkStandard = checkStandard; }
        public String getCheckMethod() { return checkMethod; }
        public void setCheckMethod(String checkMethod) { this.checkMethod = checkMethod; }
    }
}
