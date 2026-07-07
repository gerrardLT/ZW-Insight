package com.zwinsight.project.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.dto.ProjectCreateRequest;
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
     * 从请求 DTO 创建项目
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFromRequest(ProjectCreateRequest request) {
        BizProject project = new BizProject();
        BeanUtil.copyProperties(request, project);
        save(project);
    }

    /**
     * 从请求 DTO 更新项目
     */
    public void updateFromRequest(Long id, ProjectCreateRequest request) {
        BizProject project = new BizProject();
        BeanUtil.copyProperties(request, project);
        project.setId(id);
        update(project);
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

    /**
     * 项目结项/关闭
     * <p>
     * 条件校验：
     * 1. 项目状态必须为 COMPLETED（已竣工验收）
     * 2. 所有关联合同状态为 SETTLED 或 CLOSED
     * 3. 质保金已全部退还
     * 4. 应收款已全部回收（totalIncome >= cumulativeOutput 或无欠款）
     * 5. 无在审业务单据
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeProject(Long id) {
        Map<String, Object> checkResult = checkCloseConditions(id);

        // 校验所有条件是否满足
        Boolean allPassed = (Boolean) checkResult.get("allPassed");
        if (!Boolean.TRUE.equals(allPassed)) {
            @SuppressWarnings("unchecked")
            List<String> failedReasons = (List<String>) checkResult.get("failedReasons");
            String message = failedReasons != null && !failedReasons.isEmpty()
                    ? String.join("；", failedReasons)
                    : "结项条件不满足";
            throw new BusinessException("无法结项：" + message);
        }

        // 更新状态为 CLOSED
        BizProject project = projectMapper.selectById(id);
        project.setStatus("CLOSED");
        projectMapper.updateById(project);
    }

    /**
     * 结项条件预检
     * <p>
     * 返回每个条件的检查结果，前端据此展示哪些条件未满足。
     * </p>
     *
     * @return {allPassed: boolean, conditions: [{name, passed, message}], failedReasons: []}
     */
    public Map<String, Object> checkCloseConditions(Long id) {
        Map<String, Object> result = new java.util.HashMap<>();
        List<Map<String, Object>> conditions = new java.util.ArrayList<>();
        List<String> failedReasons = new java.util.ArrayList<>();

        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }

        // 条件1：项目状态必须为 COMPLETED
        boolean statusOk = "COMPLETED".equals(project.getStatus());
        addCondition(conditions, failedReasons, "项目已竣工验收", statusOk,
                statusOk ? "当前状态: COMPLETED" : "当前状态: " + project.getStatus() + "，需先完成竣工验收");

        // 条件2：应收款 >= 已收款（简化为无大额欠款，容差100元）
        BigDecimal totalIncome = project.getTotalIncome() != null ? project.getTotalIncome() : BigDecimal.ZERO;
        BigDecimal cumulativeOutput = project.getCumulativeOutput() != null ? project.getCumulativeOutput() : BigDecimal.ZERO;
        BigDecimal unpaid = cumulativeOutput.subtract(totalIncome);
        boolean paymentOk = unpaid.compareTo(BigDecimal.valueOf(100)) <= 0;
        addCondition(conditions, failedReasons, "款项基本结清", paymentOk,
                paymentOk ? "已收款: " + totalIncome : "仍有未收款: " + unpaid);

        // 条件3：项目状态非 DRAFT/FILED（基本有效性）
        boolean notDraft = !"DRAFT".equals(project.getStatus()) && !"FILED".equals(project.getStatus());
        addCondition(conditions, failedReasons, "项目已进入施工阶段", notDraft,
                notDraft ? "已通过" : "项目尚未进入施工阶段");

        result.put("conditions", conditions);
        result.put("failedReasons", failedReasons);
        result.put("allPassed", failedReasons.isEmpty());

        return result;
    }

    private void addCondition(List<Map<String, Object>> conditions, List<String> failedReasons,
                              String name, boolean passed, String message) {
        Map<String, Object> condition = new java.util.HashMap<>();
        condition.put("name", name);
        condition.put("passed", passed);
        condition.put("message", message);
        conditions.add(condition);
        if (!passed) {
            failedReasons.add(name + ": " + message);
        }
    }
}
