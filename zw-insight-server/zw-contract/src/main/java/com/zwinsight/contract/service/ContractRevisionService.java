package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.revision.ExpenseContractRevisionHandler;
import com.zwinsight.common.revision.RevisionContract;
import com.zwinsight.contract.domain.*;
import com.zwinsight.contract.domain.dto.ContractRevisionRequest;
import com.zwinsight.contract.mapper.*;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 合同修订服务。
 * <p>V2026_123 起支持支出合同族：施工合同沿用原有实现，采购/劳务/机械/分包
 * 按 contractCategory 分派到各模块的 {@link ExpenseContractRevisionHandler}，
 * 共享 biz_contract_revision 表与 contract_revision_approval 审批流程。</p>
 */
@Service
@RequiredArgsConstructor
public class ContractRevisionService {

    public static final String CATEGORY_CONSTRUCTION = "CONSTRUCTION";

    private final ContractRevisionMapper revisions;
    private final BizConstructionContractMapper contracts;
    private final BizProjectMapper projects;
    private final ApprovalService approvals;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final ContractPayTermService payTermService;
    private final List<ExpenseContractRevisionHandler> expenseHandlers;
    private final SysUserMapper userMapper;

    private ExpenseContractRevisionHandler handler(String category) {
        return expenseHandlers.stream()
                .filter(h -> h.category().equals(category))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的合同类别：" + category));
    }

    private static String categoryOf(String category) {
        return category == null || category.isBlank() ? CATEGORY_CONSTRUCTION : category.trim().toUpperCase();
    }

    private static boolean isConstruction(String category) {
        return CATEGORY_CONSTRUCTION.equals(categoryOf(category));
    }

    public ContractRevision detail(Long id) {
        ContractRevision revision = revisions.selectById(id);
        if (revision == null) throw new BusinessException("修订申请不存在");
        visibleContract(revision.getContractCategory(), revision.getContractId());
        return revision;
    }

    /** 用户侧详情：在内部 detail 之上补类别权限校验（审批回调仍走 detail，不受影响）。 */
    public ContractRevision detailForUser(Long id) {
        ContractRevision revision = detail(id);
        assertCategoryPermission(revision.getContractCategory(), "read");
        return revision;
    }

