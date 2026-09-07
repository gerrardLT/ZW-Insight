package com.zwinsight.budget.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.domain.BizCostAccountLink;
import com.zwinsight.budget.domain.BizCostAccountTxn;
import com.zwinsight.budget.service.CostAccountLinkService;
import com.zwinsight.budget.service.CostAccountService;
import com.zwinsight.budget.service.CostLedgerService;
import com.zwinsight.budget.service.CostRollUpService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CostAccountController} 单元测试（CBS 成本账户管理端点）。
 *
 * <p>本控制器是薄委派层：业务逻辑全在 Service，控制器只负责
 * 「参数绑定 → 调对服务方法 → 用 {@code R.ok(...)} 包装」。这类胶水代码同样必须测，
 * 因为它一旦错位（参数顺序写反、漏传筛选条件、忘记包装），
 * 前端拿到的就是错数据，而 Service 层单测全绿也发现不了。
 *
 * <p>本测试逐个端点钉住三件事：
 * <ol>
 *   <li>调用的是哪个 Service 方法</li>
 *   <li>传下去的参数（含 null 与可选筛选）与入参一致、顺序正确</li>
 *   <li>返回值被 {@code R.ok} 包装，code=200，data 为服务层同一实例</li>
 * </ol>
 *
 * <p>另钉住一条分层约定：<b>控制器不做业务校验</b>。例如 {@code getTree} 的 projectId
 * 由 {@code @RequestParam} 标为必填但没有额外判空，真正的「项目ID不能为空」
 * 由 {@link CostAccountService} 抛出并原样向上传播——校验只有一处，避免两层各写一套。
 */
@ExtendWith(MockitoExtension.class)
class CostAccountControllerTest {

    @Mock private CostAccountService costAccountService;
    @Mock private CostLedgerService costLedgerService;
    @Mock private CostRollUpService costRollUpService;
    @Mock private CostAccountLinkService costAccountLinkService;

    @InjectMocks
    private CostAccountController controller;

    // ==================== 构造工具 ====================

    private static BizCostAccount account(Long id, String code) {
        BizCostAccount a = new BizCostAccount();
        a.setId(id);
        a.setProjectId(1L);
        a.setAccountCode(code);
        a.setAccountName("账户-" + code);
        a.setCostCategory("LABOR");
        return a;
    }

    // =====================================================================
    // 查询端点
    // =====================================================================

    @Nested
    @DisplayName("查询：分页 / 树 / 详情 / 流水 / 绑定")
    class ReadTests {

        @Test
        @DisplayName("page：七个参数按顺序透传，结果用 R.ok 包装")
        void page_passesAllSevenParamsInOrder() {
            PageResult<BizCostAccount> pageResult =
                    new PageResult<>(List.of(account(10L, "CB-001")), 1L, 1L, 50L, 1L);
            when(costAccountService.page(1, 50, 1L, 2L, 3L, "LABOR", "ACTIVE")).thenReturn(pageResult);

            R<PageResult<BizCostAccount>> r = controller.page(1, 50, 1L, 2L, 3L, "LABOR", "ACTIVE");

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isSameAs(pageResult);
            assertThat(r.getData().getTotal()).isEqualTo(1L);
            verify(costAccountService).page(1, 50, 1L, 2L, 3L, "LABOR", "ACTIVE");
        }

        @Test
        @DisplayName("page：可选筛选全为 null 时也必须原样传下去（不能擅自补默认值）")
        void page_nullFiltersPassedThrough() {
            PageResult<BizCostAccount> pageResult =
                    new PageResult<>(Collections.emptyList(), 0L, 1L, 50L, 0L);
            when(costAccountService.page(1, 50, null, null, null, null, null)).thenReturn(pageResult);

            R<PageResult<BizCostAccount>> r = controller.page(1, 50, null, null, null, null, null);

            assertThat(r.getData().getRecords()).isEmpty();
            verify(costAccountService).page(1, 50, null, null, null, null, null);
        }

        @Test
        @DisplayName("getTree：projectId 透传，返回根节点列表")
        void getTree_passesProjectId() {
            List<BizCostAccount> roots = List.of(account(1L, "CB-001"));
            when(costAccountService.getTree(1L)).thenReturn(roots);

            R<List<BizCostAccount>> r = controller.getTree(1L);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isSameAs(roots);
            verify(costAccountService).getTree(1L);
        }

