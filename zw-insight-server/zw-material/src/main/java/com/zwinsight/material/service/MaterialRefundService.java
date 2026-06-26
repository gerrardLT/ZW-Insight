package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.mapper.BizExpenseContractMapper;
import com.zwinsight.material.domain.BizMaterialRefund;
import com.zwinsight.material.domain.BizMaterialRefundDetail;
import com.zwinsight.material.dto.MaterialRefundDetailVO;
import com.zwinsight.material.event.MaterialReturnCreatedEvent;
import com.zwinsight.material.mapper.BizMaterialRefundDetailMapper;
import com.zwinsight.material.mapper.BizMaterialRefundMapper;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 材料退款服务
 * <p>
 * 提供退款申请的创建、审批提交、审批通过回写、查询等功能。
 * 退款申请由退货出库事件自动触发生成，创建后自动提交 Flowable 审批流程。
 * 审批通过后自动扣减采购合同的累计付款金额。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialRefundService {

    private static final String BUSINESS_TYPE = "MATERIAL_REFUND";
    private static final String PROCESS_KEY = "material_refund_approval";

    private final BizMaterialRefundMapper refundMapper;
    private final BizMaterialRefundDetailMapper refundDetailMapper;
    private final ApprovalService approvalService;
    private final BizExpenseContractMapper expenseContractMapper;

    /**
     * 根据退货出库事件创建退款申请，并自动提交 Flowable 审批流程
     * <p>
     * 按入库单价计算退款金额：sum(detail.quantity × detail.inboundUnitPrice)
     * </p>
     *
     * @param event 退货出库事件
     * @return 退款申请ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createRefundFromReturn(MaterialReturnCreatedEvent event) {
        // 1. 创建退款申请主表
        BizMaterialRefund refund = new BizMaterialRefund();
        refund.setOutboundId(event.getOutboundId());
        refund.setContractId(event.getContractId());
        refund.setProjectId(event.getProjectId());
        refund.setStatus("DRAFT");

        // 2. 计算退款总金额并保存明细
        BigDecimal refundAmount = BigDecimal.ZERO;
        for (MaterialReturnCreatedEvent.OutboundDetailItem detail : event.getDetails()) {
            BigDecimal lineAmount = detail.getQuantity()
                    .multiply(detail.getInboundUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            refundAmount = refundAmount.add(lineAmount);
        }
        refund.setRefundAmount(refundAmount);
        refundMapper.insert(refund);

        // 3. 保存退款明细行
        for (MaterialReturnCreatedEvent.OutboundDetailItem detail : event.getDetails()) {
            BizMaterialRefundDetail refundDetail = new BizMaterialRefundDetail();
            refundDetail.setRefundId(refund.getId());
            refundDetail.setMaterialName(detail.getMaterialName());
            refundDetail.setSpecification(detail.getSpecification());
            refundDetail.setUnit(detail.getUnit());
            refundDetail.setQuantity(detail.getQuantity());
            refundDetail.setUnitPrice(detail.getInboundUnitPrice());
            BigDecimal lineAmount = detail.getQuantity()
                    .multiply(detail.getInboundUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            refundDetail.setAmount(lineAmount);
            refundDetailMapper.insert(refundDetail);
        }

        // 4. 提交 Flowable 审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("amount", refundAmount);
        variables.put("contractId", event.getContractId());
        variables.put("projectId", event.getProjectId());

        String processInstanceId = approvalService.startProcess(
                BUSINESS_TYPE, refund.getId(), PROCESS_KEY, variables);

        // 5. 更新退款申请状态为待审批，记录流程实例ID
        refund.setStatus("PENDING");
        refund.setWorkflowInstanceId(processInstanceId);
        refundMapper.updateById(refund);

        log.info("退款申请创建并提交审批完成: refundId={}, outboundId={}, contractId={}, refundAmount={}, processInstanceId={}",
                refund.getId(), event.getOutboundId(), event.getContractId(), refundAmount, processInstanceId);

        return refund.getId();
    }

    /**
     * 审批通过回调 — 通过 Spring Event 监听 ApprovalCompleteEvent
     * <p>
     * 当 businessType 为 MATERIAL_REFUND 且结果为 APPROVED 时：
     * 1. 更新退款申请状态为 APPROVED
     * 2. 扣减采购合同的累计付款金额
     * </p>
     */
    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onRefundApproved(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        if (!"APPROVED".equals(event.getResult())) {
            return;
        }

        Long refundId = event.getBusinessId();
        BizMaterialRefund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            log.warn("退款审批通过回调：退款申请不存在, id={}", refundId);
            return;
        }

        // 1. 更新退款申请状态为已审批
        refund.setStatus("APPROVED");
        refundMapper.updateById(refund);

        // 2. 扣减采购合同的累计付款金额
        expenseContractMapper.deductPaidAmount(refund.getContractId(), refund.getRefundAmount());

        log.info("退款审批通过，合同已付款金额已扣减: refundId={}, contractId={}, refundAmount={}",
                refundId, refund.getContractId(), refund.getRefundAmount());
    }

    /**
     * 分页查询退款记录
     *
     * @param page       页码
     * @param size       每页大小
     * @param contractId 采购合同ID（可选筛选）
     * @param startTime  开始时间（可选筛选）
     * @param endTime    结束时间（可选筛选）
     * @return 分页结果
     */
    public PageResult<BizMaterialRefund> page(int page, int size, Long contractId,
                                              LocalDateTime startTime, LocalDateTime endTime) {
        Page<BizMaterialRefund> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<BizMaterialRefund> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(contractId != null, BizMaterialRefund::getContractId, contractId)
                .ge(startTime != null, BizMaterialRefund::getCreatedAt, startTime)
                .le(endTime != null, BizMaterialRefund::getCreatedAt, endTime)
                .orderByDesc(BizMaterialRefund::getCreatedAt);

        Page<BizMaterialRefund> result = refundMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 获取退款记录详情（含退款明细行）
     *
     * @param id 退款申请ID
     * @return 退款记录详情VO
     */
    public MaterialRefundDetailVO getDetail(Long id) {
        BizMaterialRefund refund = refundMapper.selectById(id);
        if (refund == null) {
            return null;
        }

        // 查询退款明细
        LambdaQueryWrapper<BizMaterialRefundDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizMaterialRefundDetail::getRefundId, id);
        List<BizMaterialRefundDetail> details = refundDetailMapper.selectList(detailWrapper);

        // 构建详情VO
        MaterialRefundDetailVO vo = new MaterialRefundDetailVO();
        vo.setId(refund.getId());
        vo.setProjectId(refund.getProjectId());
        vo.setOutboundId(refund.getOutboundId());
        vo.setContractId(refund.getContractId());
        vo.setRefundCode(refund.getRefundCode());
        vo.setRefundAmount(refund.getRefundAmount());
        vo.setRefundReason(refund.getRefundReason());
        vo.setStatus(refund.getStatus());
        vo.setWorkflowInstanceId(refund.getWorkflowInstanceId());
        vo.setCreatedBy(refund.getCreatedBy());
        vo.setCreatedAt(refund.getCreatedAt());

        List<MaterialRefundDetailVO.RefundDetailItem> detailItems = details.stream()
                .map(d -> {
                    MaterialRefundDetailVO.RefundDetailItem item = new MaterialRefundDetailVO.RefundDetailItem();
                    item.setId(d.getId());
                    item.setMaterialName(d.getMaterialName());
                    item.setSpecification(d.getSpecification());
                    item.setUnit(d.getUnit());
                    item.setQuantity(d.getQuantity());
                    item.setUnitPrice(d.getUnitPrice());
                    item.setAmount(d.getAmount());
                    return item;
                })
                .collect(Collectors.toList());

        vo.setDetails(detailItems);
        return vo;
    }
}
