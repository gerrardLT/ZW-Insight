package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizTransferApply;
import com.zwinsight.hr.mapper.BizTransferApplyMapper;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 调动申请服务
 */
@Service
@RequiredArgsConstructor
public class TransferApplyService {

    private final BizTransferApplyMapper transferApplyMapper;
    private final SysUserMapper userMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizTransferApply> page(int page, int size) {
        Page<BizTransferApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizTransferApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizTransferApply::getCreatedAt);
        Page<BizTransferApply> result = transferApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增调动申请
     */
    public void save(BizTransferApply apply) {
        apply.setStatus("DRAFT");
        transferApplyMapper.insert(apply);
    }

    /**
     * 提交调动申请（审批→更新员工orgId/postId）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizTransferApply apply = transferApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("调动申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", apply.getUserName());
        variables.put("toOrgId", apply.getToOrgId());
        variables.put("toPostId", apply.getToPostId());
        String processInstanceId = approvalService.startProcess(
                "TRANSFER_APPLY", id, "transfer_apply_approval", variables);

        apply.setStatus("APPROVED");
        transferApplyMapper.updateById(apply);

        // 更新员工部门和岗位
        SysUser user = userMapper.selectById(apply.getUserId());
        if (user != null) {
            user.setOrgId(apply.getToOrgId());
            user.setPostId(apply.getToPostId());
            userMapper.updateById(user);
        }
    }
}
