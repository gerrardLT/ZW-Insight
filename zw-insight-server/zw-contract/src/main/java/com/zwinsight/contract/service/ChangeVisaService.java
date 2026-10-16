package com.zwinsight.contract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizChangeEvent;
import com.zwinsight.contract.domain.BizChangeVisa;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizChangeEventMapper;
import com.zwinsight.contract.mapper.BizChangeVisaMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 变更签证服务
 * <p>
 * 单一生效源模型（2026-10-16，F2）：变更事件表达"发生了什么"（CBS/WBS 传导），
 * 签证表达"确认了多少合同价款"。签证可选关联变更事件（{@code changeEventId}）；
 * 有关联时合同累计变更额<b>仅由本签证审批回写</b>，事件侧跳过，防止同一事项双录双计。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeVisaService {

    private final BizChangeVisaMapper changeVisaMapper;
    private final BizConstructionContractMapper contractMapper;
    private final BizChangeEventMapper changeEventMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizChangeVisa> page(int page, int size, Long projectId, Long contractId, String changeType) {
        Page<BizChangeVisa> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizChangeVisa> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizChangeVisa::getProjectId, projectId)
                .eq(contractId != null, BizChangeVisa::getContractId, contractId)
                .eq(StrUtil.isNotBlank(changeType), BizChangeVisa::getChangeType, changeType)
                .orderByDesc(BizChangeVisa::getCreatedAt);
        Page<BizChangeVisa> result = changeVisaMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增变更签证
     */
    public void save(BizChangeVisa changeVisa) {
        if (changeVisa.getVisaDate() == null) throw new BusinessException(400, "签证日期不能为空");
        if (changeVisa.getTaxRate() != null && (changeVisa.getTaxRate().signum() < 0
                || changeVisa.getTaxRate().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BusinessException(400, "税率须在0至100之间");
        }
        validateChangeEventLink(changeVisa);
        changeVisa.setStatus("DRAFT");
        changeVisaMapper.insert(changeVisa);
    }

    /**
     * 关联变更事件的业务校验：事件存在、同项目同合同、未直接回写过合同累计、未被其他有效签证占用。
     * 并发双录由 DB 唯一 guard（uk_active_visa_event）兜底为唯一键冲突。
     */
    private void validateChangeEventLink(BizChangeVisa changeVisa) {
        Long eventId = changeVisa.getChangeEventId();
        if (eventId == null) {
            return;
        }
        BizChangeEvent event = changeEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(404, "关联的变更事件不存在");
        }
        if (event.getContractId() == null || !Objects.equals(event.getContractId(), changeVisa.getContractId())
                || !Objects.equals(event.getProjectId(), changeVisa.getProjectId())) {
            throw new BusinessException(400, "变更事件与签证必须属于同一合同的同一项目，且事件须已指定合同");
        }
        if (Integer.valueOf(1).equals(event.getContractChangeWritten())) {
            throw new BusinessException(409, "该变更事件已直接回写合同累计变更额，不能再关联签证（会双计）");
        }
        if (changeVisaMapper.countActiveByChangeEvent(eventId) > 0) {
            throw new BusinessException(409, "该变更事件已被其他有效签证关联，请编辑既有签证");
        }
    }

    /**
     * 提交审批（发起流程，状态置 SUBMITTED；合同累计变更金额待审批通过后回写）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizChangeVisa changeVisa = changeVisaMapper.selectById(id);
        if (changeVisa == null) {
            throw new BusinessException("变更签证不存在");
        }
        if (!"DRAFT".equals(changeVisa.getStatus()) && !"REJECTED".equals(changeVisa.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交");
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("changeAmount", changeVisa.getChangeAmount());
        variables.put("contractId", changeVisa.getContractId());
        String processInstanceId = approvalService.startProcess(
                "CHANGE_VISA", id, "change_visa_approval", variables);

        changeVisa.setWorkflowInstanceId(processInstanceId);
        changeVisa.setStatus("SUBMITTED");
        changeVisaMapper.updateById(changeVisa);
    }

    /**
     * 审批通过回调：置 APPROVED 并回写合同累计变更金额
     * <p>幂等：状态已为 APPROVED 时直接返回（兼容审批时点改造前的存量在途单据与重复事件），
     * 避免同一笔变更重复累加合同累计变更金额。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizChangeVisa changeVisa = changeVisaMapper.selectById(id);
        if (changeVisa == null) {
            log.warn("变更签证审批通过回调：记录不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(changeVisa.getStatus()) && !"REJECTED".equals(changeVisa.getStatus())) {
            log.info("变更签证已生效，跳过重复回调, id={}", id);
            return;
        }

        if (changeVisaMapper.approvePending(id) != 1) {
            return;
        }
        // 单一生效源终检：关联事件若已直接回写过合同累计（极窄并发窗口下链接先于事件提交 commit 建立），
        // 签证不得再写，否则双计；抛异常使本次审批整体回滚，状态 CAS 一并撤销。
        if (changeVisa.getChangeEventId() != null) {
            BizChangeEvent event = changeEventMapper.selectById(changeVisa.getChangeEventId());
            if (event != null && Integer.valueOf(1).equals(event.getContractChangeWritten())) {
                throw new BusinessException(409,
                        "关联的变更事件已直接回写合同累计变更额，本签证不得重复回写，请作废后重新发起独立签证");
            }
        }
        BigDecimal changeAmount = changeVisa.getChangeAmount() != null
                ? changeVisa.getChangeAmount() : BigDecimal.ZERO;
        if (contractMapper.addCumulativeChangeAmount(changeVisa.getContractId(), changeAmount) != 1) {
            throw new BusinessException("签证关联合同不存在，审批未生效");
        }
        log.info("变更签证审批通过并生效, id={}, changeAmount={}", id, changeVisa.getChangeAmount());
    }

    /**
     * 审批驳回/撤回回调：状态置 REJECTED（合同累计变更金额未回写，无需回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizChangeVisa changeVisa = changeVisaMapper.selectById(id);
        if (changeVisa == null) {
            log.warn("变更签证驳回回调：记录不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(changeVisa.getStatus())) {
            return;
        }
        if (changeVisaMapper.transitionSubmitted(id, "REJECTED") != 1) {
            return;
        }
        changeVisa.setStatus("REJECTED");
        log.info("变更签证审批驳回, id={}", id);
    }
}
