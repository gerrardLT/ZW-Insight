package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizEntertainmentDetail;
import com.zwinsight.finance.domain.BizReimbursementDetail;
import com.zwinsight.finance.mapper.BizEntertainmentDetailMapper;
import com.zwinsight.finance.mapper.BizReimbursementDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报销明细服务（V2026_60 激活孤儿表 biz_reimbursement_detail）
 * <p>
 * 职责：报销单的费用科目明细级联保存与校验、招待费专项明细强约束、招待费分析聚合。
 * </p>
 * <p>校验纪律（对齐项目"不静默"铁律）：
 * 明细合计必须等于主表 totalAmount（容差 0.01）；科目编码必须存在且为支出向；
 * 招待费明细行必须携带 {@link BizEntertainmentDetail}（对象单位/事由/日期必填），
 * 任一不满足抛 BusinessException 而非丢弃该维度。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReimbursementDetailService {

    private final BizReimbursementDetailMapper detailMapper;
    private final BizEntertainmentDetailMapper entertainmentMapper;
    private final FundCategoryService fundCategoryService;

    /** 金额一致性容差（与资金计划明细校验同口径） */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    /**
     * 级联保存报销明细（替换式：先逻辑删除旧明细再插入，与资金计划明细同惯例）。
     *
     * @param sourceType      报销来源（PROJECT/PERSONAL）
     * @param reimbursementId 报销单ID
     * @param projectId       项目ID（个人报销可空）
     * @param details         明细列表（null=不维护明细，向后兼容存量单据；空列表=清空明细）
     * @param totalAmount     主表报销总额（明细非空时合计必须与之一致）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDetails(String sourceType, Long reimbursementId, Long projectId,
                            List<BizReimbursementDetail> details, BigDecimal totalAmount) {
        if (sourceType == null
                || !(BizReimbursementDetail.SOURCE_PROJECT.equals(sourceType)
                || BizReimbursementDetail.SOURCE_PERSONAL.equals(sourceType))) {
            throw new BusinessException("报销来源不合法，需为 PROJECT 或 PERSONAL");
        }
        if (reimbursementId == null) {
            throw new BusinessException("报销单ID不能为空");
        }
        if (details == null) {
            return; // 明细可选：存量单据与简易报销可不维护明细
        }
        validateDetails(details, totalAmount);

        // 替换式保存：先清旧明细（含其招待费专项明细），再逐行插入
        List<BizReimbursementDetail> old = detailMapper.selectList(
                new LambdaQueryWrapper<BizReimbursementDetail>()
                        .eq(BizReimbursementDetail::getSourceType, sourceType)
                        .eq(BizReimbursementDetail::getReimbursementId, reimbursementId));
        for (BizReimbursementDetail o : old) {
            entertainmentMapper.delete(new LambdaQueryWrapper<BizEntertainmentDetail>()
                    .eq(BizEntertainmentDetail::getReimbursementDetailId, o.getId()));
        }
        detailMapper.delete(new LambdaQueryWrapper<BizReimbursementDetail>()
                .eq(BizReimbursementDetail::getSourceType, sourceType)
                .eq(BizReimbursementDetail::getReimbursementId, reimbursementId));

        for (BizReimbursementDetail detail : details) {
            detail.setId(null);
            detail.setSourceType(sourceType);
            detail.setReimbursementId(reimbursementId);
            detailMapper.insert(detail);

            if (BizReimbursementDetail.CATEGORY_ENTERTAINMENT.equals(detail.getCategoryCode())) {
                BizEntertainmentDetail entertainment = detail.getEntertainment();
                entertainment.setReimbursementDetailId(detail.getId());
                if (entertainment.getProjectId() == null) {
                    entertainment.setProjectId(projectId);
                }
                entertainmentMapper.insert(entertainment);
            }
        }
        log.info("报销明细已保存, sourceType={}, reimbursementId={}, 明细行数={}",
                sourceType, reimbursementId, details.size());
    }

    /**
     * 查询报销明细（含招待费专项明细回填）
     */
    public List<BizReimbursementDetail> listDetails(String sourceType, Long reimbursementId) {
        List<BizReimbursementDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<BizReimbursementDetail>()
                        .eq(BizReimbursementDetail::getSourceType, sourceType)
                        .eq(BizReimbursementDetail::getReimbursementId, reimbursementId)
                        .orderByAsc(BizReimbursementDetail::getId));
        for (BizReimbursementDetail detail : details) {
            if (BizReimbursementDetail.CATEGORY_ENTERTAINMENT.equals(detail.getCategoryCode())) {
                detail.setEntertainment(entertainmentMapper.selectOne(
                        new LambdaQueryWrapper<BizEntertainmentDetail>()
                                .eq(BizEntertainmentDetail::getReimbursementDetailId, detail.getId())));
            }
        }
        return details;
    }

    /**
     * 招待费分析（docs/资金流转流程.md §6.3 分析维度 + §11 预警项）。
     * <p>老板视角"默认看异常"：anomalies 汇总全部预警项笔数，正常报销不罗列。</p>
     *
     * @param projectId  项目ID（可空=全部项目）
     * @param sourceType 报销来源（可空=合计）
     * @return {summary: 聚合指标, byHandler: 责任人累计, anomalies: 异常项清单}
     */
    public Map<String, Object> analyzeEntertainment(Long projectId, String sourceType) {
        Map<String, Object> summary = entertainmentMapper.analyzeEntertainment(projectId, sourceType);
        if (summary == null) {
            summary = new HashMap<>();
        }
        List<Map<String, Object>> byHandler = entertainmentMapper.sumByHandler(projectId);

        // 异常项清单（§6.3：无事由/无对象/无审批/票据异常 + §11：同人同日多笔）
        List<Map<String, Object>> anomalies = new ArrayList<>();
        addAnomaly(anomalies, "无招待事由", toLong(summary.get("noReasonCount")),
                "补齐事由后方可入账，无事由招待不得报销");
        addAnomaly(anomalies, "无招待对象", toLong(summary.get("noHostCount")),
                "补齐对方单位/人员，无对象招待视为不合规");
        addAnomaly(anomalies, "无事前审批", toLong(summary.get("noPreApprovalCount")),
                "推行事前申请，事后补批需说明原因");
        addAnomaly(anomalies, "发票不完整", toLong(summary.get("noInvoiceCount")),
                "补齐发票与消费凭证");
        addAnomaly(anomalies, "同人同日多笔", nullToZero(entertainmentMapper.countSameHandlerSameDay(projectId)),
                "核查是否拆单规避审批");

        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary);
        result.put("byHandler", byHandler);
        result.put("anomalies", anomalies);
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 明细校验：金额正数、科目有效且支出向、招待费专项必填、合计与主表一致。
     */
    private void validateDetails(List<BizReimbursementDetail> details, BigDecimal totalAmount) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BizReimbursementDetail detail : details) {
            if (detail.getAmount() == null || detail.getAmount().signum() <= 0) {
                throw new BusinessException("报销明细金额必须大于0，费用类型：" + detail.getExpenseType());
            }
            if (detail.getCategoryCode() == null || detail.getCategoryCode().isBlank()) {
                throw new BusinessException("报销明细必须选择费用科目（费用类型：" + detail.getExpenseType() + "）");
            }
            // 科目有效性与方向校验（不存在/非支出向由 getByCode 抛异常，不静默丢弃）
            fundCategoryService.getByCode(detail.getCategoryCode(), "EXPENSE");

            if (BizReimbursementDetail.CATEGORY_ENTERTAINMENT.equals(detail.getCategoryCode())) {
                validateEntertainment(detail.getEntertainment());
            }
            sum = sum.add(detail.getAmount());
        }
        BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        if (details.isEmpty()) {
            return; // 空列表=清空明细，不做合计校验
        }
        if (sum.subtract(total).abs().compareTo(TOLERANCE) > 0) {
            throw new BusinessException("报销明细合计 " + sum + " 与报销总额 " + total + " 不一致");
        }
    }

    /**
     * 招待费专项字段强校验（§6.3 最少字段中的必填项）
     */
    private void validateEntertainment(BizEntertainmentDetail entertainment) {
        if (entertainment == null) {
            throw new BusinessException("招待费明细必须填写专项信息（招待对象/事由/日期）");
        }
        if (entertainment.getEntertainDate() == null) {
            throw new BusinessException("招待费必须填写招待日期");
        }
        if (entertainment.getHostCompany() == null || entertainment.getHostCompany().isBlank()) {
            throw new BusinessException("招待费必须填写招待对象单位");
        }
        if (entertainment.getEntertainReason() == null || entertainment.getEntertainReason().isBlank()) {
            throw new BusinessException("招待费必须填写招待事由");
        }
    }

    private void addAnomaly(List<Map<String, Object>> anomalies, String name, long count, String suggestion) {
        if (count <= 0) {
            return; // 正常项不罗列（§8.1「正常费用隐藏，异常费用冒出来」）
        }
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("count", count);
        item.put("suggestion", suggestion);
        anomalies.add(item);
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    /**
     * COUNT/SUM 聚合结果防 null（SQL 已用 COALESCE，此处额外防自动拆箱 NPE）
     */
    private long nullToZero(Long value) {
        return value != null ? value : 0L;
    }
}
