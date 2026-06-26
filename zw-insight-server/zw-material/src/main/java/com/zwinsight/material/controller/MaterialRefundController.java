package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizMaterialRefund;
import com.zwinsight.material.dto.MaterialRefundDetailVO;
import com.zwinsight.material.service.MaterialRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 材料退款接口
 * <p>
 * 提供退款记录的分页查询和详情查询功能。
 * 退款申请由退货出库事件自动创建，无需手动创建接口。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/material/refund")
@RequiredArgsConstructor
public class MaterialRefundController {

    private final MaterialRefundService materialRefundService;

    /**
     * 分页查询退款记录
     * <p>
     * 支持按采购合同ID和时间范围筛选。
     * </p>
     *
     * @param page       页码（默认1）
     * @param size       每页大小（默认10）
     * @param contractId 采购合同ID（可选）
     * @param startTime  开始时间（可选，格式 yyyy-MM-dd HH:mm:ss）
     * @param endTime    结束时间（可选，格式 yyyy-MM-dd HH:mm:ss）
     * @return 分页退款记录列表
     */
    @GetMapping
    public R<PageResult<BizMaterialRefund>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok(materialRefundService.page(page, size, contractId, startTime, endTime));
    }

    /**
     * 获取退款记录详情（含退款明细行）
     *
     * @param id 退款申请ID
     * @return 退款记录详情
     */
    @GetMapping("/{id}")
    public R<MaterialRefundDetailVO> getDetail(@PathVariable Long id) {
        MaterialRefundDetailVO detail = materialRefundService.getDetail(id);
        if (detail == null) {
            return R.fail("退款记录不存在");
        }
        return R.ok(detail);
    }
}
