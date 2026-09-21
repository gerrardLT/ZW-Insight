package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizBankAccount;
import com.zwinsight.finance.domain.BizBankAccountGroup;
import com.zwinsight.finance.mapper.BizBankAccountGroupMapper;
import com.zwinsight.finance.mapper.BizBankAccountMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BankAccountGroupService 单元测试（55_V2026_53 银行账户分组）
 * <p>核心断言：编码唯一、顶级/子级层级计算、删除前的子分组与关联账户保护、树形组装。</p>
 */
@ExtendWith(MockitoExtension.class)
class BankAccountGroupServiceTest {

    @Mock private BizBankAccountGroupMapper groupMapper;
    @Mock private BizBankAccountMapper accountMapper;

    @InjectMocks
    private BankAccountGroupService groupService;

    private BizBankAccountGroup group(Long id, Long parentId, Integer level, String code, String name) {
        BizBankAccountGroup g = new BizBankAccountGroup();
        g.setId(id);
        g.setParentId(parentId);
        g.setLevel(level);
        g.setGroupCode(code);
        g.setGroupName(name);
        g.setStatus("ENABLED");
        return g;
    }

    @Nested
    @DisplayName("save() 新增分组")
    class SaveTests {

        @Test
        @DisplayName("正常路径 — 顶级分组 level=1 parentId=0")
        void save_topLevel() {
            BizBankAccountGroup g = group(null, 0L, 1, "GRP-001", "集团公司");
            when(groupMapper.selectCount(any())).thenReturn(0L);

            groupService.save(g);

            assertThat(g.getLevel()).isEqualTo(1);
            assertThat(g.getStatus()).isEqualTo("ENABLED");
            verify(groupMapper).insert(g);
        }

        @Test
        @DisplayName("正常路径 — 子级 level=父级+1")
        void save_childLevel() {
            BizBankAccountGroup parent = group(10L, 0L, 1, "GRP-001", "集团公司");
            BizBankAccountGroup child = group(null, 10L, null, "GRP-001-01", "XX项目部");
            when(groupMapper.selectCount(any())).thenReturn(0L);
            when(groupMapper.selectById(10L)).thenReturn(parent);

            groupService.save(child);

            assertThat(child.getLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("异常路径 — 编码重复被拒绝")
        void save_duplicateCode_rejected() {
            BizBankAccountGroup g = group(null, 0L, 1, "GRP-DUP", "重复");
            when(groupMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> groupService.save(g))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已存在");
            verify(groupMapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("delete() 删除保护")
    class DeleteTests {

        @Test
        @DisplayName("异常路径 — 存在子分组拒绝删除")
        void delete_hasChildren_rejected() {
            when(groupMapper.selectById(1L)).thenReturn(group(1L, 0L, 1, "G", "集团"));
            when(groupMapper.selectCount(any())).thenReturn(2L);

            assertThatThrownBy(() -> groupService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("存在子分组");
            verify(groupMapper, never()).deleteById(any());
        }

        @Test
        @DisplayName("异常路径 — 有账户归属拒绝删除")
        void delete_hasAccounts_rejected() {
            when(groupMapper.selectById(1L)).thenReturn(group(1L, 0L, 1, "G", "集团"));
            when(groupMapper.selectCount(any())).thenReturn(0L); // 无子分组
            when(accountMapper.selectCount(any())).thenReturn(3L); // 有 3 个账户

            assertThatThrownBy(() -> groupService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("3个账户归属此分组");
            verify(groupMapper, never()).deleteById(any());
        }

        @Test
        @DisplayName("正常路径 — 无子分组无账户可删除")
        void delete_empty_ok() {
            when(groupMapper.selectById(1L)).thenReturn(group(1L, 0L, 1, "G", "集团"));
            when(groupMapper.selectCount(any())).thenReturn(0L);
            when(accountMapper.selectCount(any())).thenReturn(0L);

            groupService.delete(1L);

            verify(groupMapper).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("tree() 树形组装")
    class TreeTests {

        @Test
        @DisplayName("正常路径 — 两级分组挂载为父子树")
        void tree_nestsChildren() {
            BizBankAccountGroup parent = group(10L, 0L, 1, "G1", "集团");
            BizBankAccountGroup child = group(11L, 10L, 2, "G2", "项目部");
            when(groupMapper.selectList(any())).thenReturn(List.of(parent, child));

            List<BizBankAccountGroup> tree = groupService.tree();

            assertThat(tree).hasSize(1);
            assertThat(tree.get(0).getId()).isEqualTo(10L);
            assertThat(tree.get(0).getChildren()).hasSize(1);
            assertThat(tree.get(0).getChildren().get(0).getId()).isEqualTo(11L);
        }
    }
}
