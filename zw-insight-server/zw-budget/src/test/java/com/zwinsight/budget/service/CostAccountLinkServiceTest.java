package com.zwinsight.budget.service;

import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.domain.BizCostAccountLink;
import com.zwinsight.budget.mapper.BizCostAccountLinkMapper;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成本账户绑定服务单元测试。
 * <p>
 * 绑定是「歧义不猜」原则的落地手段：一个科目多个账户时，只有业务人员显式指定，
 * 归集才敢把钱记进去。因此这里重点验证：
 * </p>
 * <ol>
 *   <li><b>跨项目绑定必须拦住</b>——否则别的项目的钱会记到本项目账户，账彻底乱掉</li>
 *   <li><b>幂等</b>——重复绑定返回既有记录而非报错（前端重复提交/批量绑定常见）</li>
 *   <li><b>解绑不冲销已记账</b>——已记的账是当时正确决策的结果，冲销需显式反向操作</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CostAccountLinkService 账户绑定")
class CostAccountLinkServiceTest {

    @Mock private BizCostAccountLinkMapper linkMapper;
    @Mock private BizCostAccountMapper costAccountMapper;

    @InjectMocks private CostAccountLinkService linkService;

    private BizCostAccount account(long id, Long projectId, String category) {
        BizCostAccount a = new BizCostAccount();
        a.setId(id);
        a.setProjectId(projectId);
        a.setAccountCode("01.02");
        a.setAccountName("混凝土");
        a.setCostCategory(category);
        a.setStatus("ACTIVE");
        return a;
    }

    private BizCostAccountLink linkRequest(Long projectId, long accountId, String sourceType, String sourceId) {
        BizCostAccountLink l = new BizCostAccountLink();
        l.setProjectId(projectId);
        l.setAccountId(accountId);
        l.setSourceType(sourceType);
        l.setSourceId(sourceId);
        return l;
    }

    @Nested
    @DisplayName("新增绑定")
    class Bind {