    /**
     * 类别权限校验（V2026_124 审查修复，V2026_125 升级为写码）。
     * <p>控制器的方法级注解是 5 个写码（或 submit 的施工码）OR，只能保证"持有任一模块写权限"；
     * 若不在此按类别二次校验，仅持 machine:contract:edit 的用户可借默认类别读取/修改施工合同修订。
     * 读动作按模块 view 码，写/提交动作按模块修订写码（purchase/labor/machine/subcontract:contract:edit）。</p>
     */
    private void assertCategoryPermission(String category, String action) {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) return; // 系统任务/审批回调不按操作人校验
        List<String> permissions = userMapper.selectPermissionsByUserId(userId);
        if (permissions == null) permissions = List.of();
        if (permissions.contains("*:*:*")) return;
        String required = requiredCode(category, action);
        if (required != null && !permissions.contains(required)) {
            throw new BusinessException(403, "无权操作该类别合同修订");
        }
    }

    private static String requiredCode(String category, String action) {
        String normalized = categoryOf(category);
        if (CATEGORY_CONSTRUCTION.equals(normalized)) {
            return switch (action) {
                case "read" -> "contract:view";
                case "submit" -> "contract:contract:submit";
                default -> "contract:contract:edit";
            };
        }
        boolean readOnly = "read".equals(action);
        return switch (normalized) {
            case "PURCHASE" -> readOnly ? "purchase:view" : "purchase:contract:edit";
            case "LABOR" -> readOnly ? "labor:view" : "labor:contract:edit";
            case "MACHINE" -> readOnly ? "machine:view" : "machine:contract:edit";
            case "SUBCONTRACT" -> readOnly ? "subcontract:view" : "subcontract:contract:edit";
            default -> null;
        };
    }

    private void visibleContract(String category, Long id) {
        String normalized = categoryOf(category);
        if (isConstruction(normalized)) {
            if (contracts.selectById(id) == null) throw new BusinessException(403, "无权访问源合同");
            return;
        }
        if (!handler(normalized).exists(id)) throw new BusinessException(403, "无权访问源合同");
    }

    public List<ContractRevision> list(Long contractId) {
        return list(contractId, CATEGORY_CONSTRUCTION);
    }

    public List<ContractRevision> list(Long contractId, String contractCategory) {
        String category = categoryOf(contractCategory);
        assertCategoryPermission(category, "read");
        visibleContract(category, contractId);
        return revisions.selectList(new LambdaQueryWrapper<ContractRevision>()
                .eq(ContractRevision::getContractCategory, category)
                .eq(ContractRevision::getContractId, contractId)
                .orderByDesc(ContractRevision::getCreatedAt));
    }

    private BizConstructionContract locked(Long id) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM biz_construction_contract WHERE id=? AND tenant_id=? AND deleted=0 FOR UPDATE",
                Long.class, id, SecurityContextHolder.getTenantId());
        if (ids.isEmpty()) throw new BusinessException("合同不存在");
        BizConstructionContract c = contracts.selectOne(new LambdaQueryWrapper<BizConstructionContract>()
                .eq(BizConstructionContract::getId, id).last("FOR UPDATE"));
        if (c == null || !"EFFECTIVE".equals(c.getStatus())) throw new BusinessException("仅生效合同可申请修订");
        return c;
    }

    private String snapshot(BizConstructionContract c) {
        try {
            // V2026_109：快照纳入收付款条款（修订前后条款变化可追溯，防无痕篡改支付约定）
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("contract", c);
            body.put("payTerms", payTermService.list(BizContractPayTerm.CATEGORY_CONSTRUCTION, c.getId()));
            return json.writeValueAsString(body);
        }
        catch (JsonProcessingException e) { throw new IllegalStateException("合同快照序列化失败", e); }
    }

    static BigDecimal value(BigDecimal n) { return n == null ? BigDecimal.ZERO : n; }

    static void validateFloor(BizConstructionContract c, BigDecimal amount) {
        BigDecimal ceiling = amount.add(value(c.getCumulativeChangeAmount()));
        if (ceiling.compareTo(value(c.getCumulativeOutput())) < 0
                || ceiling.compareTo(value(c.getCumulativeInvoiceAmount())) < 0
                || ceiling.compareTo(value(c.getCumulativeReceivedAmount())) < 0)
            throw new BusinessException("修订后合同总额不得低于已发生产值、开票或收款");
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ContractRevisionRequest request) {
        String category = categoryOf(request.contractCategory());
        assertCategoryPermission(category, "write");
        if (!isConstruction(category)) {
            return createExpense(category, request);
        }
        BizConstructionContract c = locked(request.contractId());
        if (!Objects.equals(c.getVersion(), request.baseVersion())) throw new BusinessException("合同版本已变化，请刷新后申请");
        if (request.afterAmount().compareTo(c.getContractAmount()) == 0) throw new BusinessException("修订金额未变化");
        validateFloor(c, request.afterAmount());
        if (revisions.selectCount(new LambdaQueryWrapper<ContractRevision>()
                .eq(ContractRevision::getContractCategory, CATEGORY_CONSTRUCTION)
                .eq(ContractRevision::getContractId, c.getId())
                .in(ContractRevision::getStatus, "DRAFT", "SUBMITTED", "REJECTED")) > 0)
            throw new BusinessException("合同已有在途修订申请");
        ContractRevision v = new ContractRevision();
        v.setContractId(c.getId());
        v.setContractCategory(CATEGORY_CONSTRUCTION);
        v.setProjectId(c.getProjectId());
        v.setBaseVersion(c.getVersion());
        v.setBeforeAmount(c.getContractAmount());
        v.setAfterAmount(request.afterAmount());
        v.setDeltaAmount(request.afterAmount().subtract(c.getContractAmount()));
        v.setBeforeSnapshot(snapshot(c)); c.setContractAmount(request.afterAmount()); tax(c);
        v.setAfterSnapshot(snapshot(c)); v.setReason(request.reason()); v.setStatus("DRAFT");
        revisions.insert(v); return v.getId();
    }

    /**
     * 支出合同修订申请：由各模块 handler 校验合同与累计值下限，本服务只负责修订台账与在途控制。
     */
    private Long createExpense(String category, ContractRevisionRequest request) {
        ExpenseContractRevisionHandler handler = handler(category);
        RevisionContract base = handler.lockAndValidate(request.contractId(), request.baseVersion(), request.afterAmount());
        if (revisions.selectCount(new LambdaQueryWrapper<ContractRevision>()
                .eq(ContractRevision::getContractCategory, category)
                .eq(ContractRevision::getContractId, base.getContractId())
                .in(ContractRevision::getStatus, "DRAFT", "SUBMITTED", "REJECTED")) > 0)
            throw new BusinessException("合同已有在途修订申请");
        ContractRevision v = new ContractRevision();
        v.setContractId(base.getContractId());
        v.setContractCategory(category);
        v.setProjectId(base.getProjectId());
        v.setBaseVersion(base.getVersion());
        v.setBeforeAmount(base.getContractAmount());
        v.setAfterAmount(request.afterAmount());
        v.setDeltaAmount(request.afterAmount().subtract(base.getContractAmount()));
        v.setBeforeSnapshot(base.getSnapshot());
        v.setAfterSnapshot(expenseAfterSnapshot(request.afterAmount()));
        v.setReason(request.reason());
        v.setStatus("DRAFT");
        revisions.insert(v);
        return v.getId();
    }

    private String expenseAfterSnapshot(BigDecimal afterAmount) {
        try {
            return json.writeValueAsString(Map.of("afterAmount", afterAmount));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("修订后快照序列化失败", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ContractRevision initial = detail(id);
        assertCategoryPermission(initial.getContractCategory(), "submit");
        if (!isConstruction(initial.getContractCategory())) {
            submitExpense(id);
            return;
        }
        BizConstructionContract c = locked(initial.getContractId());
        ContractRevision v = revisions.selectOne(new LambdaQueryWrapper<ContractRevision>()
                .eq(ContractRevision::getId, id).last("FOR UPDATE"));
        if (!"DRAFT".equals(v.getStatus())) throw new BusinessException("仅草稿可提交");
        baseline(c, v); validateFloor(c, v.getAfterAmount());
        String process = approvals.startProcess("CONTRACT_REVISION", id, "contract_revision_approval",
                approvalVariables(v));
        v.setStatus("SUBMITTED"); v.setWorkflowInstanceId(process);
        if (revisions.updateById(v) != 1) throw new BusinessException("修订申请已变化");
    }

    private void submitExpense(Long id) {
        ContractRevision v = revisions.selectOne(new LambdaQueryWrapper<ContractRevision>()
                .eq(ContractRevision::getId, id).last("FOR UPDATE"));
        if (v == null) throw new BusinessException("修订申请不存在");
        if (!"DRAFT".equals(v.getStatus())) throw new BusinessException("仅草稿可提交");
        // 再次校验合同基准与修订下限（草稿期间合同可能已被其他操作修改）
        handler(v.getContractCategory()).lockAndValidate(v.getContractId(), v.getBaseVersion(), v.getAfterAmount());
        String process = approvals.startProcess("CONTRACT_REVISION", id, "contract_revision_approval",
                approvalVariables(v));
        v.setStatus("SUBMITTED");
        v.setWorkflowInstanceId(process);
        if (revisions.updateById(v) != 1) throw new BusinessException("修订申请已变化");
    }

    private Map<String, Object> approvalVariables(ContractRevision v) {
        Long tenant = SecurityContextHolder.getTenantId(), initiator = SecurityContextHolder.getUserId();
        if (tenant == null || initiator == null) throw new BusinessException(401, "登录上下文缺失");
        List<Long> managers = jdbc.queryForList("SELECT DISTINCT u.id FROM biz_project_member m JOIN sys_user u ON u.id=m.user_id "
                + "AND u.tenant_id=m.tenant_id WHERE m.project_id=? AND m.tenant_id=? AND m.deleted=0 AND m.status=1 "
                + "AND JSON_CONTAINS(m.project_roles, '\"PROJECT_MANAGER\"') AND u.deleted=0 AND u.status=1 AND u.id<>?",
                Long.class, v.getProjectId(), tenant, initiator);
        List<Long> finance = jdbc.queryForList("SELECT DISTINCT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id "
                + "JOIN sys_role r ON r.id=ur.role_id WHERE u.tenant_id=? AND u.status=1 AND u.deleted=0 "
                + "AND r.status=1 AND r.deleted=0 AND (r.tenant_id=? OR r.tenant_id IS NULL) "
                + "AND r.role_code='FINANCE_STAFF' AND u.id<>?", Long.class, tenant, tenant, initiator);
        if (managers.isEmpty()) throw new BusinessException("缺少非发起人的有效项目负责人，禁止自审");
        if (finance.isEmpty()) throw new BusinessException("缺少非发起人的有效财务审批人，禁止自审");
        return Map.of("projectId", v.getProjectId(), "contractAmount", v.getAfterAmount(),
                "revisionManagers", managers.stream().map(String::valueOf).collect(Collectors.joining(",")),
                "revisionFinance", finance.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private static void baseline(BizConstructionContract c, ContractRevision v) {
        if (!Objects.equals(c.getVersion(), v.getBaseVersion()) || c.getContractAmount().compareTo(v.getBeforeAmount()) != 0)
            throw new BusinessException("合同基准已变化，须重新申请修订");
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        ContractRevision initial = detail(id);
        if ("APPROVED".equals(initial.getStatus())) return;
        if (!isConstruction(initial.getContractCategory())) {
            approveExpense(id);
            return;
        }
        BizConstructionContract c = locked(initial.getContractId());
        ContractRevision v = revisions.selectOne(new LambdaQueryWrapper<ContractRevision>()
                .eq(ContractRevision::getId, id).last("FOR UPDATE"));
        if ("APPROVED".equals(v.getStatus())) return;
        if (!List.of("SUBMITTED", "REJECTED").contains(v.getStatus())) throw new BusinessException("修订不在审批中");
        baseline(c, v); validateFloor(c, v.getAfterAmount());
        c.setContractAmount(v.getAfterAmount()); tax(c);
        int count = contracts.updateContractAmountWithVersion(
                c.getId(), v.getBaseVersion(), v.getBeforeAmount(),
                c.getContractAmount(), c.getAmountWithoutTax(), c.getTaxAmount());
        if (count != 1) throw new BusinessException("合同并发修订冲突");
        if (projects.addContractAmount(v.getProjectId(), v.getDeltaAmount()) != 1) throw new BusinessException("项目金额回写失败");
        v.setStatus("APPROVED");
        if (revisions.updateById(v) != 1) throw new BusinessException("修订状态并发冲突");
    }

    private void approveExpense(Long id) {
        ContractRevision v = revisions.selectOne(new LambdaQueryWrapper<ContractRevision>()
                .eq(ContractRevision::getId, id).last("FOR UPDATE"));
        if (v == null) throw new BusinessException("修订申请不存在");
        if ("APPROVED".equals(v.getStatus())) return;
        if (!List.of("SUBMITTED", "REJECTED").contains(v.getStatus())) throw new BusinessException("修订不在审批中");
        handler(v.getContractCategory()).applyAmount(
                v.getContractId(), v.getBaseVersion(), v.getBeforeAmount(), v.getAfterAmount());
        v.setStatus("APPROVED");
        if (revisions.updateById(v) != 1) throw new BusinessException("修订状态并发冲突");
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id) { reject(id, false); }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, boolean terminated) {
        ContractRevision initial = detail(id);
        if (isConstruction(initial.getContractCategory())) locked(initial.getContractId());
        ContractRevision v = revisions.selectOne(new LambdaQueryWrapper<ContractRevision>()
                .eq(ContractRevision::getId, id).last("FOR UPDATE"));
        if (!List.of("SUBMITTED", "REJECTED").contains(v.getStatus())) return;
        v.setStatus(terminated ? "CANCELLED" : "REJECTED");
        if (revisions.updateById(v) != 1) throw new BusinessException("修订状态并发冲突");
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ContractRevision initial = detail(id);
        assertCategoryPermission(initial.getContractCategory(), "write");
        if (isConstruction(initial.getContractCategory())) locked(initial.getContractId());
        ContractRevision v = revisions.selectOne(new LambdaQueryWrapper<ContractRevision>()
                .eq(ContractRevision::getId, id).last("FOR UPDATE"));
        if (!"DRAFT".equals(v.getStatus())) throw new BusinessException("仅草稿可作废");
        v.setStatus("CANCELLED");
        if (revisions.updateById(v) != 1) throw new BusinessException("修订状态并发冲突");
    }

    private static void tax(BizConstructionContract c) {
        BigDecimal net = c.getContractAmount().divide(BigDecimal.ONE.add(value(c.getTaxRate()).divide(new BigDecimal("100"))), 2, RoundingMode.HALF_UP);
        c.setAmountWithoutTax(net);
        c.setTaxAmount(c.getContractAmount().subtract(net));
    }
}
