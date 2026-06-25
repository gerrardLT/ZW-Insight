package com.zwinsight.project.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 项目服务
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final BizProjectMapper projectMapper;
    private final SerialNumberService serialNumberService;
    private final ProjectMemberService memberService;

    /**
     * 分页查询
     */
    public PageResult<BizProject> page(int page, int size, String projectName, String status, String projectType) {
        Page<BizProject> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(projectName), BizProject::getProjectName, projectName)
                .eq(StrUtil.isNotBlank(status), BizProject::getStatus, status)
                .eq(StrUtil.isNotBlank(projectType), BizProject::getProjectType, projectType)
                .orderByDesc(BizProject::getCreatedAt);
        Page<BizProject> result = projectMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BizProject getById(Long id) {
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        return project;
    }

    /**
     * 新增项目（自动生成编号）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizProject project) {
        // 自动生成项目编号
        String projectCode = serialNumberService.generate("PROJECT");
        project.setProjectCode(projectCode);
        project.setStatus("DRAFT");

        // 初始化金额字段
        if (project.getBudgetAmount() == null) {
            project.setBudgetAmount(BigDecimal.ZERO);
        }
        if (project.getContractAmount() == null) {
            project.setContractAmount(BigDecimal.ZERO);
        }
        if (project.getCumulativeOutput() == null) {
            project.setCumulativeOutput(BigDecimal.ZERO);
        }
        if (project.getSettlementAmount() == null) {
            project.setSettlementAmount(BigDecimal.ZERO);
        }
        if (project.getTotalIncome() == null) {
            project.setTotalIncome(BigDecimal.ZERO);
        }
        if (project.getTotalExpense() == null) {
            project.setTotalExpense(BigDecimal.ZERO);
        }
        if (project.getTotalOtherPayment() == null) {
            project.setTotalOtherPayment(BigDecimal.ZERO);
        }

        projectMapper.insert(project);

        // 自动将创建人添加为项目经理
        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId != null) {
            memberService.addCreatorAsProjectManager(project.getId(), currentUserId, null);
        }
    }

    /**
     * 更新项目（仅DRAFT状态可改）
     */
    public void update(BizProject project) {
        BizProject existing = projectMapper.selectById(project.getId());
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        projectMapper.updateById(project);
    }

    /**
     * 删除项目（仅DRAFT状态可删）
     */
    public void delete(Long id) {
        BizProject existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        projectMapper.deleteById(id);
    }

    /**
     * 批量删除（仅DRAFT状态可删）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    /**
     * 提交项目（DRAFT → FILED）
     */
    public void submit(Long id) {
        BizProject existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        existing.setStatus("FILED");
        projectMapper.updateById(existing);
    }

    /**
     * 更新项目状态
     */
    public void updateStatus(Long id, String newStatus) {
        BizProject existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        existing.setStatus(newStatus);
        projectMapper.updateById(existing);
    }
}
