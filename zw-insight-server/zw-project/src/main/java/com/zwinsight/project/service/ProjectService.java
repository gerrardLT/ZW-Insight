package com.zwinsight.project.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectChangeLog;
import com.zwinsight.project.domain.BizProjectStatusLog;
import com.zwinsight.project.domain.enums.ProjectEvent;
import com.zwinsight.project.mapper.BizProjectChangeLogMapper;
import com.zwinsight.project.mapper.BizProjectStatusLogMapper;
import com.zwinsight.project.domain.dto.ProjectCreateRequest;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.project.mapper.BizProjectWbsNodeMapper;
import com.zwinsight.project.mapper.SysUserProjectMapper;
import com.zwinsight.project.vo.ProjectPortfolioVO;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 项目服务
 * <p>
 * P1-M1 深度优化（V2026_81，蓝图 docs/deep-opt/01-project.md）：
 * <ul>
 *   <li>状态流转统一走 {@link ProjectStateMachine}（I1 边表 / I2 开工准入 / I3 周期回写 + 大事记日志）</li>
 *   <li>立项可配审批（B1：sys_config project_filing_approval_enabled，默认直置位兼容 L4）</li>
 *   <li>撤回（WITHDRAW）/ 落标归档（LOSE_BID）/ 暂停复工（PAUSE/RESUME）/ 终止审批（TERMINATE_*）</li>
 *   <li>立项后关键字段可改且落变更台账（I4，biz_project_change_log）</li>
 *   <li>结项条件实口径（I5：应收台账余额/应付未付/质保金/保证金+备用金/最终结算）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final BizProjectMapper projectMapper;
    private final SerialNumberService serialNumberService;
    private final ProjectMemberService memberService;
    private final ApprovalService approvalService;
    /** 项目删除级联：自有子表直接清理（R7-02） */
    private final BizProjectMemberMapper projectMemberMapper;
    private final BizProjectWbsNodeMapper wbsNodeMapper;
    private final SysUserProjectMapper userProjectMapper;
    /** 项目删除级联：跳模块子表由各自模块监听清理（避免 Maven 循环依赖） */
    private final ApplicationEventPublisher eventPublisher;
    /** P1-M1：状态机 + 两台账 */
    private final ProjectStateMachine stateMachine;
    private final BizProjectStatusLogMapper statusLogMapper;
    private final BizProjectChangeLogMapper changeLogMapper;

    /** 立项审批开关（B1） */
    private static final String CONFIG_FILING_APPROVAL = "project_filing_approval_enabled";

    /** 关键字段清单（I4：立项后修改必落变更台账；驼峰属性名） */
    private static final Set<String> KEY_FIELDS = Set.of(
            "projectName", "ownerCompanyId", "ownerCompanyName",
            "signingCompanyId", "signingCompanyName",
            "budgetAmount", "contractAmount",
            "plannedStartDate", "plannedEndDate", "needTender");

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
     * 列表查询（供前端下拉选择使用）
     * 按项目名称模糊匹配，按创建时间倒序，限制返回条数避免数据量过大。
     * 上限 500：项目总量 209（2026 取证），原 50 上限导致下拉只显示前 50 条，
     * 按创建时间倒序使较早创建的项目不可选。
     */
    public List<BizProject> list(String projectName) {
        LambdaQueryWrapper<BizProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(projectName), BizProject::getProjectName, projectName)
                .orderByDesc(BizProject::getCreatedAt)
                .last("LIMIT 500");
        return projectMapper.selectList(wrapper);
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
     * 从请求 DTO 更新项目（P1-M1 A5：状态感知的编辑矩阵 + 变更台账）
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
     * 更新项目（P1-M1 A5 重写：状态感知编辑矩阵 + I4 变更台账）。
     * <ul>
     *   <li>DRAFT：全字段可改（原语义保留）</li>
     *   <li>FILED 及之后的活跃态：基本信息可改——修正录入错误不再是"冻结死"；
     *       关键字段（名称/业主/签约方/金额/计划周期/招标标志）逐项 diff 落变更台账</li>
     *   <li>终态（CLOSED/LOST/TERMINATED）与审批中间态（CLOSING/TERMINATING）：只读</li>
     * </ul>
     */
    public void update(BizProject project) {
        BizProject existing = projectMapper.selectById(project.getId());
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        String status = existing.getStatus();
        if (ProjectStateMachine.TERMINAL_STATUSES.contains(status)
                || "CLOSING".equals(status) || "TERMINATING".equals(status)) {
            throw new BusinessException("项目当前状态[" + status + "]不可编辑");
        }
        if ("DRAFT".equals(status)) {
            projectMapper.updateById(project);
            return;
        }
        // 活跃态：以 existing 为基线做字段级合并，关键字段 diff 落台账
        Long operatorId = SecurityContextHolder.getUserId();
        java.util.List<BizProjectChangeLog> logs = new java.util.ArrayList<>();
        java.lang.reflect.Field[] fields = BizProject.class.getDeclaredFields();
        for (java.lang.reflect.Field f : fields) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            Object newVal;
            try {
                newVal = f.get(project);
            } catch (IllegalAccessException e) {
                continue;
            }
            if (newVal == null) {
                continue; // 请求未携带的字段视为不改（部分更新语义）
            }
            Object oldVal;
            try {
                oldVal = f.get(existing);
            } catch (IllegalAccessException e) {
                continue;
            }
            boolean changed = (oldVal == null) || !newVal.equals(oldVal);
            if ("status".equals(f.getName()) || "id".equals(f.getName())
                    || "workflowInstanceId".equals(f.getName())) {
                continue; // 状态字段不走编辑通道
            }
            if (changed) {
                try {
                    f.set(existing, newVal); // 合并新值（反射写回暂存实体）
                } catch (IllegalAccessException ignored) {
                    continue; // 不可写字段直接跳过（不阻断编辑）
                }
                if (KEY_FIELDS.contains(f.getName())) {
                    BizProjectChangeLog row = new BizProjectChangeLog();
                    row.setProjectId(existing.getId());
                    row.setFieldName(f.getName());
                    row.setOldValue(oldVal == null ? null : String.valueOf(oldVal));
                    row.setNewValue(String.valueOf(newVal));
                    row.setOperatorId(operatorId);
                    logs.add(row);
                }
            }
        }
        // 周期不变量（I3 的编辑侧守卫）
        if (existing.getPlannedStartDate() != null && existing.getPlannedEndDate() != null
                && existing.getPlannedEndDate().isBefore(existing.getPlannedStartDate())) {
            throw new BusinessException("计划竣工日期不得早于计划开工日期");
        }
        if (existing.getActualStartDate() != null && existing.getActualEndDate() != null
                && existing.getActualEndDate().isBefore(existing.getActualStartDate())) {
            throw new BusinessException("实际竣工日期不得早于实际开工日期");
        }
        // 只更新业务字段，防篡改状态/乐观锁由 @Version 机制承担
        projectMapper.updateById(existing);
        logs.forEach(changeLogMapper::insert);
        if (!logs.isEmpty()) {
            log.info("项目关键字段变更留痕, projectId={}, 变更字段数={}", existing.getId(), logs.size());
        }
    }

    /**
     * 删除项目（仅DRAFT状态可删；存在关联投标报名时一律拦截，防引用悬空）
     * <p>
     * <b>级联清理（2026-09-18 R7-02 修复）</b>：原实现仅删 biz_project 单表，
     * 导致子表数据全部变成孤儿（线上取证：372 个已删项目遗留 ~2000 条孤儿，
     * 其中施工合同 49 条、项目成员 336 条、用户-项目映射 1000 条）。
     * </p>
     * <p>级联分两层：
     * <ol>
     *   <li><b>本模块自有表</b>（biz_project_member / biz_project_wbs_node / sys_user_project）
     *       直接调 Mapper 清理</li>
     *   <li><b>其他 9 个模块的表</b>（合同/材料/财务/劳务/机械/分包/采购/预算/现场）
     *       发布 {@link ProjectDeletedEvent}，由各模块自己的 Listener 清理。
     *       不能直接注入这些模块的 Service：它们全部已依赖 zw-insight-project，
     *       反向注入会形成 Maven 循环依赖（惯例参照 BizProjectMapper.countTenderRegisters
     *       的「直接查表避免循环依赖」注释）</li>
     * </ol>
     * 整体单事务：任一 Listener 抛异常则全部回滚，不允许「项目删了但子表留着」的半成品状态。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizProject existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        // 引用检查（2026-08-21 台账缺陷修复）：挂报名引用的项目不可删除，
        // E2E 清理须先删报名再删项目（与账本「引用拦截」预期对齐）
        if (projectMapper.countTenderRegisters(id) > 0) {
            throw new BusinessException("项目存在关联投标报名，不可删除");
        }

        // 级联第 1 层：本模块自有子表（与主表同为逻辑删除；sys_user_project 无 deleted 列故物理删）
        int members = projectMemberMapper.logicDeleteByProjectId(id);
        int wbsNodes = wbsNodeMapper.logicDeleteByProjectId(id);
        int userProjects = userProjectMapper.deleteByProjectId(id);

        projectMapper.deleteById(id);

        // 级联第 2 层：发布事件，其他模块同步清理自己的表（同事务，异常传播则整体回滚）
        eventPublisher.publishEvent(new ProjectDeletedEvent(
                this, id, existing.getTenantId(), existing.getProjectCode(), existing.getProjectName()));

        log.info("项目删除完成（含级联）, projectId={}, code={}, 自有子表清理: member={} wbs={} userProject={}",
                id, existing.getProjectCode(), members, wbsNodes, userProjects);
    }

    /**
     * 批量删除（仅DRAFT状态可删；空列表拒绝，整体单事务，任一失败回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("ID 列表不能为空");
        }
        for (Long id : ids) {
            delete(id);
        }
    }

    /**
     * 提交项目（DRAFT → FILED；B1：sys_config 开关控制是否走立项审批）。
     * <p>开关关闭（默认）：直置位 FILED（兼容 L4 lifecycle 断言）。
     * 开关开启：发起 PROJECT_FILING 审批（复用 project_close_approval 审批链），
     * 状态仍先置 FILED（信息可用），审批通过仅记大事记；驳回则回退 DRAFT。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizProject existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        stateMachine.fire(existing, ProjectEvent.SUBMIT, null, SecurityContextHolder.getUserId());
        if (isFilingApprovalEnabled()) {
            Map<String, Object> variables = new java.util.HashMap<>();
            variables.put("businessType", "PROJECT_FILING");
            variables.put("projectId", id);
            approvalService.startProcess("PROJECT_FILING", id, "project_close_approval", variables);
            log.info("立项审批已发起（开关开启）, projectId={}", id);
        }
    }

    private boolean isFilingApprovalEnabled() {
        try {
            String v = projectMapper.selectConfigValue(CONFIG_FILING_APPROVAL);
            return "true".equalsIgnoreCase(v == null ? "" : v.trim());
        } catch (Exception e) {
            return false; // 配置读取异常一律回退直置位，不阻断立项
        }
    }

    /** 撤回立项（FILED→DRAFT，A1：信息有误收回修改） */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        BizProject existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        stateMachine.fire(existing, ProjectEvent.WITHDRAW, "撤回立项", SecurityContextHolder.getUserId());
    }

    /** 进入投标（FILED→TENDERING；投标报名模块调用） */
    @Transactional(rollbackFor = Exception.class)
    public void goTender(Long id) {
        BizProject existing = projectMapper.selectById(id);
        stateMachine.fire(existing, ProjectEvent.GO_TENDER, null, SecurityContextHolder.getUserId());
    }

    /** 中标（TENDERING→WON；开标模块调用） */
    @Transactional(rollbackFor = Exception.class)
    public void winBid(Long id) {
        BizProject existing = projectMapper.selectById(id);
        stateMachine.fire(existing, ProjectEvent.WIN_BID, "投标中标", SecurityContextHolder.getUserId());
    }

    /** 落标归档（TENDERING→LOST 终态，A3；须记原因，落标项目禁止再被业务引用） */
    @Transactional(rollbackFor = Exception.class)
    public void loseBid(Long id, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("落标原因不能为空（落标归档须留痕）");
        }
        BizProject existing = projectMapper.selectById(id);
        existing.setLostReason(StrUtil.maxLength(reason, 300));
        stateMachine.fire(existing, ProjectEvent.LOSE_BID, "落标：" + reason, SecurityContextHolder.getUserId());
    }

    /** 开工（WON/FILED→CONSTRUCTION；施工合同生效模块调用，含 I2 准入守卫） */
    @Transactional(rollbackFor = Exception.class)
    public void startConstruction(Long id) {
        BizProject existing = projectMapper.selectById(id);
        stateMachine.fire(existing, ProjectEvent.START_CONSTRUCTION, "施工合同生效开工",
                SecurityContextHolder.getUserId());
    }

    /** 暂停（CONSTRUCTION→PAUSED，A4：留痕原因） */
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long id, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("暂停原因不能为空（停工留痕）");
        }
        BizProject existing = projectMapper.selectById(id);
        existing.setPauseReason(StrUtil.maxLength(reason, 300));
        stateMachine.fire(existing, ProjectEvent.PAUSE, "暂停：" + reason, SecurityContextHolder.getUserId());
    }

    /** 复工（PAUSED→CONSTRUCTION） */
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        BizProject existing = projectMapper.selectById(id);
        existing.setPauseReason(null);
        stateMachine.fire(existing, ProjectEvent.RESUME, "复工", SecurityContextHolder.getUserId());
    }

    /** 竣工（CONSTRUCTION→COMPLETED；竣工验收模块调用，回写实际竣工日） */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        BizProject existing = projectMapper.selectById(id);
        stateMachine.fire(existing, ProjectEvent.COMPLETE, "竣工验收通过", SecurityContextHolder.getUserId());
    }

    /**
     * 发起终止审批（A4：任意非终态→TERMINATING；复用 project_close_approval 审批链，
     * businessType=PROJECT_TERMINATE）。审批通过→TERMINATED（回调）；
     * 驳回→回退发起前状态（从大事记回溯）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long id, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("终止原因不能为空");
        }
        BizProject existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        // 在途业务单据拦截预警：存在未审批完成的付款/结算时拒绝终止（防半途账）
        long inFlight = countInFlightFinanceDocs(id);
        if (inFlight > 0) {
            throw new BusinessException("项目存在 " + inFlight + " 笔在途（审批中）的付款/结算单据，请先处理完毕再终止");
        }
        existing.setTerminateReason(StrUtil.maxLength(reason, 500));
        stateMachine.fire(existing, ProjectEvent.TERMINATE_APPLY, "发起终止：" + reason,
                SecurityContextHolder.getUserId());
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("businessType", "PROJECT_TERMINATE");
        variables.put("projectId", id);
        approvalService.startProcess("PROJECT_TERMINATE", id, "project_close_approval", variables);
    }

    /** 在途业务单据计数（终止守卫：审批中的付款申请与项目结算） */
    private long countInFlightFinanceDocs(Long projectId) {
        return projectMapper.countInFlightPayments(projectId)
                + projectMapper.countInFlightSettlements(projectId);
    }

    /** 终止审批通过回调（TERMINATING→TERMINATED） */
    @Transactional(rollbackFor = Exception.class)
    public void onTerminateApproved(Long id) {
        BizProject project = projectMapper.selectById(id);
        stateMachine.fire(project, ProjectEvent.TERMINATE_APPROVED, "终止审批通过", null);
    }

    /** 终止审批驳回回调（TERMINATING→发起前状态） */
    @Transactional(rollbackFor = Exception.class)
    public void onTerminateRejected(Long id) {
        BizProject project = projectMapper.selectById(id);
        stateMachine.fire(project, ProjectEvent.TERMINATE_REJECTED, "终止审批驳回", null);
    }

    /**
     * 更新项目状态（P1-M1 收口：自由赋值废弃）。
     * <p>保留签名兼容既有调用方，内部把状态对推断为业务事件并走状态机校验；
     * 无法推断的跳变一律拒绝。新代码请直接使用 withdraw/pause/resume/terminate 等事件方法。</p>
     */
    public void updateStatus(Long id, String newStatus) {
        BizProject existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("项目不存在");
        }
        ProjectEvent event = inferEvent(existing.getStatus(), newStatus);
        stateMachine.fire(existing, event, "兼容通道：" + newStatus, SecurityContextHolder.getUserId());
    }

    /** 状态对 → 事件推断（兼容通道专用；无法推断即拒绝） */
    private ProjectEvent inferEvent(String from, String to) {
        if ("DRAFT".equals(from) && "FILED".equals(to)) return ProjectEvent.SUBMIT;
        if ("FILED".equals(from) && "DRAFT".equals(to)) return ProjectEvent.WITHDRAW;
        if ("FILED".equals(from) && "TENDERING".equals(to)) return ProjectEvent.GO_TENDER;
        if ("TENDERING".equals(from) && "WON".equals(to)) return ProjectEvent.WIN_BID;
        if ("TENDERING".equals(from) && "LOST".equals(to)) return ProjectEvent.LOSE_BID;
        if (("WON".equals(from) || "FILED".equals(from)) && "CONSTRUCTION".equals(to)) return ProjectEvent.START_CONSTRUCTION;
        if ("CONSTRUCTION".equals(from) && "PAUSED".equals(to)) return ProjectEvent.PAUSE;
        if ("PAUSED".equals(from) && "CONSTRUCTION".equals(to)) return ProjectEvent.RESUME;
        if ("CONSTRUCTION".equals(from) && "COMPLETED".equals(to)) return ProjectEvent.COMPLETE;
        if ("COMPLETED".equals(from) && "CLOSING".equals(to)) return ProjectEvent.APPLY_CLOSE;
        if ("CLOSING".equals(from) && "CLOSED".equals(to)) return ProjectEvent.APPROVE_CLOSE;
        if ("CLOSING".equals(from) && "COMPLETED".equals(to)) return ProjectEvent.REJECT_CLOSE;
        throw new BusinessException("不允许的状态变更：" + from + " → " + to + "（请使用业务事件接口）");
    }

    /**
     * 项目结项/关闭（发起审批流程）
     * <p>
     * 校验结项条件通过后，发起 project_close_approval 审批流，将状态置为 CLOSING（结项审批中）。
     * 审批通过后由 {@link #onCloseApproved(Long)} 置 CLOSED；驳回后由 {@link #onCloseRejected(Long)} 回退 COMPLETED。
     * </p>
     * <p>
     * 结项条件校验：
     * 1. 项目状态必须为 COMPLETED（已竣工验收）
     * 2. 款项基本结清（容差100元）
     * 3. 项目已进入施工阶段
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
    
        BizProject project = projectMapper.selectById(id);
    
        // 发起结项审批流程
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("businessType", "PROJECT_CLOSE");
        variables.put("projectId", id);
        String processInstanceId = approvalService.startProcess(
                "PROJECT_CLOSE", id, "project_close_approval", variables);
    
        // 更新状态为 CLOSING（结项审批中），记录流程实例ID —— 走状态机（留大事记）
        project.setWorkflowInstanceId(processInstanceId);
        stateMachine.fire(project, ProjectEvent.APPLY_CLOSE, "发起结项", SecurityContextHolder.getUserId());
    }
    
    /**
     * 结项审批通过回调：状态 CLOSING → CLOSED
     */
    @Transactional(rollbackFor = Exception.class)
    public void onCloseApproved(Long id) {
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        stateMachine.fire(project, ProjectEvent.APPROVE_CLOSE, "结项审批通过", null);
    }
    
    /**
     * 结项审批驳回回调：状态 CLOSING → COMPLETED（回退）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onCloseRejected(Long id) {
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        stateMachine.fire(project, ProjectEvent.REJECT_CLOSE, "结项审批驳回回退", null);
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

        // 条件2（A6 实口径 I5-1）：应收台账余额已清（receivable_amount = OPEN 应收余额，V2026_57 维护）
        BigDecimal receivableOpen = project.getReceivableAmount() != null ? project.getReceivableAmount() : BigDecimal.ZERO;
        boolean paymentOk = receivableOpen.compareTo(BigDecimal.valueOf(100)) <= 0;
        addCondition(conditions, failedReasons, "应收账款已结清", paymentOk,
                paymentOk ? "应收余额: " + receivableOpen : "应收台账仍有余额: " + receivableOpen);

        // 条件2b（I5-2）：五类支出合同应付未付清零
        BigDecimal payable = nvl(projectMapper.sumPayableOutstanding(id));
        boolean payableOk = payable.compareTo(BigDecimal.valueOf(100)) <= 0;
        addCondition(conditions, failedReasons, "应付款项已结清", payableOk,
                payableOk ? "应付未付: " + payable : "仍有应付未付: " + payable);

        // 条件2c（I5-3）：质保金全退
        BigDecimal retention = nvl(projectMapper.sumUnreturnedRetention(id));
        boolean retentionOk = retention.compareTo(BigDecimal.ZERO) <= 0;
        addCondition(conditions, failedReasons, "质保金已退还", retentionOk,
                retentionOk ? "无未退质保金" : "仍有未退质保金: " + retention);

        // 条件2d（I5-4）：保证金与备用金全退
        BigDecimal bond = nvl(projectMapper.sumUnreturnedSecurityBond(id));
        BigDecimal reserve = nvl(projectMapper.sumUnreturnedReserveFund(id));
        boolean bondOk = bond.add(reserve).compareTo(BigDecimal.ZERO) <= 0;
        addCondition(conditions, failedReasons, "保证金/备用金已结清", bondOk,
                bondOk ? "无未退保证金/备用金" : "仍有未退保证金 " + bond + " / 备用金 " + reserve);

        // 条件3：项目状态非 DRAFT/FILED（基本有效性）
        boolean notDraft = !"DRAFT".equals(project.getStatus()) && !"FILED".equals(project.getStatus());
        addCondition(conditions, failedReasons, "项目已进入施工阶段", notDraft,
                notDraft ? "已通过" : "项目尚未进入施工阶段");

        // 条件4：存在已审批的最终结算单（项目关闭的唯一通道前置）
        boolean settlementOk = projectMapper.countApprovedSettlement(id) > 0;
        addCondition(conditions, failedReasons, "最终结算已审批", settlementOk,
                settlementOk ? "已通过" : "项目最终结算单尚未审批通过，需先完成最终结算");

        result.put("conditions", conditions);
        result.put("failedReasons", failedReasons);
        result.put("allPassed", failedReasons.isEmpty());

        return result;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
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

    /** 项目大事记（流转时间线，详情页 B4） */
    public List<BizProjectStatusLog> statusTimeline(Long projectId) {
        return statusLogMapper.selectList(new LambdaQueryWrapper<BizProjectStatusLog>()
                .eq(BizProjectStatusLog::getProjectId, projectId)
                .orderByDesc(BizProjectStatusLog::getCreatedAt)
                .orderByDesc(BizProjectStatusLog::getId));
    }

    /** 关键字段变更台账（I4 查询侧） */
    public List<BizProjectChangeLog> changeLogs(Long projectId) {
        return changeLogMapper.selectList(new LambdaQueryWrapper<BizProjectChangeLog>()
                .eq(BizProjectChangeLog::getProjectId, projectId)
                .orderByDesc(BizProjectChangeLog::getCreatedAt)
                .orderByDesc(BizProjectChangeLog::getId));
    }

    /**
     * 项目组合看板（状态 × 金额分布）
     * <p>dashboard company-overview 仅提供状态数量分布，此处补充金额维度。</p>
     */
    public ProjectPortfolioVO portfolio() {
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<>());

        ProjectPortfolioVO vo = new ProjectPortfolioVO();
        vo.setTotalProjectCount(projects.size());
        vo.setTotalContractAmount(sumAmount(projects, BizProject::getContractAmount));
        vo.setTotalBudgetAmount(sumAmount(projects, BizProject::getBudgetAmount));
        vo.setTotalCumulativeOutput(sumAmount(projects, BizProject::getCumulativeOutput));

        // 按状态分组（TreeMap 保证状态枚举顺序输出）
        Map<String, List<BizProject>> byStatus = new TreeMap<>();
        for (BizProject project : projects) {
            String status = project.getStatus() != null ? project.getStatus() : "UNKNOWN";
            byStatus.computeIfAbsent(status, k -> new ArrayList<>()).add(project);
        }
        List<ProjectPortfolioVO.StatusItem> statusList = new ArrayList<>();
        for (Map.Entry<String, List<BizProject>> entry : byStatus.entrySet()) {
            ProjectPortfolioVO.StatusItem item = new ProjectPortfolioVO.StatusItem();
            item.setStatus(entry.getKey());
            item.setCount(entry.getValue().size());
            item.setContractAmount(sumAmount(entry.getValue(), BizProject::getContractAmount));
            item.setBudgetAmount(sumAmount(entry.getValue(), BizProject::getBudgetAmount));
            item.setCumulativeOutput(sumAmount(entry.getValue(), BizProject::getCumulativeOutput));
            statusList.add(item);
        }
        vo.setStatusList(statusList);
        return vo;
    }

    private BigDecimal sumAmount(List<BizProject> list, Function<BizProject, BigDecimal> getter) {
        return list.stream().map(getter).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
