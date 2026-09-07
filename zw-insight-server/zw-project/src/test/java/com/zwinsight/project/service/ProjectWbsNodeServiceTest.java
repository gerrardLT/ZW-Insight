package com.zwinsight.project.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectWbsNode;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectWbsNodeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectWbsNodeService} 单元测试。
 *
 * <p>覆盖类级 Javadoc 声明的 5 条关键不变量，以及 MAX_LEVEL=5 深度上限：
 * <ol>
 *   <li>编号唯一（同项目内 nodeCode 唯一，应用层前置校验给友好错误）</li>
 *   <li>层级自洽（level 由父节点推导，<b>调用方传入的 level 被忽略</b>）</li>
 *   <li>禁止环（新父节点不得是自身或自身后代）</li>
 *   <li>删除保护（存在子节点时拒绝删除，避免孤儿）</li>
 *   <li>日期合法（endDate 不得早于 startDate）</li>
 * </ol>
 *
 * <h3>Mock 顺序约定（改动本测试前必读）</h3>
 * <p>{@code moveNode} 内 {@code collectDescendantIds} → {@code maxSubtreeDepth} →
 * {@code relevelDescendants} 会<b>依次</b>调用同一个 {@code wbsNodeMapper.selectList}，
 * 且前两者以完全相同的 BFS 顺序各遍历一遍子树。因此涉及子树的用例必须用
 * 连续 {@code thenReturn} 按调用轮次喂数据，见 {@link #stubSubtreeChainTwice}。
 *
 * <p>{@code projectMapper.selectBatchIds} 无需显式打桩：{@code ProjectNameFiller.fill}
 * 只在 projectId 非空时调用它，而 Mockito 对 List 返回类型的默认应答即空列表，
 * 不会 NPE，只是 projectName 回填为 null。需要断言回填效果时才打桩。
 */
@ExtendWith(MockitoExtension.class)
class ProjectWbsNodeServiceTest {

    private static final Long PROJECT_ID = 1L;

    @Mock private BizProjectWbsNodeMapper wbsNodeMapper;
    @Mock private BizProjectMapper projectMapper;

    @InjectMocks
    private ProjectWbsNodeService service;

    /**
     * 初始化 MyBatis-Plus 的实体列元数据（lambda cache）。
     *
     * <p><b>为什么必须做：</b>{@code collectDescendantIds} 与 {@code maxSubtreeDepth} 构造的
     * {@code LambdaQueryWrapper} 使用了 {@code .select(BizProjectWbsNode::getId)}。
     * 与 {@code eq/in/orderBy}（列名延迟到 {@code getSqlSegment()} 才解析，Mock 环境下永不触发）
     * 不同，{@code select()} 会<b>立即</b>解析列名，进而查 {@code LambdaUtils.getColumnMap}。
     * 纯 Mockito 单测没有 SqlSessionFactory 去注册实体，该 map 为 null，
     * 会抛 {@code MybatisPlusException: can not find lambda cache for this entity}。
     *
     * <p>这是本仓库既有统一做法（参见 zw-workflow {@code UrgeServiceTest}、
     * zw-dashboard {@code ProjectDashboardServiceTest} 等），属于测试基座引导，
     * 不改变被测逻辑，也不引入任何假数据。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizProjectWbsNode.class);
    }

    // ==================== 构造工具 ====================

    /** 构造一个字段完整的 WBS 节点（status 默认 ACTIVE）。 */
    private static BizProjectWbsNode node(Long id, Long projectId, Long parentId,
                                          Integer level, String code) {
        BizProjectWbsNode n = new BizProjectWbsNode();
        n.setId(id);
        n.setProjectId(projectId);
        n.setParentId(parentId);
        n.setNodeLevel(level);
        n.setNodeCode(code);
        n.setNodeName("名称-" + code);
        n.setStatus(ProjectWbsNodeService.STATUS_ACTIVE);
        return n;
    }

    private static BizProject project(Long id, String name) {
        BizProject p = new BizProject();
        p.setId(id);
        p.setProjectName(name);
        return p;
    }

    /** BFS 遍历到叶子时的收尾空列表（使 while 循环退出）。 */
    private static final List<BizProjectWbsNode> NO_CHILDREN = Collections.emptyList();

    /**
     * 按调用顺序为 {@code wbsNodeMapper.selectList} 连续打桩。
     *
     * <p>{@code moveNode} 会依次调用 {@code collectDescendantIds}（环检测）、
     * {@code maxSubtreeDepth}（深度计算）、{@code relevelDescendants}（子树层级平移），
     * 三者共用同一个 {@code selectList}，且前两者以完全相同的 BFS 顺序各遍历一遍子树。
     * 因此调用方必须按轮次显式给出返回值序列：
     * <ul>
     *   <li>环检测命中即抛 → 只需喂第 1 轮</li>
     *   <li>深度上限命中即抛 → 需喂第 1、2 轮</li>
     *   <li>移动成功且层级变化 → 需喂第 1、2、3 轮</li>
     * </ul>
     *
     * @param returns 每次 selectList 调用的返回值，按调用先后排列
     */
    @SafeVarargs
    private final void stubSelectListInOrder(List<BizProjectWbsNode>... returns) {
        OngoingStubbing<List<BizProjectWbsNode>> stub =
                when(wbsNodeMapper.selectList(any(LambdaQueryWrapper.class)));
        for (List<BizProjectWbsNode> r : returns) {
            stub = stub.thenReturn(r);
        }
    }

    // =====================================================================
    // 查询
    // =====================================================================

    @Nested
    @DisplayName("查询：分页 / 树装配 / 单条 / 子节点 / 下拉")
    class QueryTests {

        @Test
        @DisplayName("page：projectId 为空直接拒绝，不触达 DB")
        void page_nullProjectId_throws() {
            assertThatThrownBy(() -> service.page(1, 10, null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");

            verify(wbsNodeMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("page：正常分页并回填 projectName（一次批量查询，非 N+1）")
        void page_success_fillsProjectName() {
            BizProjectWbsNode n1 = node(10L, PROJECT_ID, null, 1, "PH-001");
            Page<BizProjectWbsNode> mpPage = new Page<>(1, 10);
            mpPage.setRecords(new ArrayList<>(List.of(n1)));
            mpPage.setTotal(1L);
            doReturn(mpPage).when(wbsNodeMapper).selectPage(any(), any());
            when(projectMapper.selectBatchIds(any())).thenReturn(List.of(project(PROJECT_ID, "滨江花园一期")));

            PageResult<BizProjectWbsNode> result = service.page(1, 10, PROJECT_ID, null, null, null);

            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getProjectName()).isEqualTo("滨江花园一期");
            // 回填必须走批量查询，禁止逐条 selectById
            verify(projectMapper, times(1)).selectBatchIds(any());
            verify(projectMapper, never()).selectById(any());
        }

        @Test
        @DisplayName("getTree：projectId 为空直接拒绝")
        void getTree_nullProjectId_throws() {
            assertThatThrownBy(() -> service.getTree(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");
        }

        @Test
        @DisplayName("getTree：三层结构正确装配父子关系，仅返回一个根")
        void getTree_threeLevels_assemblesHierarchy() {
            BizProjectWbsNode root = node(10L, PROJECT_ID, null, 1, "PH-001");
            BizProjectWbsNode pkg = node(11L, PROJECT_ID, 10L, 2, "WP-001");
            BizProjectWbsNode task = node(12L, PROJECT_ID, 11L, 3, "T-001");
            when(wbsNodeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(root, pkg, task));

            List<BizProjectWbsNode> roots = service.getTree(PROJECT_ID);

            assertThat(roots).hasSize(1);
            assertThat(roots.get(0).getId()).isEqualTo(10L);
            assertThat(roots.get(0).getChildren()).hasSize(1);
            assertThat(roots.get(0).getChildren().get(0).getId()).isEqualTo(11L);
            assertThat(roots.get(0).getChildren().get(0).getChildren()).hasSize(1);
            assertThat(roots.get(0).getChildren().get(0).getChildren().get(0).getId()).isEqualTo(12L);
        }

        @Test
        @DisplayName("getTree：父节点已不存在的孤儿提升为根，数据不丢失")
        void getTree_orphanParent_promotedToRoot() {
            BizProjectWbsNode orphan = node(20L, PROJECT_ID, 999L, 2, "WP-009");
            when(wbsNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(orphan));

            List<BizProjectWbsNode> roots = service.getTree(PROJECT_ID);

            assertThat(roots).hasSize(1);
            assertThat(roots.get(0).getId()).isEqualTo(20L);
            assertThat(roots.get(0).getParentId()).isEqualTo(999L);
        }

        @Test
        @DisplayName("getTree：parentId 指向自身的脏数据按根处理，不自挂成环")
        void getTree_selfReferencingParent_treatedAsRoot() {
            BizProjectWbsNode selfLoop = node(30L, PROJECT_ID, 30L, 1, "PH-030");
            when(wbsNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(selfLoop));

            List<BizProjectWbsNode> roots = service.getTree(PROJECT_ID);

            assertThat(roots).hasSize(1);
            assertThat(roots.get(0).getChildren()).isEmpty();
        }

        @Test
        @DisplayName("getTree：多根并存时全部返回")
        void getTree_multipleRoots_allReturned() {
            BizProjectWbsNode r1 = node(10L, PROJECT_ID, null, 1, "PH-001");
            BizProjectWbsNode r2 = node(11L, PROJECT_ID, null, 1, "PH-002");
            when(wbsNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r1, r2));

            assertThat(service.getTree(PROJECT_ID)).hasSize(2);
        }

        @Test
        @DisplayName("getTree：空结果返回空列表而非 null")
        void getTree_empty_returnsEmptyList() {
            when(wbsNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            assertThat(service.getTree(PROJECT_ID)).isEmpty();
        }

        @Test
        @DisplayName("getById：节点不存在时抛业务异常并带出 ID")
        void getById_notFound_throws() {
            when(wbsNodeMapper.selectById(404L)).thenReturn(null);

            assertThatThrownBy(() -> service.getById(404L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBS 节点不存在")
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("getById：存在时回填 projectName")
        void getById_success_fillsProjectName() {
            BizProjectWbsNode n = node(10L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(n);
            when(projectMapper.selectBatchIds(any())).thenReturn(List.of(project(PROJECT_ID, "城南市政道路改造")));

            BizProjectWbsNode result = service.getById(10L);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getProjectName()).isEqualTo("城南市政道路改造");
        }

        @Test
        @DisplayName("listChildren：透传查询直接子节点，不做树装配")
        void listChildren_delegatesToMapper() {
            BizProjectWbsNode c1 = node(11L, PROJECT_ID, 10L, 2, "WP-001");
            when(wbsNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(c1));

            List<BizProjectWbsNode> children = service.listChildren(10L);

            assertThat(children).hasSize(1);
            assertThat(children.get(0).getParentId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("listForSelect：projectId 为空直接拒绝")
        void listForSelect_nullProjectId_throws() {
            assertThatThrownBy(() -> service.listForSelect(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");
        }

        @Test
        @DisplayName("listForSelect：返回 ACTIVE 节点扁平列表，children 保持空")
        void listForSelect_success_returnsFlatList() {
            BizProjectWbsNode n1 = node(10L, PROJECT_ID, null, 1, "PH-001");
            BizProjectWbsNode n2 = node(11L, PROJECT_ID, 10L, 2, "WP-001");
            when(wbsNodeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(n1, n2));

            List<BizProjectWbsNode> list = service.listForSelect(PROJECT_ID);

            assertThat(list).hasSize(2);
            assertThat(list).allSatisfy(n -> assertThat(n.getChildren()).isEmpty());
        }
    }

    // =====================================================================
    // create：基础校验 + 层级推导 + 编号唯一 + 深度上限
    // =====================================================================

    @Nested
    @DisplayName("create：校验 / 层级自洽 / 编号唯一 / MAX_LEVEL=5")
    class CreateTests {

        @Test
        @DisplayName("校验：projectId 为空拒绝")
        void create_nullProjectId_throws() {
            BizProjectWbsNode n = node(null, null, null, 1, "PH-001");

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID不能为空");

            verify(wbsNodeMapper, never()).insert(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("校验：编号为空白拒绝")
        void create_blankCode_throws() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "   ");

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBS 节点编号不能为空");
        }

        @Test
        @DisplayName("校验：名称为空白拒绝")
        void create_blankName_throws() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "PH-001");
            n.setNodeName("");

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBS 节点名称不能为空");
        }

        @Test
        @DisplayName("校验：编号超过 50 字符拒绝（51 字符为最小越界样本）")
        void create_codeTooLong_throws() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "C".repeat(51));

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("编号长度不得超过 50");
        }

        @Test
        @DisplayName("校验：编号恰好 50 字符放行（边界不被误杀）")
        void create_codeExactly50_passes() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "C".repeat(50));
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.insert(any(BizProjectWbsNode.class))).thenReturn(1);

            service.create(n);

            verify(wbsNodeMapper).insert(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("校验：名称超过 200 字符拒绝")
        void create_nameTooLong_throws() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "PH-001");
            n.setNodeName("N".repeat(201));

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("名称长度不得超过 200");
        }

        @Test
        @DisplayName("不变量⑤：结束日期早于开始日期拒绝，错误信息带出编号")
        void create_endDateBeforeStartDate_throws() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "PH-001");
            n.setStartDate(LocalDate.of(2026, 9, 10));
            n.setEndDate(LocalDate.of(2026, 9, 9));

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("结束日期不得早于开始日期")
                    .hasMessageContaining("PH-001");

            verify(wbsNodeMapper, never()).insert(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("不变量⑤：起止日期相同放行（零工期合法）")
        void create_sameStartAndEnd_passes() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "PH-001");
            n.setStartDate(LocalDate.of(2026, 9, 10));
            n.setEndDate(LocalDate.of(2026, 9, 10));
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.insert(any(BizProjectWbsNode.class))).thenReturn(1);

            service.create(n);

            verify(wbsNodeMapper).insert(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("项目不存在时拒绝，不再往下推导层级")
        void create_projectNotExists_throws() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "PH-001");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目不存在")
                    .hasMessageContaining("1");

            verify(wbsNodeMapper, never()).insert(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("不变量②：根节点 level 强制为 1，调用方传入的 99 被忽略")
        void create_rootLevelForcedToOne_callerValueIgnored() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 99, "PH-001");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.insert(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode saved = service.create(n);

            assertThat(saved.getNodeLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("不变量②：子节点 level = 父 level + 1，调用方传入的 1 被忽略")
        void create_childLevelDerivedFromParent_callerValueIgnored() {
            BizProjectWbsNode parent = node(10L, PROJECT_ID, null, 3, "PH-001");
            BizProjectWbsNode n = node(null, PROJECT_ID, 10L, 1, "WP-001");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.selectById(10L)).thenReturn(parent);
            when(wbsNodeMapper.insert(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode saved = service.create(n);

            assertThat(saved.getNodeLevel()).isEqualTo(4);
            assertThat(saved.getParentId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("脏数据兜底：父节点 level 为 null 时按 1 计算，子节点落 level 2")
        void create_parentLevelNull_childBecomesLevel2() {
            BizProjectWbsNode parent = node(10L, PROJECT_ID, null, null, "PH-001");
            BizProjectWbsNode n = node(null, PROJECT_ID, 10L, null, "WP-001");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.selectById(10L)).thenReturn(parent);
            when(wbsNodeMapper.insert(any(BizProjectWbsNode.class))).thenReturn(1);

            assertThat(service.create(n).getNodeLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("父节点不存在时拒绝（getById 兜底），不落库")
        void create_parentNotFound_throws() {
            BizProjectWbsNode n = node(null, PROJECT_ID, 404L, null, "WP-001");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.selectById(404L)).thenReturn(null);

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBS 节点不存在");

            verify(wbsNodeMapper, never()).insert(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("跨项目挂载拒绝：父节点属于别的项目")
        void create_parentFromAnotherProject_throws() {
            BizProjectWbsNode parent = node(10L, 2L, null, 1, "PH-001");
            BizProjectWbsNode n = node(null, PROJECT_ID, 10L, null, "WP-001");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.selectById(10L)).thenReturn(parent);

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("父节点不属于同一项目");

            verify(wbsNodeMapper, never()).insert(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("MAX_LEVEL：父节点已是第 5 级时拒绝创建第 6 级")
        void create_sixthLevel_throws() {
            BizProjectWbsNode parent = node(10L, PROJECT_ID, null, 5, "L5-001");
            BizProjectWbsNode n = node(null, PROJECT_ID, 10L, null, "L6-001");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.selectById(10L)).thenReturn(parent);

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBS 层级不得超过 5 级");

            verify(wbsNodeMapper, never()).insert(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("MAX_LEVEL 边界：父节点第 4 级时允许创建第 5 级")
        void create_fifthLevel_allowed() {
            BizProjectWbsNode parent = node(10L, PROJECT_ID, null, 4, "L4-001");
            BizProjectWbsNode n = node(null, PROJECT_ID, 10L, null, "L5-001");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.selectById(10L)).thenReturn(parent);
            when(wbsNodeMapper.insert(any(BizProjectWbsNode.class))).thenReturn(1);

            assertThat(service.create(n).getNodeLevel()).isEqualTo(5);
        }

        @Test
        @DisplayName("不变量①：同项目内编号重复拒绝")
        void create_duplicateCode_throws() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "PH-001");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> service.create(n))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBS 节点编号已存在")
                    .hasMessageContaining("PH-001");

            verify(wbsNodeMapper, never()).insert(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("默认值：status 空白补 ACTIVE，sortOrder 为空时追加到同层末尾")
        void create_defaultsStatusAndAppendsSortOrder() {
            BizProjectWbsNode last = node(99L, PROJECT_ID, null, 1, "PH-000");
            last.setSortOrder(7);
            BizProjectWbsNode n = new BizProjectWbsNode();
            n.setProjectId(PROJECT_ID);
            n.setNodeCode("PH-001");
            n.setNodeName("阶段一");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(last);
            when(wbsNodeMapper.insert(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode saved = service.create(n);

            assertThat(saved.getStatus()).isEqualTo(ProjectWbsNodeService.STATUS_ACTIVE);
            assertThat(saved.getSortOrder()).isEqualTo(8);
        }

        @Test
        @DisplayName("默认值：同层无节点时 sortOrder 从 1 起")
        void create_firstNodeInLevel_sortOrderStartsAtOne() {
            BizProjectWbsNode n = new BizProjectWbsNode();
            n.setProjectId(PROJECT_ID);
            n.setNodeCode("PH-001");
            n.setNodeName("阶段一");
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(wbsNodeMapper.insert(any(BizProjectWbsNode.class))).thenReturn(1);

            assertThat(service.create(n).getSortOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("默认值：调用方显式指定 status 与 sortOrder 时不被覆盖")
        void create_explicitStatusAndSortOrder_preserved() {
            BizProjectWbsNode n = node(null, PROJECT_ID, null, 1, "PH-001");
            n.setStatus(ProjectWbsNodeService.STATUS_INACTIVE);
            n.setSortOrder(42);
            when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "P"));
            when(wbsNodeMapper.insert(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode saved = service.create(n);

            assertThat(saved.getStatus()).isEqualTo(ProjectWbsNodeService.STATUS_INACTIVE);
            assertThat(saved.getSortOrder()).isEqualTo(42);
            // 显式给了 sortOrder 就不该再去查同层末尾
            verify(wbsNodeMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        }
    }

    // =====================================================================
    // update：字段级合并 + 环检测 + 深度上限
    // =====================================================================

    @Nested
    @DisplayName("update/ move：字段合并/环检测/深度上限")
    class UpdateMoveTests {

        @Test
        @DisplayName("update：节点不存在抛异常")
        void update_notFound_throws() {
            when(wbsNodeMapper.selectById(404L)).thenReturn(null);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setNodeName("新名称");

            assertThatThrownBy(() -> service.update(404L, patch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBS 节点不存在");
        }

        @Test
        @DisplayName("update：修改名称生效")
        void update_rename_applies() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.updateById(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setNodeName("新阶段");

            service.update(10L, patch);

            assertThat(existing.getNodeName()).isEqualTo("新阶段");
        }

        @Test
        @DisplayName("不变量①：改成一个已被占用的新编号时拒绝")
        void update_codeChange_duplicate_throws() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setNodeCode("PH-002");

            assertThatThrownBy(() -> service.update(10L, patch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBS 节点编号已存在")
                    .hasMessageContaining("PH-002");

            // 编号必须在抛异常前保持原值，不能被写脏
            assertThat(existing.getNodeCode()).isEqualTo("PH-001");
            verify(wbsNodeMapper, never()).updateById(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("更新同一编号（未变）不触发唯一性检查")
        void update_sameCode_skipsUniquenessCheck() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.updateById(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setNodeCode("PH-001");

            service.update(10L, patch);

            verify(wbsNodeMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("更新唯一编号放行")
        void update_codeChange_unique_applies() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(wbsNodeMapper.updateById(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setNodeCode("PH-002");

            service.update(10L, patch);

            verify(wbsNodeMapper).updateById(argThat(e -> e.getNodeCode().equals("PH-002")));
        }

        @Test
        @DisplayName("description 用 != null 判定：null 被忽略，空串会被写入（与 name/code 的 isNotBlank 不同）")
        void update_description_nullIgnoredButEmptyStringApplied() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            existing.setDescription("原始描述");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.updateById(any(BizProjectWbsNode.class))).thenReturn(1);

            // patch.description == null → 跳过，保留原值
            service.update(10L, new BizProjectWbsNode());
            assertThat(existing.getDescription()).isEqualTo("原始描述");

            // patch.description == "" → 非 null 即写入，可被显式清空
            BizProjectWbsNode clearPatch = new BizProjectWbsNode();
            clearPatch.setDescription("");
            service.update(10L, clearPatch);
            assertThat(existing.getDescription()).isEmpty();
        }

        @Test
        @DisplayName("不变量⑤：日期变更后 endDate < startDate 拒绝")
        void update_dateRangeInvalid_throws() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            existing.setStartDate(LocalDate.of(2026, 9, 10));
            existing.setEndDate(LocalDate.of(2026, 9, 10));
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setEndDate(LocalDate.of(2026, 9, 9));

            assertThatThrownBy(() -> service.update(10L, patch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("结束日期不得早于开始日期")
                    .hasMessageContaining("PH-001");

            verify(wbsNodeMapper, never()).updateById(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("状态和排序号字段可被安全覆盖")
        void update_statusAndSortOrderApplied() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.updateById(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setStatus(ProjectWbsNodeService.STATUS_INACTIVE);
            patch.setSortOrder(77);

            service.update(10L, patch);

            assertThat(existing.getStatus()).isEqualTo(ProjectWbsNodeService.STATUS_INACTIVE);
            assertThat(existing.getSortOrder()).isEqualTo(77);
        }

        @Test
        @DisplayName("设计限制钉住：patch.parentId=null 无法把节点提升为根（moveNode 要求新父非 null）")
        void update_parentIdNull_cannotPromoteToRoot() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, 5L, 2, "WP-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.updateById(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setParentId(null);
            patch.setNodeName("改名");

            service.update(10L, patch);

            // moveNode 的进入条件是 patch.getParentId() != null && 与现值不同，
            // 故 parentId=null 的 patch 走不到移动分支：父节点保持 5L，层级保持 2。
            assertThat(existing.getParentId()).isEqualTo(5L);
            assertThat(existing.getNodeLevel()).isEqualTo(2);
            assertThat(existing.getNodeName()).isEqualTo("改名");
            // moveNode 未触发 → collectDescendantIds / maxSubtreeDepth / relevelDescendants 均未执行
            verify(wbsNodeMapper, never()).selectList(any(LambdaQueryWrapper.class));
            // 但 update() 结尾的落库照常执行一次
            verify(wbsNodeMapper, times(1)).updateById(existing);
        }

        @Test
        @DisplayName("不变量③：移动至自身拒绝")
        void update_moveToSelf_throws() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setParentId(10L);

            assertThatThrownBy(() -> service.update(10L, patch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能将节点挂载为自身的子节点");
        }

        @Test
        @DisplayName("不变量③：移动至直接后代拒绝（一次 BFS 遍历即可命中）")
        void update_moveToOwnChild_throwsCycle() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            BizProjectWbsNode child = node(11L, PROJECT_ID, 10L, 2, "WP-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.selectById(11L)).thenReturn(child);
            stubSelectListInOrder(List.of(child), NO_CHILDREN); // collectDescendantIds sees child then exits

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setParentId(11L);

            assertThatThrownBy(() -> service.update(10L, patch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("目标父节点是当前节点的下级，移动将产生环");

            verify(wbsNodeMapper, never()).updateById(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("跨项目挂载拒绝：moveNode 中复用 projectMapper.selectById")
        void update_moveCrossProject_throws() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            BizProjectWbsNode newParent = node(100L, 9L, null, 1, "PH-009");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.selectById(100L)).thenReturn(newParent);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setParentId(100L);

            assertThatThrownBy(() -> service.update(10L, patch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("父节点不属于同一项目");

            verify(wbsNodeMapper, never()).updateById(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("移动后子树将超过 MAX_LEVEL 拒绝（第 2 轮 depth 计算后命中）")
        void update_move_exceedsMaxLevel_inSecondRound_throws() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            BizProjectWbsNode newParent = node(100L, PROJECT_ID, null, 5, "L5-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.selectById(100L)).thenReturn(newParent);
            // collectDescendantIds(10): [NO_CHILDREN] breaks
            // maxSubtreeDepth(10): [NO_CHILDREN] returns depth=1; 6+1-1=6 > 5 throws
            stubSelectListInOrder(NO_CHILDREN, NO_CHILDREN);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setParentId(100L);

            assertThatThrownBy(() -> service.update(10L, patch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("移动后子树层级将超过 5 级");

            verify(wbsNodeMapper, never()).updateById(any(BizProjectWbsNode.class));
        }

        @Test
        @DisplayName("移动成功：层级变化时子树整体平移（level 3→2，子节点 4→3）")
        void update_moveUp_relevelsWholeSubtree() {
            // 忠实按层喂数据：每次 selectList 只返回「当前 frontier 的直接子节点」，
            // 与生产 SQL（in(parent_id, frontier)）行为一致。
            BizProjectWbsNode n11 = node(11L, PROJECT_ID, 10L, 4, "T-001");
            BizProjectWbsNode existing = node(10L, PROJECT_ID, 5L, 3, "WP-001");
            BizProjectWbsNode newParent = node(100L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.selectById(100L)).thenReturn(newParent);
            List<BizProjectWbsNode> level1 = List.of(n11);
            stubSelectListInOrder(
                    level1, NO_CHILDREN,   // 第 1 轮 collectDescendantIds：{11}
                    level1, NO_CHILDREN,   // 第 2 轮 maxSubtreeDepth：depth=2
                    level1, NO_CHILDREN    // 第 3 轮 relevelDescendants：delta=-1
            );
            when(wbsNodeMapper.updateById(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setParentId(100L);

            service.update(10L, patch);

            // 自身挂到新父并重算层级：newLevel = parent.level(1) + 1 = 2
            assertThat(existing.getParentId()).isEqualTo(100L);
            assertThat(existing.getNodeLevel()).isEqualTo(2);
            // 子树整体平移 delta = 2 - 3 = -1，子节点 4 → 3
            assertThat(n11.getNodeLevel()).isEqualTo(3);
            verify(wbsNodeMapper, times(1)).updateById(n11);
            verify(wbsNodeMapper, times(1)).updateById(existing);
        }

        @Test
        @DisplayName("MAX_LEVEL：三层子树（depth=4）移到 level 3 父节点下被拒（4+4-1=7>5）")
        void update_moveDeepSubtree_exceedsMaxLevel_throws() {
            // 10(L1) → 11(L2) → 12(L3) → 13(L4)，子树深度 4
            BizProjectWbsNode n11 = node(11L, PROJECT_ID, 10L, 2, "A");
            BizProjectWbsNode n12 = node(12L, PROJECT_ID, 11L, 3, "B");
            BizProjectWbsNode n13 = node(13L, PROJECT_ID, 12L, 4, "C");
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            BizProjectWbsNode newParent = node(100L, PROJECT_ID, null, 3, "P");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.selectById(100L)).thenReturn(newParent);
            stubSelectListInOrder(
                    List.of(n11), List.of(n12), List.of(n13), NO_CHILDREN,   // 第 1 轮
                    List.of(n11), List.of(n12), List.of(n13), NO_CHILDREN    // 第 2 轮 → depth=4
            );

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setParentId(100L);

            assertThatThrownBy(() -> service.update(10L, patch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("移动后子树层级将超过 5 级");

            // 深度上限命中即抛，不得进入层级平移，任何节点都不能被写脏
            verify(wbsNodeMapper, never()).updateById(any(BizProjectWbsNode.class));
            assertThat(n11.getNodeLevel()).isEqualTo(2);
            assertThat(existing.getParentId()).isNull();
        }

        @Test
        @DisplayName("不变量②：update 不复制 patch.nodeLevel，层级无法被调用方直接篡改")
        void update_patchLevelIgnored_levelStaysDerived() {
            BizProjectWbsNode existing = node(10L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(existing);
            when(wbsNodeMapper.updateById(any(BizProjectWbsNode.class))).thenReturn(1);

            BizProjectWbsNode patch = new BizProjectWbsNode();
            patch.setNodeLevel(99);
            patch.setNodeName("改名");

            service.update(10L, patch);

            // update() 的字段合并清单为 code/name/description/startDate/endDate/status/sortOrder，
            // 不含 nodeLevel；层级只能由 moveNode 依父节点重算，调用方无法直接指定。
            assertThat(existing.getNodeLevel()).isEqualTo(1);
            assertThat(existing.getNodeName()).isEqualTo("改名");
        }
    }

    // =====================================================================
    // delete / batchDelete：删除保护（存在子节点时拒绝） + 批量中断语义
    // =====================================================================

    @Nested
    @DisplayName("delete/batchDelete：删除保护与批量回滚")
    class DeleteBatchTests {

        @Test
        @DisplayName("delete：节点不存在直接抛业务异常并带出 ID")
        void delete_notFound_throws() {
            when(wbsNodeMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> service.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WBS 节点不存在")
                    .hasMessageContaining("999");

            verify(wbsNodeMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("delete：有直接子节点时拒绝删除，报错信息带出子节点数")
        void delete_hasChildren_throws() {
            BizProjectWbsNode node = node(10L, PROJECT_ID, null, 2, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(node);
            when(wbsNodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            assertThatThrownBy(() -> service.delete(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("该节点下还有 2 个子节点")
                    .hasMessageContaining("PH-001");

            verify(wbsNodeMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("delete：无任何子节点允许删除")
        void delete_noChildren_success() {
            BizProjectWbsNode node = node(10L, PROJECT_ID, null, 1, "PH-001");
            when(wbsNodeMapper.selectById(10L)).thenReturn(node);
            when(wbsNodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            doReturn(1).when(wbsNodeMapper).deleteById(anyLong());

            service.delete(10L);

            verify(wbsNodeMapper).deleteById(10L);
        }

        @Test
        @DisplayName("batchDelete：ID 列表为空拒绝，整体不执行单删")
        void batchDelete_emptyList_throws() {
            assertThatThrownBy(() -> service.batchDelete(Collections.emptyList()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ID 列表不能为空");
        }

        @Test
        @DisplayName("batchDelete：成功删除全部")
        void batchDelete_success_deletesAll() {
            BizProjectWbsNode n1 = node(10L, PROJECT_ID, null, 1, "PH-001");
            BizProjectWbsNode n2 = node(11L, PROJECT_ID, null, 1, "PH-002");
            List<Long> ids = List.of(10L, 11L);
            when(wbsNodeMapper.selectById(10L)).thenReturn(n1);
            when(wbsNodeMapper.selectById(11L)).thenReturn(n2);
            when(wbsNodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            doReturn(1).when(wbsNodeMapper).deleteById(anyLong());

            int deleted = service.batchDelete(ids);

            assertThat(deleted).isEqualTo(2);
            verify(wbsNodeMapper).deleteById(10L);
            verify(wbsNodeMapper).deleteById(11L);
        }

        @Test
        @DisplayName("batchDelete：任一节点被拦截则停止后续删除，已删数量按实际返回")
        void batchDelete_rollbackOnFirstFailure() {
            BizProjectWbsNode n1 = node(10L, PROJECT_ID, null, 1, "PH-001");
            BizProjectWbsNode n2 = node(11L, PROJECT_ID, null, 1, "PH-002");
            List<Long> ids = List.of(10L, 11L);
            when(wbsNodeMapper.selectById(10L)).thenReturn(n1);
            when(wbsNodeMapper.selectById(11L)).thenReturn(n2);
            AtomicInteger counter = new AtomicInteger(0);
            when(wbsNodeMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenAnswer(i -> counter.getAndIncrement() == 0 ? 0L : 1L); // first ok (n1), second fail (n2)
            doReturn(1).when(wbsNodeMapper).deleteById(anyLong());

            assertThatThrownBy(() -> service.batchDelete(ids))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("该节点下还有");

            verify(wbsNodeMapper).deleteById(10L);
            verify(wbsNodeMapper, never()).deleteById(11L);
        }
    }
}