        @Test
        @DisplayName("正常绑定：projectId 以账户实际归属为准回填，防止调用方传错")
        void bindsAndBackfillsProjectId() {
            when(costAccountMapper.selectById(1001L)).thenReturn(account(1001L, 100L, "MATERIAL"));
            when(linkMapper.selectBySource(1001L, "PURCHASE_CONTRACT", "555")).thenReturn(null);

            // 调用方没传 projectId
            BizCostAccountLink result = linkService.bind(linkRequest(null, 1001L, "PURCHASE_CONTRACT", "555"));

            assertThat(result.getProjectId()).isEqualTo(100L);
            ArgumentCaptor<BizCostAccountLink> captor = ArgumentCaptor.forClass(BizCostAccountLink.class);
            verify(linkMapper).insert(captor.capture());
            assertThat(captor.getValue().getProjectId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("跨项目绑定坚决拦住：否则别的项目的钱会记到本项目账户")
        void rejectsCrossProjectBinding() {
            // 账户属于项目 200，请求方声称项目 100
            when(costAccountMapper.selectById(2001L)).thenReturn(account(2001L, 200L, "MATERIAL"));

            assertThatThrownBy(() -> linkService.bind(linkRequest(100L, 2001L, "PURCHASE_CONTRACT", "555")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不属于该项目")
                    .hasMessageContaining("禁止跨项目绑定");

            verify(linkMapper, never()).insert(any());
        }

        @Test
        @DisplayName("账户不存在：拒绝绑定")
        void rejectsMissingAccount() {
            when(costAccountMapper.selectById(9999L)).thenReturn(null);

            assertThatThrownBy(() -> linkService.bind(linkRequest(100L, 9999L, "PURCHASE_CONTRACT", "555")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("成本账户不存在");
        }

        @Test
        @DisplayName("重复绑定幂等：返回既有记录，不报错不重复插入")
        void duplicateBindIsIdempotent() {
            BizCostAccountLink existing = linkRequest(100L, 1001L, "PURCHASE_CONTRACT", "555");
            existing.setId(77L);
            when(costAccountMapper.selectById(1001L)).thenReturn(account(1001L, 100L, "MATERIAL"));
            when(linkMapper.selectBySource(1001L, "PURCHASE_CONTRACT", "555")).thenReturn(existing);

            BizCostAccountLink result = linkService.bind(linkRequest(100L, 1001L, "PURCHASE_CONTRACT", "555"));

            assertThat(result.getId()).isEqualTo(77L);
            verify(linkMapper, never()).insert(any());
        }

        @Test
        @DisplayName("必填校验：账户ID/来源类型/来源ID 缺一不可")
        void validatesMandatoryFields() {
            assertThatThrownBy(() -> linkService.bind(null))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("绑定信息");

            // accountId 为 null
            BizCostAccountLink noAccount = linkRequest(100L, 0L, "PURCHASE_CONTRACT", "555");
            noAccount.setAccountId(null);
            assertThatThrownBy(() -> linkService.bind(noAccount))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("成本账户ID");

            BizCostAccountLink noType = linkRequest(100L, 1001L, "", "555");
            assertThatThrownBy(() -> linkService.bind(noType))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("来源类型");

            BizCostAccountLink noSourceId = linkRequest(100L, 1001L, "PURCHASE_CONTRACT", " ");
            assertThatThrownBy(() -> linkService.bind(noSourceId))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("来源业务ID");
        }

        @Test
        @DisplayName("超长字段拒绝（列宽 sourceType 50 / sourceId 100）")
        void rejectsOverlongFields() {
            BizCostAccountLink longType = linkRequest(100L, 1001L, "X".repeat(51), "555");
            assertThatThrownBy(() -> linkService.bind(longType))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("50");

            BizCostAccountLink longId = linkRequest(100L, 1001L, "PURCHASE_CONTRACT", "Y".repeat(101));
            assertThatThrownBy(() -> linkService.bind(longId))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("100");
        }
    }

    @Nested
    @DisplayName("批量绑定")
    class BatchBind {

        @Test
        @DisplayName("返回实际新建数：已存在的按幂等跳过不计入")
        void countsOnlyNewlyCreated() {
            when(costAccountMapper.selectById(1001L)).thenReturn(account(1001L, 100L, "MATERIAL"));
            // 第一条已存在，第二条是新的
            BizCostAccountLink existing = linkRequest(100L, 1001L, "PURCHASE_CONTRACT", "555");
            existing.setId(77L);
            when(linkMapper.selectBySource(1001L, "PURCHASE_CONTRACT", "555")).thenReturn(existing);
            when(linkMapper.selectBySource(1001L, "PURCHASE_CONTRACT", "556")).thenReturn(null);

            int created = linkService.bindBatch(List.of(
                    linkRequest(100L, 1001L, "PURCHASE_CONTRACT", "555"),
                    linkRequest(100L, 1001L, "PURCHASE_CONTRACT", "556")));

            assertThat(created).isEqualTo(1);
            verify(linkMapper).insert(any(BizCostAccountLink.class));
        }

        @Test
        @DisplayName("空列表拒绝（避免无意义的空事务）")
        void rejectsEmptyBatch() {
            assertThatThrownBy(() -> linkService.bindBatch(List.of()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不能为空");
            assertThatThrownBy(() -> linkService.bindBatch(null))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不能为空");
        }
    }

    @Nested
    @DisplayName("解绑")
    class Unbind {

        @Test
        @DisplayName("解绑只删绑定关系，不触碰已记账金额")
        void unbindDoesNotTouchAmounts() {
            BizCostAccountLink existing = linkRequest(100L, 1001L, "PURCHASE_CONTRACT", "555");
            existing.setId(77L);
            when(linkMapper.selectById(77L)).thenReturn(existing);

            linkService.unbind(77L);

            verify(linkMapper).deleteById(77L);
            // 关键：解绑不得调用任何记账逻辑（金额冲销必须是显式的反向操作）
            verify(costAccountMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("解绑不存在的记录：明确报错而非静默成功")
        void unbindMissingThrows() {
            when(linkMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> linkService.unbind(999L))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
        }
    }

    @Nested
    @DisplayName("查询")
    class Queries {

        @Test
        @DisplayName("按项目/账户查询透传参数")
        void listQueriesPassThrough() {
            when(linkMapper.selectByProject(100L)).thenReturn(List.of());
            when(linkMapper.selectByAccount(1001L)).thenReturn(List.of());

            assertThat(linkService.listByProject(100L)).isEmpty();
            assertThat(linkService.listByAccount(1001L)).isEmpty();
            verify(linkMapper).selectByProject(100L);
            verify(linkMapper).selectByAccount(1001L);
        }

        @Test
        @DisplayName("缺 ID 拒绝查询")
        void listQueriesRequireIds() {
            assertThatThrownBy(() -> linkService.listByProject(null))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("项目ID");
            assertThatThrownBy(() -> linkService.listByAccount(null))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("成本账户ID");
        }
    }
}
