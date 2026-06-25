package com.zwinsight.contract.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.contract.domain.BizBoqItem;
import com.zwinsight.contract.dto.BoqUploadResultVO;
import com.zwinsight.contract.service.BoqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 工程量清单(BOQ)接口
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/boq")
@RequiredArgsConstructor
public class BoqController {

    private final BoqService boqService;

    /**
     * 上传并解析工程量清单
     *
     * @param contractId 施工合同ID
     * @param file       Excel 文件
     * @return 上传结果（条目数、层级数、合计金额、文件地址）
     */
    @PostMapping("/upload")
    public R<BoqUploadResultVO> uploadBoq(
            @PathVariable Long contractId,
            @RequestParam("file") MultipartFile file) {
        return R.ok(boqService.uploadBoq(contractId, file));
    }

    /**
     * 查询清单树形结构
     *
     * @param contractId 施工合同ID
     * @return 清单条目列表（按 sortOrder 排序，前端根据 parentId 构建树）
     */
    @GetMapping
    public R<List<BizBoqItem>> getBoqTree(@PathVariable Long contractId) {
        return R.ok(boqService.getBoqTree(contractId));
    }

    /**
     * 查询清单平铺列表（供产值上报使用）
     *
     * @param contractId 施工合同ID
     * @return 清单条目列表（按 level + sortOrder 排序）
     */
    @GetMapping("/flat")
    public R<List<BizBoqItem>> getBoqFlat(@PathVariable Long contractId) {
        return R.ok(boqService.getBoqFlat(contractId));
    }

    /**
     * 清除合同清单数据
     *
     * @param contractId 施工合同ID
     * @return 操作结果
     */
    @DeleteMapping
    public R<Void> deleteBoq(@PathVariable Long contractId) {
        boqService.deleteBoq(contractId);
        return R.ok();
    }
}
