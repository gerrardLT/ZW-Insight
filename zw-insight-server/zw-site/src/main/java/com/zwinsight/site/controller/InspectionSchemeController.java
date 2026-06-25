package com.zwinsight.site.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.site.dto.InspectionSchemeVO;
import com.zwinsight.site.dto.SchemeSnapshotDTO;
import com.zwinsight.site.mapper.InspectionSchemeQueryMapper;
import com.zwinsight.site.service.InspectionSchemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 检查方案接口
 * 提供方案列表查询、方案检查项查询、关联方案到检查记录
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InspectionSchemeController {

    private final InspectionSchemeService inspectionSchemeService;
    private final InspectionSchemeQueryMapper schemeQueryMapper;
    private final ObjectMapper objectMapper;

    /**
     * 方案列表（按 inspectionType 筛选）
     *
     * @param inspectionType 检查类型（QUALITY/SAFETY）
     * @param page           页码，默认1
     * @param size           每页大小，默认10
     * @return 分页方案列表
     */
    @GetMapping("/inspection-schemes")
    public R<PageResult<InspectionSchemeVO>> listSchemes(
            @RequestParam String inspectionType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<InspectionSchemeVO> result = inspectionSchemeService.listSchemes(inspectionType, page, size);
        return R.ok(result);
    }

    /**
     * 方案检查项列表（从方案 content 解析）
     *
     * @param id 方案ID
     * @return 检查项列表
     */
    @GetMapping("/inspection-schemes/{id}/items")
    public R<List<SchemeSnapshotDTO.ItemDTO>> getSchemeItems(@PathVariable Long id) {
        InspectionSchemeQueryMapper.SchemeRawDTO scheme = schemeQueryMapper.selectSchemeById(id);
        if (scheme == null) {
            throw new BusinessException("检查方案不存在");
        }

        List<SchemeSnapshotDTO.ItemDTO> items = parseSchemeContentToItems(scheme.getContent());
        return R.ok(items);
    }

    /**
     * 关联方案到检查记录
     *
     * @param id   检查记录ID
     * @param body 请求体，包含 schemeId
     */
    @PostMapping("/inspections/{id}/apply-scheme")
    public R<Void> applyScheme(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long schemeId = body.get("schemeId");
        if (schemeId == null) {
            throw new BusinessException("schemeId 不能为空");
        }
        inspectionSchemeService.applyScheme(id, schemeId);
        return R.ok();
    }

    /**
     * 解析方案 content JSON 为检查项列表
     */
    private List<SchemeSnapshotDTO.ItemDTO> parseSchemeContentToItems(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(content, new TypeReference<List<SchemeSnapshotDTO.ItemDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException("方案检查项内容格式异常，无法解析");
        }
    }
}