        @Test
        @DisplayName("分层约定：控制器不校验 projectId，服务层异常原样向上传播")
        void getTree_serviceValidationPropagates() {
            when(costAccountService.getTree(null))
                    .thenThrow(new com.zwinsight.common.exception.BusinessException("项目ID不能为空"));

            assertThatThrownBy(() -> controller.getTree(null))
                    .isInstanceOf(com.zwinsight.common.exception.BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");
        }

        @Test
        @DisplayName("getById：返回服务层同一实例")
        void getById_wrapsServiceReturn() {
            BizCostAccount acc = account(10L, "CB-010");
            when(costAccountService.getById(10L)).thenReturn(acc);

            R<BizCostAccount> r = controller.getById(10L);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isSameAs(acc);
            verify(costAccountService).getById(10L);
        }

        @Test
        @DisplayName("ledger：账户 ID + 金额维度 + 起止时间四个参数原样透传（含 null）")
        void ledger_passesAllFilters() {
            LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2026, 9, 30, 23, 59);
            List<BizCostAccountTxn> txns = List.of(new BizCostAccountTxn());
            when(costLedgerService.listByAccount(10L, "CURRENT", from, to)).thenReturn(txns);

            R<List<BizCostAccountTxn>> r = controller.ledger(10L, "CURRENT", from, to);

            assertThat(r.getData()).isSameAs(txns);
            verify(costLedgerService).listByAccount(10L, "CURRENT", from, to);
        }

        @Test
        @DisplayName("ledger：不传金额维度与时间范围时按 null 透传（服务层自行处理为「全部」）")
        void ledger_nullFiltersPassedThrough() {
            when(costLedgerService.listByAccount(10L, null, null, null))
                    .thenReturn(Collections.emptyList());

            R<List<BizCostAccountTxn>> r = controller.ledger(10L, null, null, null);

            assertThat(r.getData()).isEmpty();
            verify(costLedgerService).listByAccount(10L, null, null, null);
        }

        @Test
        @DisplayName("ledgerPage：服务层返回 IPage，控制器负责转成 PageResult")
        void ledgerPage_convertsIPageToPageResult() {
            Page<BizCostAccountTxn> iPage = new Page<>(2, 20);
            iPage.setRecords(List.of(new BizCostAccountTxn()));
            iPage.setTotal(21L);
            when(costLedgerService.page(2, 20, 100L, "CURRENT", "CONTRACT")).thenReturn(iPage);

            R<PageResult<BizCostAccountTxn>> r = controller.ledgerPage(2, 20, 100L, "CURRENT", "CONTRACT");

            assertThat(r.getData().getTotal()).isEqualTo(21L);
            assertThat(r.getData().getPage()).isEqualTo(2L);
            assertThat(r.getData().getSize()).isEqualTo(20L);
            assertThat(r.getData().getRecords()).hasSize(1);
            verify(costLedgerService).page(2, 20, 100L, "CURRENT", "CONTRACT");
        }

        @Test
        @DisplayName("listLinks：按项目查绑定关系")
        void listLinks_byProject() {
            List<BizCostAccountLink> links = List.of(new BizCostAccountLink());
            when(costAccountLinkService.listByProject(1L)).thenReturn(links);

            R<List<BizCostAccountLink>> r = controller.listLinks(1L);

            assertThat(r.getData()).isSameAs(links);
            verify(costAccountLinkService).listByProject(1L);
        }

        @Test
        @DisplayName("listLinksByAccount：按账户查绑定明细（这个账户的钱来自哪些单据）")
        void listLinksByAccount_passesAccountId() {
            when(costAccountLinkService.listByAccount(100L)).thenReturn(Collections.emptyList());

            R<List<BizCostAccountLink>> r = controller.listLinksByAccount(100L);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isEmpty();
            verify(costAccountLinkService).listByAccount(100L);
        }
    }

    // =====================================================================
    // 写入端点
    // =====================================================================

    @Nested
    @DisplayName("写入：增 / 改 / 删 / 锁 / 关 / 同步")
    class WriteTests {

        @Test
        @DisplayName("create：请求体原样交给服务层，返回创建结果")
        void create_delegatesAndWraps() {
            BizCostAccount req = account(null, "CB-NEW");
            BizCostAccount created = account(900L, "CB-NEW");
            when(costAccountService.create(req)).thenReturn(created);

            R<BizCostAccount> r = controller.create(req);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isSameAs(created);
            assertThat(r.getData().getId()).isEqualTo(900L);
            verify(costAccountService).create(req);
        }

