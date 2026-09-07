package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CostAccountService} 单元测试（CBS 成本账户服务）。
 *
 * <p>覆盖三条主线：查询（分页/建树/详情/类别汇总）、写入（增改锁关删）、
 * 成本主线（变更事件传导 {@code handleChangeEventApproval}、源单据同步 {@code syncFromSource}）。
 *
 * <h3>本测试刻意钉住的三处「实现如此但并不理想」的行为</h3>
 * <p>写这些用例的目的不是美化缺陷，而是让后续改动无法悄悄改变行为：
 * <ol>
 *   <li><b>{@code close()} 对 LOCKED 账户写库两次</b>：先写 ACTIVE 再写 CLOSED，
 *       中间态被真实持久化。最终态正确，但多一次无谓 UPDATE，
 *       且 binlog/审计里会留下「关闭前又变回活动」的误导痕迹。</li>
 *   <li><b>{@code syncFromSource()} 在两个 delta 都为 null/0 时仍 updateById 一次</b>：纯无效写。</li>
 *   <li><b>{@code handleChangeEventApproval()} 把同一个 totalDelta 加到列表里每个账户上</b>：
 *       N 个账户 → 总额被放大 N 倍。其 Javadoc 声称负载是
 *       {@code [{accountId, deltaType, deltaAmount}]}（每账户各自 delta），
 *       但签名 {@code (List<Long> accountIds, BigDecimal totalDelta)} 根本无法表达该结构。
 *       全仓库 grep 确认<b>零调用方</b>：真实变更传导走
 *       {@code ChangeEventApprovedCostHandler → CostLedgerService.postBatch}，
 *       按账户各自 signed delta 记账并带幂等键 {@code eventId:accountId}。
 *       故此方法是残留死代码，一旦被误用即造成成本重复入账，属高风险陷阱。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CostAccountServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_CLOSED = "CLOSED";

    @Mock private BizCostAccountMapper costAccountMapper;

    @InjectMocks
    private CostAccountService service;

    // ==================== 构造工具 ====================

    private static BizCostAccount account(Long id, Long parentId, String code, String category, String status) {
        BizCostAccount a = new BizCostAccount();
        a.setId(id);
        a.setProjectId(PROJECT_ID);
        a.setParentId(parentId);
        a.setAccountCode(code);
        a.setAccountName("账户-" + code);
        a.setCostCategory(category);
        a.setStatus(status);
        return a;
    }

    /** 只带必填字段的待创建账户（金额与状态全 null，用于验证默认值填充）。 */
    private static BizCostAccount newAccount(String code) {
        BizCostAccount a = new BizCostAccount();
        a.setProjectId(PROJECT_ID);
        a.setAccountCode(code);
        a.setAccountName("账户-" + code);
        a.setCostCategory("LABOR");
        return a;
    }

    private static BizCostAccountMapper.CostAccountCategorySum categorySum(String category, int count, String current) {
        BizCostAccountMapper.CostAccountCategorySum s = new BizCostAccountMapper.CostAccountCategorySum();
        s.setCostCategory(category);
        s.setAccountCount(count);
        s.setCurrentAmount(new BigDecimal(current));
        return s;
    }

    // =====================================================================
    // 查询
    // =====================================================================

    @Nested
    @DisplayName("查询：分页 / 建树 / 详情 / 类别汇总")
    class QueryTests {

        @Test
        @DisplayName("page：projectId 为空拒绝，不触达 Mapper")
        void page_nullProjectId_throws() {
            assertThatThrownBy(() -> service.page(1, 10, null, null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");

            verify(costAccountMapper, never())
                    .selectAccountPage(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("page：透传全部筛选条件，keyword 固定传 null（服务层未开放关键字）")
        void page_passesFiltersAndNullKeyword() {
            Page<BizCostAccount> mpPage = new Page<>(1, 10);
            mpPage.setRecords(List.of(account(10L, null, "CB-001", "LABOR", STATUS_ACTIVE)));
            mpPage.setTotal(1L);
            doReturn(mpPage).when(costAccountMapper)
                    .selectAccountPage(any(Page.class), eq(PROJECT_ID), eq(20L), eq(30L),
                            eq("LABOR"), eq(STATUS_ACTIVE), isNull());

            PageResult<BizCostAccount> result =
                    service.page(1, 10, PROJECT_ID, 20L, 30L, "LABOR", STATUS_ACTIVE);

            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getAccountCode()).isEqualTo("CB-001");
        }

        @Test
        @DisplayName("getTree：projectId 为空拒绝")
        void getTree_nullProjectId_throws() {
            assertThatThrownBy(() -> service.getTree(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");

            verify(costAccountMapper, never()).selectByProject(any());
        }

        @Test
        @DisplayName("getTree：无账户返回空列表而非 null")
        void getTree_noAccounts_returnsEmptyList() {
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(Collections.emptyList());

            assertThat(service.getTree(PROJECT_ID)).isEmpty();
        }

        @Test
        @DisplayName("getTree：父子正确装配，孤儿（父已删）提升为根不丢数据")
        void getTree_assemblesHierarchyAndPromotesOrphan() {
            BizCostAccount root = account(1L, null, "CB-001", "LABOR", STATUS_ACTIVE);
            BizCostAccount child = account(2L, 1L, "CB-001-01", "LABOR", STATUS_ACTIVE);
            BizCostAccount orphan = account(3L, 999L, "CB-002", "MATERIAL", STATUS_ACTIVE);
            when(costAccountMapper.selectByProject(PROJECT_ID)).thenReturn(List.of(root, child, orphan));

            List<BizCostAccount> roots = service.getTree(PROJECT_ID);

            assertThat(roots).hasSize(2);
            assertThat(roots).extracting(BizCostAccount::getId).containsExactly(1L, 3L);
            assertThat(roots.get(0).getChildren()).hasSize(1);
            assertThat(roots.get(0).getChildren().get(0).getId()).isEqualTo(2L);
            assertThat(roots.get(1).getChildren()).isEmpty();
        }

        @Test
        @DisplayName("getTree：parentId 指向自身的脏数据按根处理，不自挂成环")
        void getTree_selfParent_treatedAsRoot() {
            when(costAccountMapper.selectByProject(PROJECT_ID))
                    .thenReturn(List.of(account(10L, 10L, "CB-010", "OTHER", STATUS_ACTIVE)));

            List<BizCostAccount> roots = service.getTree(PROJECT_ID);

            assertThat(roots).hasSize(1);
            assertThat(roots.get(0).getChildren()).isEmpty();
        }

        @Test
        @DisplayName("getById：不存在抛异常并带出 ID")
        void getById_notFound_throws() {
            when(costAccountMapper.selectById(404L)).thenReturn(null);

            assertThatThrownBy(() -> service.getById(404L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户不存在")
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("getById：存在时原样返回")
        void getById_found_returnsAccount() {
            BizCostAccount acc = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            acc.setCurrentAmount(new BigDecimal("80"));
            when(costAccountMapper.selectById(10L)).thenReturn(acc);

            BizCostAccount found = service.getById(10L);

            assertThat(found.getAccountCode()).isEqualTo("CB-010");
            assertThat(found.getCurrentAmount()).isEqualByComparingTo("80");
        }

        @Test
        @DisplayName("sumByCategory：projectId 为空拒绝")
        void sumByCategory_nullProjectId_throws() {
            assertThatThrownBy(() -> service.sumByCategory(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");

            verify(costAccountMapper, never()).sumByCategory(any());
        }

        @Test
        @DisplayName("sumByCategory：透传单条 SQL 的六维汇总结果")
        void sumByCategory_delegatesToMapper() {
            when(costAccountMapper.sumByCategory(PROJECT_ID)).thenReturn(List.of(
                    categorySum("LABOR", 10, "80"),
                    categorySum("MATERIAL", 5, "40")));

            List<BizCostAccountMapper.CostAccountCategorySum> result = service.sumByCategory(PROJECT_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getCostCategory()).isEqualTo("LABOR");
            assertThat(result.get(0).getAccountCount()).isEqualTo(10);
            assertThat(result.get(0).getCurrentAmount()).isEqualByComparingTo("80");
        }
    }

    // =====================================================================
    // 写入
    // =====================================================================

    @Nested
    @DisplayName("写入：创建 / 更新 / 锁定 / 关闭 / 删除")
    class WriteTests {

        @Test
        @DisplayName("create 校验：projectId 为空拒绝")
        void create_nullProjectId_throws() {
            BizCostAccount a = newAccount("CB-001");
            a.setProjectId(null);

            assertThatThrownBy(() -> service.create(a))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");

            verify(costAccountMapper, never()).insert(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("create 校验：编号空白拒绝")
        void create_blankCode_throws() {
            assertThatThrownBy(() -> service.create(newAccount("   ")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户编号不能为空");
        }

        @Test
        @DisplayName("create 校验：编号 51 字符拒绝，恰好 50 字符放行")
        void create_codeLengthBoundary() {
            assertThatThrownBy(() -> service.create(newAccount("C".repeat(51))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("编号长度不得超过 50 字符");

            service.create(newAccount("C".repeat(50)));
            verify(costAccountMapper).insert(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("create 校验：名称空白拒绝")
        void create_blankName_throws() {
            BizCostAccount a = newAccount("CB-001");
            a.setAccountName("");

            assertThatThrownBy(() -> service.create(a))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户名称不能为空");
        }

        @Test
        @DisplayName("create 校验：名称 201 字符拒绝")
        void create_nameTooLong_throws() {
            BizCostAccount a = newAccount("CB-001");
            a.setAccountName("N".repeat(201));

            assertThatThrownBy(() -> service.create(a))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("名称长度不得超过 200 字符");
        }

        @Test
        @DisplayName("create 校验：费用类别空白拒绝（CBS 归类是成本口径的前提）")
        void create_blankCategory_throws() {
            BizCostAccount a = newAccount("CB-001");
            a.setCostCategory("  ");

            assertThatThrownBy(() -> service.create(a))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("费用类别不能为空");

            verify(costAccountMapper, never()).insert(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("create：同项目内编号重复拒绝，不落库")
        void create_duplicateCode_throws() {
            when(costAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> service.create(newAccount("CB-001")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户编号已存在")
                    .hasMessageContaining("CB-001");

            verify(costAccountMapper, never()).insert(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("create：status 空白补 ACTIVE，五个金额 null 全部补 ZERO")
        void create_populatesDefaultStatusAndZeroAmounts() {
            BizCostAccount saved = service.create(newAccount("CB-001"));

            assertThat(saved.getStatus()).isEqualTo(STATUS_ACTIVE);
            assertThat(saved.getBaselineAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getCurrentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getCommitmentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getActualAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getForecastAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(costAccountMapper).insert(saved);
        }

        @Test
        @DisplayName("create：调用方显式给的金额与状态不被覆盖")
        void create_explicitValuesPreserved() {
            BizCostAccount a = newAccount("CB-001");
            a.setStatus(STATUS_LOCKED);
            a.setBaselineAmount(new BigDecimal("100"));
            a.setCurrentAmount(new BigDecimal("80"));
            a.setCommitmentAmount(new BigDecimal("60"));
            a.setActualAmount(new BigDecimal("20"));
            a.setForecastAmount(new BigDecimal("30"));

            BizCostAccount saved = service.create(a);

            assertThat(saved.getStatus()).isEqualTo(STATUS_LOCKED);
            assertThat(saved.getBaselineAmount()).isEqualByComparingTo("100");
            assertThat(saved.getCurrentAmount()).isEqualByComparingTo("80");
            assertThat(saved.getCommitmentAmount()).isEqualByComparingTo("60");
            assertThat(saved.getActualAmount()).isEqualByComparingTo("20");
            assertThat(saved.getForecastAmount()).isEqualByComparingTo("30");
        }

        @Test
        @DisplayName("update：账户不存在拒绝")
        void update_notFound_throws() {
            when(costAccountMapper.selectById(404L)).thenReturn(null);

            assertThatThrownBy(() -> service.update(404L, new BizCostAccount()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户不存在");

            verify(costAccountMapper, never()).updateById(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("update：编号未变时不做唯一性查询（省一次 SQL）")
        void update_sameCode_skipsUniquenessQuery() {
            BizCostAccount existing = account(10L, null, "CB-001", "LABOR", STATUS_ACTIVE);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            BizCostAccount patch = new BizCostAccount();
            patch.setAccountCode("CB-001");
            patch.setAccountName("新名称");

            service.update(10L, patch);

            verify(costAccountMapper, never()).selectCount(any(LambdaQueryWrapper.class));
            verify(costAccountMapper).updateById(existing);
        }

        @Test
        @DisplayName("update：改成已占用编号时拒绝，且原编号不被写脏")
        void update_codeChangeDuplicate_throws() {
            BizCostAccount existing = account(10L, null, "CB-001", "LABOR", STATUS_ACTIVE);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);
            when(costAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            BizCostAccount patch = new BizCostAccount();
            patch.setAccountCode("CB-002");

            assertThatThrownBy(() -> service.update(10L, patch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户编号已存在")
                    .hasMessageContaining("CB-002");

            assertThat(existing.getAccountCode()).isEqualTo("CB-001");
            verify(costAccountMapper, never()).updateById(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("update：改成唯一编号放行")
        void update_codeChangeUnique_applies() {
            BizCostAccount existing = account(10L, null, "CB-001", "LABOR", STATUS_ACTIVE);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);
            when(costAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            BizCostAccount patch = new BizCostAccount();
            patch.setAccountCode("CB-002");

            service.update(10L, patch);

            assertThat(existing.getAccountCode()).isEqualTo("CB-002");
            verify(costAccountMapper).updateById(existing);
        }

        @Test
        @DisplayName("update：name/subcategory/remark/status/wbsNodeId 非 null 即合并")
        void update_mergesNonNullFields() {
            BizCostAccount existing = account(10L, null, "CB-001", "LABOR", STATUS_ACTIVE);
            existing.setCostSubcategory("旧子类");
            existing.setRemark("旧备注");
            existing.setWbsNodeId(55L);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            BizCostAccount patch = new BizCostAccount();
            patch.setAccountName("新名称");
            patch.setCostSubcategory("新子类");
            patch.setRemark("新备注");
            patch.setStatus(STATUS_LOCKED);
            patch.setWbsNodeId(66L);

            service.update(10L, patch);

            assertThat(existing.getAccountName()).isEqualTo("新名称");
            assertThat(existing.getCostSubcategory()).isEqualTo("新子类");
            assertThat(existing.getRemark()).isEqualTo("新备注");
            assertThat(existing.getStatus()).isEqualTo(STATUS_LOCKED);
            assertThat(existing.getWbsNodeId()).isEqualTo(66L);
        }

        @Test
        @DisplayName("update：patch 字段为 null 时保留原值")
        void update_nullFieldsPreserveOriginals() {
            BizCostAccount existing = account(10L, null, "CB-001", "LABOR", STATUS_ACTIVE);
            existing.setAccountName("原名称");
            existing.setWbsNodeId(55L);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.update(10L, new BizCostAccount());

            assertThat(existing.getAccountName()).isEqualTo("原名称");
            assertThat(existing.getStatus()).isEqualTo(STATUS_ACTIVE);
            assertThat(existing.getWbsNodeId()).isEqualTo(55L);
            verify(costAccountMapper).updateById(existing);
        }

        @Test
        @DisplayName("lock：CLOSED 账户拒绝锁定")
        void lock_closedAccount_throws() {
            when(costAccountMapper.selectById(10L))
                    .thenReturn(account(10L, null, "CB-010", "LABOR", STATUS_CLOSED));

            assertThatThrownBy(() -> service.lock(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("账户已关闭，无法锁定");

            verify(costAccountMapper, never()).updateById(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("lock：ACTIVE → LOCKED")
        void lock_activeToLocked() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.lock(10L);

            assertThat(existing.getStatus()).isEqualTo(STATUS_LOCKED);
            verify(costAccountMapper, times(1)).updateById(existing);
        }

        @Test
        @DisplayName("lock：LOCKED → LOCKED 幂等放行，不报错")
        void lock_alreadyLocked_isIdempotent() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_LOCKED);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.lock(10L);

            assertThat(existing.getStatus()).isEqualTo(STATUS_LOCKED);
            verify(costAccountMapper, times(1)).updateById(existing);
        }

        @Test
        @DisplayName("钉住缺陷：close() 对 LOCKED 账户写库两次，中间态 ACTIVE 被真实持久化")
        void close_lockedAccount_writesTwiceWithTransientActiveState() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_LOCKED);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.close(10L);

            assertThat(existing.getStatus()).isEqualTo(STATUS_CLOSED);
            // 第 1 次写 ACTIVE（源码 close() 的「解锁」分支），第 2 次写 CLOSED。
            // 最终态正确，但多一次无谓 UPDATE，审计/binlog 会留下误导痕迹。
            verify(costAccountMapper, times(2)).updateById(existing);
        }

        @Test
        @DisplayName("close：ACTIVE 账户只写一次 CLOSED")
        void close_activeAccount_writesOnce() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.close(10L);

            assertThat(existing.getStatus()).isEqualTo(STATUS_CLOSED);
            verify(costAccountMapper, times(1)).updateById(existing);
        }

        @Test
        @DisplayName("close：CLOSED 账户重复关闭不触发解锁分支，仍只写一次")
        void close_alreadyClosed_writesOnce() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_CLOSED);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.close(10L);

            assertThat(existing.getStatus()).isEqualTo(STATUS_CLOSED);
            verify(costAccountMapper, times(1)).updateById(existing);
        }

        @Test
        @DisplayName("delete：有子账户拒绝，报错带出子账户数与编号")
        void delete_hasChildren_throws() {
            when(costAccountMapper.selectById(10L))
                    .thenReturn(account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE));
            when(costAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            assertThatThrownBy(() -> service.delete(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("该账户下还有 2 个子账户")
                    .hasMessageContaining("CB-010");

            verify(costAccountMapper, never()).deleteById(any(Long.class));
        }

        @Test
        @DisplayName("delete：叶子账户允许删除")
        void delete_leafAccount_deletes() {
            when(costAccountMapper.selectById(10L))
                    .thenReturn(account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE));
            when(costAccountMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            service.delete(10L);

            verify(costAccountMapper).deleteById(10L);
        }
    }

    // =====================================================================
    // 成本主线：变更事件传导 / 源单据同步
    // =====================================================================

    @Nested
    @DisplayName("成本主线：变更传导 / 源单据同步")
    class MainlineTests {

        @Test
        @DisplayName("变更传导：走 SQL 原子自增，不做读-改-写")
        void handleChangeEventApproval_usesAtomicAdjustNotReadModifyWrite() {
            when(costAccountMapper.adjustCurrentAmount(eq(101L), any(BigDecimal.class))).thenReturn(1);

            service.handleChangeEventApproval(List.of(101L), new BigDecimal("50000"));

            verify(costAccountMapper).adjustCurrentAmount(101L, new BigDecimal("50000"));
            verify(costAccountMapper, never()).selectById(any());
            verify(costAccountMapper, never()).updateById(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("变更传导：affected=0（账户不存在或调整后为负被 DB 拒绝）抛异常，不静默吞掉")
        void handleChangeEventApproval_adjustAffectsNothing_throws() {
            when(costAccountMapper.adjustCurrentAmount(eq(101L), any(BigDecimal.class))).thenReturn(0);

            assertThatThrownBy(() ->
                    service.handleChangeEventApproval(List.of(101L), new BigDecimal("-999999")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户 [101] 调整失败");
        }

        @Test
        @DisplayName("变更传导：负 delta（变更核减）同样被接受，只要 DB 侧未拒绝")
        void handleChangeEventApproval_negativeDelta_accepted() {
            when(costAccountMapper.adjustCurrentAmount(eq(101L), any(BigDecimal.class))).thenReturn(1);

            service.handleChangeEventApproval(List.of(101L), new BigDecimal("-30000"));

            verify(costAccountMapper).adjustCurrentAmount(101L, new BigDecimal("-30000"));
        }

        @Test
        @DisplayName("变更传导：空列表不触发任何调整")
        void handleChangeEventApproval_emptyList_noOp() {
            service.handleChangeEventApproval(Collections.emptyList(), new BigDecimal("100"));

            verify(costAccountMapper, never()).adjustCurrentAmount(any(), any());
        }

        @Test
        @DisplayName("钉住缺陷：多账户时同一 totalDelta 被加到每个账户，总额放大 N 倍")
        void handleChangeEventApproval_multipleAccounts_appliesFullDeltaToEach() {
            when(costAccountMapper.adjustCurrentAmount(any(Long.class), any(BigDecimal.class))).thenReturn(1);

            service.handleChangeEventApproval(List.of(101L, 102L, 103L), new BigDecimal("100"));

            // 三个账户各 +100，合计 +300，而调用方传入的 totalDelta 只有 100。
            // 方法 Javadoc 声称负载是每账户各自的 {accountId, deltaType, deltaAmount}，
            // 但签名无法表达该结构。全仓库 grep 确认零调用方：真实链路走
            // ChangeEventApprovedCostHandler → CostLedgerService.postBatch，
            // 按账户各自 signed delta 记账并带幂等键 eventId:accountId。
            // 本用例钉住「它确实会这么做」，以便清理该死代码时能看见行为差异。
            verify(costAccountMapper).adjustCurrentAmount(101L, new BigDecimal("100"));
            verify(costAccountMapper).adjustCurrentAmount(102L, new BigDecimal("100"));
            verify(costAccountMapper).adjustCurrentAmount(103L, new BigDecimal("100"));
            verify(costAccountMapper, times(3)).adjustCurrentAmount(any(Long.class), any(BigDecimal.class));
        }

        @Test
        @DisplayName("变更传导：中途某账户失败即抛异常，后续账户不再调整")
        void handleChangeEventApproval_midListFailure_stopsRemaining() {
            when(costAccountMapper.adjustCurrentAmount(eq(101L), any(BigDecimal.class))).thenReturn(1);
            when(costAccountMapper.adjustCurrentAmount(eq(102L), any(BigDecimal.class))).thenReturn(0);

            assertThatThrownBy(() ->
                    service.handleChangeEventApproval(List.of(101L, 102L, 103L), new BigDecimal("100")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户 [102] 调整失败");

            verify(costAccountMapper).adjustCurrentAmount(101L, new BigDecimal("100"));
            verify(costAccountMapper).adjustCurrentAmount(102L, new BigDecimal("100"));
            // 第 3 个不再执行；已执行的第 1 个由 @Transactional 回滚，此处只验证调用序列
            verify(costAccountMapper, never()).adjustCurrentAmount(eq(103L), any(BigDecimal.class));
        }

        @Test
        @DisplayName("源单据同步：承诺与实际同时增量，基于原值累加")
        void syncFromSource_bothDeltas_accumulate() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            existing.setCommitmentAmount(new BigDecimal("100"));
            existing.setActualAmount(new BigDecimal("50"));
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.syncFromSource(10L, new BigDecimal("30"), new BigDecimal("20"));

            assertThat(existing.getCommitmentAmount()).isEqualByComparingTo("130");
            assertThat(existing.getActualAmount()).isEqualByComparingTo("70");
            verify(costAccountMapper).updateById(existing);
        }

        @Test
        @DisplayName("源单据同步：金额字段为 null 时按 0 起算，不抛 NPE")
        void syncFromSource_nullAmounts_treatedAsZero() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.syncFromSource(10L, new BigDecimal("30"), new BigDecimal("20"));

            assertThat(existing.getCommitmentAmount()).isEqualByComparingTo("30");
            assertThat(existing.getActualAmount()).isEqualByComparingTo("20");
        }

        @Test
        @DisplayName("源单据同步：账户不存在拒绝")
        void syncFromSource_accountNotFound_throws() {
            when(costAccountMapper.selectById(404L)).thenReturn(null);

            assertThatThrownBy(() -> service.syncFromSource(404L, new BigDecimal("10"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户不存在");

            verify(costAccountMapper, never()).updateById(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("源单据同步：承诺金额扣成负数拒绝，且不落库")
        void syncFromSource_negativeCommitment_throws() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            existing.setCommitmentAmount(new BigDecimal("10"));
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            assertThatThrownBy(() -> service.syncFromSource(10L, new BigDecimal("-11"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("承诺金额不能为负");

            verify(costAccountMapper, never()).updateById(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("源单据同步：实际金额扣成负数拒绝，且不落库")
        void syncFromSource_negativeActual_throws() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            existing.setActualAmount(new BigDecimal("10"));
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            assertThatThrownBy(() -> service.syncFromSource(10L, null, new BigDecimal("-15")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("实际金额不能为负");

            verify(costAccountMapper, never()).updateById(any(BizCostAccount.class));
        }

        @Test
        @DisplayName("源单据同步：扣到恰好 0 放行（0 不是负数）")
        void syncFromSource_exactlyZero_allowed() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            existing.setCommitmentAmount(new BigDecimal("10"));
            existing.setActualAmount(new BigDecimal("10"));
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.syncFromSource(10L, new BigDecimal("-10"), new BigDecimal("-10"));

            assertThat(existing.getCommitmentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(existing.getActualAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(costAccountMapper).updateById(existing);
        }

        @Test
        @DisplayName("源单据同步：delta 为 0 时跳过该维度计算，原值不变")
        void syncFromSource_zeroDelta_skipsThatDimension() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            existing.setCommitmentAmount(new BigDecimal("100"));
            existing.setActualAmount(new BigDecimal("50"));
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.syncFromSource(10L, BigDecimal.ZERO, BigDecimal.ZERO);

            assertThat(existing.getCommitmentAmount()).isEqualByComparingTo("100");
            assertThat(existing.getActualAmount()).isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("源单据同步：只给 commitmentDelta 时 actual 不受影响")
        void syncFromSource_onlyCommitmentDelta_leavesActualUntouched() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            existing.setCommitmentAmount(new BigDecimal("100"));
            existing.setActualAmount(new BigDecimal("50"));
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.syncFromSource(10L, new BigDecimal("25"), null);

            assertThat(existing.getCommitmentAmount()).isEqualByComparingTo("125");
            assertThat(existing.getActualAmount()).isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("钉住缺陷：两个 delta 都为 null 时仍执行一次无效 updateById")
        void syncFromSource_bothDeltasNull_stillWritesOnce() {
            BizCostAccount existing = account(10L, null, "CB-010", "LABOR", STATUS_ACTIVE);
            when(costAccountMapper.selectById(10L)).thenReturn(existing);

            service.syncFromSource(10L, null, null);

            // 无任何字段变化却仍写库一次。行为稳定但属无效写，钉住以便后续优化时可见。
            verify(costAccountMapper, times(1)).updateById(existing);
        }
    }
}
