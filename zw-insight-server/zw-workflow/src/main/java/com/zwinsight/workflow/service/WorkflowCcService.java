package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.domain.BizWorkflowCc;
import com.zwinsight.workflow.mapper.BizWorkflowCcMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 审批抄送服务（V2026_117）
 * <p>
 * 创建：发起人提交单据时通过 ccUserIds 参数指定抄送人，审批流程启动后调用 create()。
 * 查询：抄送人在审批中心「抄送我的」页签查看（分页 + 已读状态）。
 * 已读：点击查看详情时标记已读。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCcService {

    private final BizWorkflowCcMapper ccMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 创建抄送记录（幂等：同 processInstanceId + ccUserId 已存在则跳过）。
     *
     * @param processInstanceId 流程实例ID
     * @param businessType      业务类型
     * @param businessId        业务单据ID
     * @param ccUserIds         被抄送人用户ID列表
     * @param ccBy              抄送发起人
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(String processInstanceId, String businessType, Long businessId,
                       List<Long> ccUserIds, Long ccBy) {
        if (processInstanceId == null || businessType == null || businessId == null) {
            throw new BusinessException("抄送参数不完整：processInstanceId/businessType/businessId 必填");
        }
        if (ccUserIds == null || ccUserIds.isEmpty()) {
            return;
        }
        // 去重后再校验数量与落库，避免重复 ID 被误判为"用户不存在"
        List<Long> distinctIds = ccUserIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return;
        }
        if (distinctIds.size() > 50) {
            throw new BusinessException("抄送人不能超过50人");
        }
        // 有效性校验：必须为本租户启用用户（sys_ 表不带租户拦截，必须显式按 tenant_id 查询）
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(401, "租户上下文缺失");
        }
        List<Long> activeIds = sysUserMapper.selectActiveIdsByTenant(tenantId, distinctIds);
        if (activeIds == null || activeIds.size() != distinctIds.size()) {
            throw new BusinessException("抄送人不存在、已停用或不属于当前租户，请刷新后重试");
        }
        for (Long ccUserId : distinctIds) {
            Long exists = ccMapper.selectCount(new LambdaQueryWrapper<BizWorkflowCc>()
                    .eq(BizWorkflowCc::getProcessInstanceId, processInstanceId)
                    .eq(BizWorkflowCc::getCcUserId, ccUserId));
            if (exists != null && exists > 0) {
                continue;
            }
            BizWorkflowCc cc = new BizWorkflowCc();
            cc.setProcessInstanceId(processInstanceId);
            cc.setBusinessType(businessType);
            cc.setBusinessId(businessId);
            cc.setCcUserId(ccUserId);
            cc.setCcBy(ccBy);
            cc.setReadStatus(0);
            ccMapper.insert(cc);
        }
        log.info("审批抄送已创建, processInstanceId={}, businessType={}, businessId={}, ccUserIds={}",
                processInstanceId, businessType, businessId, distinctIds);
    }

    /**
     * 分页查询「抄送我的」列表（按创建时间降序，最新的在前）。
     */
    public PageResult<BizWorkflowCc> pageByUser(int page, int size, Integer readStatus) {
        Long userId = SecurityContextHolder.getUserId();
        Page<BizWorkflowCc> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizWorkflowCc> wrapper = new LambdaQueryWrapper<BizWorkflowCc>()
                .eq(BizWorkflowCc::getCcUserId, userId)
                .eq(readStatus != null, BizWorkflowCc::getReadStatus, readStatus)
                .orderByDesc(BizWorkflowCc::getCreatedAt);
        Page<BizWorkflowCc> result = ccMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 标记已读（点开详情时调用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        Long userId = SecurityContextHolder.getUserId();
        ccMapper.update(null, new LambdaUpdateWrapper<BizWorkflowCc>()
                .eq(BizWorkflowCc::getId, id)
                .eq(BizWorkflowCc::getCcUserId, userId)
                .eq(BizWorkflowCc::getReadStatus, 0)
                .set(BizWorkflowCc::getReadStatus, 1)
                .set(BizWorkflowCc::getReadAt, LocalDateTime.now()));
    }

    /** 未读抄送计数（首页工作台卡消费） */
    public long unreadCount() {
        Long userId = SecurityContextHolder.getUserId();
        return ccMapper.selectCount(new LambdaQueryWrapper<BizWorkflowCc>()
                .eq(BizWorkflowCc::getCcUserId, userId)
                .eq(BizWorkflowCc::getReadStatus, 0));
    }
}