        @Test
        @DisplayName("update：路径 id 与请求体分别作为两个参数传下去（顺序不可颠倒）")
        void update_passesIdAndPatchSeparately() {
            BizCostAccount patch = new BizCostAccount();
            patch.setAccountName("新名称");
            BizCostAccount updated = account(10L, "CB-010");
            updated.setAccountName("新名称");
            when(costAccountService.update(10L, patch)).thenReturn(updated);

            R<BizCostAccount> r = controller.update(10L, patch);

            assertThat(r.getData().getAccountName()).isEqualTo("新名称");
            verify(costAccountService).update(10L, patch);
        }

        @Test
        @DisplayName("delete：服务层为 void，控制器返回 R.ok() 且 data 为 null")
        void delete_returnsOkWithNullData() {
            R<Void> r = controller.delete(404L);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isNull();
            verify(costAccountService).delete(404L);
        }

        @Test
        @DisplayName("lock：委派服务层，返回 R.ok()")
        void lock_delegates() {
            R<Void> r = controller.lock(10L);

            assertThat(r.getCode()).isEqualTo(200);
            verify(costAccountService).lock(10L);
        }

        @Test
        @DisplayName("close：委派服务层，返回 R.ok()")
        void close_delegates() {
            R<Void> r = controller.close(20L);

            assertThat(r.getCode()).isEqualTo(200);
            verify(costAccountService).close(20L);
        }

        @Test
        @DisplayName("sync：commitmentDelta 与 actualDelta 两个可选参数按顺序透传")
        void sync_passesBothDeltasInOrder() {
            R<Void> r = controller.sync(10L, new BigDecimal("30"), new BigDecimal("20"));

            assertThat(r.getCode()).isEqualTo(200);
            // 顺序写反会导致承诺额与实际额互换，属高危错位，必须钉住
            verify(costAccountService).syncFromSource(10L, new BigDecimal("30"), new BigDecimal("20"));
        }

        @Test
        @DisplayName("sync：两个 delta 均为 null 时同样透传，由服务层决定是否跳过")
        void sync_nullDeltasPassedThrough() {
            controller.sync(10L, null, null);

            verify(costAccountService).syncFromSource(10L, null, null);
        }
    }

    // =====================================================================
    // 归集与绑定
    // =====================================================================

    @Nested
    @DisplayName("归集与绑定：手工触发入口")
    class RollupAndBindTests {

        @Test
        @DisplayName("rollup：手工触发归集，报告原样返回（含 unmapped 待人工处理清单）")
        void rollup_returnsReport() {
            CostRollUpService.RollupReport report = new CostRollUpService.RollupReport();
            report.setProjectId(1L);
            report.setPostedCount(5);
            report.setDuplicateCount(2);
            CostRollUpService.UnmappedDoc doc = new CostRollUpService.UnmappedDoc();
            doc.setReason("科目下存在多个账户，需显式绑定");
            report.setUnmapped(List.of(doc));
            when(costRollUpService.rollup(1L)).thenReturn(report);

            R<CostRollUpService.RollupReport> r = controller.rollup(1L);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isSameAs(report);
            assertThat(r.getData().getPostedCount()).isEqualTo(5);
            assertThat(r.getData().needsAttention()).isTrue();
            verify(costRollUpService).rollup(1L);
        }

        @Test
        @DisplayName("bind：单条绑定，返回既有或新建的绑定记录")
        void bind_wrapsSingleResult() {
            BizCostAccountLink req = new BizCostAccountLink();
            BizCostAccountLink bound = new BizCostAccountLink();
            when(costAccountLinkService.bind(req)).thenReturn(bound);

            R<BizCostAccountLink> r = controller.bind(req);

            assertThat(r.getData()).isSameAs(bound);
            verify(costAccountLinkService).bind(req);
        }

        @Test
        @DisplayName("bindBatch：批量绑定，返回成功条数")
        void bindBatch_wrapsCount() {
            List<BizCostAccountLink> requests =
                    List.of(new BizCostAccountLink(), new BizCostAccountLink());
            when(costAccountLinkService.bindBatch(requests)).thenReturn(2);

            R<Integer> r = controller.bindBatch(requests);

            assertThat(r.getData()).isEqualTo(2);
            verify(costAccountLinkService).bindBatch(requests);
        }

        @Test
        @DisplayName("unbind：解绑只影响下次归集分配，控制器返回 R.ok()")
        void unbind_returnsOk() {
            R<Void> r = controller.unbind(999L);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isNull();
            verify(costAccountLinkService).unbind(999L);
        }
    }
}
