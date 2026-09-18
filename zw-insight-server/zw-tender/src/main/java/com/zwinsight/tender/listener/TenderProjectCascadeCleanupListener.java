package com.zwinsight.tender.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.domain.BizOpenBidRecord;
import com.zwinsight.tender.domain.BizTenderFee;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizDepositApplyMapper;
import com.zwinsight.tender.mapper.BizOpenBidRecordMapper;
import com.zwinsight.tender.mapper.BizTenderFeeMapper;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-tender 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理本模块含 {@code project_id} 列的 4 张表。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 *
 * <p><b>与投标报名前置校验的关系</b>：{@code ProjectService.delete} 在发布事件前会执行
 * {@code countTenderRegisters(id) > 0 → 抛异常} 的硬拦截，因此正常路径下
 * biz_tender_register 永远走不到本监听器。Round 7 取证与该推断一致：该表存量 8 条、
 * 孤儿 0 条（同批次产生的 biz_deposit_apply / biz_open_bid_record 却各有 3 条孤儿，
 * 正因为它们不受这条硬拦截保护）。此处仍然清理它，是为了在该前置校验被放宽时
 * 不留下潜在孤儿源——代价仅是一条影响 0 行的 UPDATE。</p>
 *
 * <p><b>不纳入级联</b>：biz_deposit_return 以 {@code deposit_apply_id} 关联；
 * biz_tender_task 按招标任务自身组织；biz_company_certificate /
 * biz_person_certificate 为公司与个人资质档案，均无 {@code project_id} 列。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：本模块遗留孤儿——投标保证金 3 条、
 * 开标记录 3 条（均指向物理不存在的项目）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenderProjectCascadeCleanupListener {

    private final BizTenderRegisterMapper tenderRegisterMapper;
    private final BizDepositApplyMapper depositApplyMapper;
    private final BizTenderFeeMapper tenderFeeMapper;
    private final BizOpenBidRecordMapper openBidRecordMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int tenderRegisters = tenderRegisterMapper.delete(
                new QueryWrapper<BizTenderRegister>().eq("project_id", projectId));
        int depositApplies = depositApplyMapper.delete(
                new QueryWrapper<BizDepositApply>().eq("project_id", projectId));
        int tenderFees = tenderFeeMapper.delete(
                new QueryWrapper<BizTenderFee>().eq("project_id", projectId));
        int openBidRecords = openBidRecordMapper.delete(
                new QueryWrapper<BizOpenBidRecord>().eq("project_id", projectId));

        log.info("项目删除级联清理[tender]完成, projectId={}, 投标报名={} 保证金={} 标书费={} 开标记录={}",
                projectId, tenderRegisters, depositApplies, tenderFees, openBidRecords);
    }
}
