package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizContractPayTerm;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizContractPayTermMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContractPayTermService 单元测试（V2026_109，C2）
 */
@ExtendWith(MockitoExtension.class)
class ContractPayTermServiceTest {

    @Mock
    private BizContractPayTermMapper termMapper;

    @Mock
    private BizConstructionContractMapper contractMapper;

    @InjectMocks
    private ContractPayTermService service;

    private BizContractPayTerm term(String type, BigDecimal ratio, BigDecimal amount) {
        BizContractPayTerm t = new BizContractPayTerm();
        t.setContractCategory(BizContractPayTerm.CATEGORY_CONSTRUCTION);
        t.setContractId(20L);
        t.setTermType(type);
        t.setRatio(ratio);
        t.setAmount(amount);
        return t;
    }

    private BizConstructionContract contract() {
        BizConstructionContract c = new BizConstructionContract();
        c.setId(20L);
        c.setProjectId(10L);
        return c;
    }

    @Test
    @DisplayName("save - 施工合同条款：projectId 后端反查填充，不信任请求体")
    void save_construction_backfillsProjectId() {
        BizContractPayTerm t = term(BizContractPayTerm.TYPE_PROGRESS, new BigDecimal("80"), null);
        when(contractMapper.selectById(20L)).thenReturn(contract());

        service.save(t);

        assertThat(t.getProjectId()).isEqualTo(10L);
        assertThat(t.getStatus()).isEqualTo("ACTIVE");
        verify(termMapper).insert(t);
    }

    @Test
    @DisplayName("save - 比例与金额全空拒绝；质保金比例超 3% 拒绝（财建183号）")
    void save_validationRejections() {
        BizContractPayTerm empty = term(BizContractPayTerm.TYPE_PROGRESS, null, null);
        assertThatThrownBy(() -> service.save(empty)).hasMessageContaining("至少填一项");

        BizContractPayTerm over = term(BizContractPayTerm.TYPE_RETENTION, new BigDecimal("5"), null);
        when(contractMapper.selectById(20L)).thenReturn(contract());
        assertThatThrownBy(() -> service.save(over)).hasMessageContaining("3%");

        BizContractPayTerm badRule = term(BizContractPayTerm.TYPE_PREPAY, null, new BigDecimal("100"));
        badRule.setDeductRule("WHENEVER");
        when(contractMapper.selectById(20L)).thenReturn(contract());
        assertThatThrownBy(() -> service.save(badRule)).hasMessageContaining("扣回规则");

        verify(termMapper, never()).insert(any(BizContractPayTerm.class));
    }

    @Test
    @DisplayName("update - 归属不可改绑（类别/合同/项目强制沿用存量）")
    void update_cannotRebindOwnership() {
        BizContractPayTerm existing = term(BizContractPayTerm.TYPE_RETENTION, new BigDecimal("3"), null);
        existing.setId(5L);
        existing.setProjectId(10L);
        when(termMapper.selectById(5L)).thenReturn(existing);

        BizContractPayTerm forged = new BizContractPayTerm();
        forged.setId(5L);
        forged.setContractCategory("PURCHASE");
        forged.setContractId(999L);
        forged.setTermType(BizContractPayTerm.TYPE_RETENTION);
        forged.setRatio(new BigDecimal("3"));

        service.update(forged);

        assertThat(forged.getContractCategory()).isEqualTo("CONSTRUCTION");
        assertThat(forged.getContractId()).isEqualTo(20L);
        assertThat(forged.getProjectId()).isEqualTo(10L);
        verify(termMapper).updateById(forged);
    }

    @Test
    @DisplayName("update - 条款不存在拒绝（404）")
    void update_missing_throws404() {
        when(termMapper.selectById(9L)).thenReturn(null);
        BizContractPayTerm t = term(BizContractPayTerm.TYPE_FINAL, null, new BigDecimal("1000"));
        t.setId(9L);
        assertThatThrownBy(() -> service.update(t))
                .isInstanceOf(BusinessException.class).hasMessageContaining("条款不存在");
    }

    @Test
    @DisplayName("calcRetentionAmount - 按比例计提并封顶审定额；无条款/无参数返回 null")
    void calcRetention_various() {
        when(termMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(term(BizContractPayTerm.TYPE_RETENTION, new BigDecimal("3"), null)));

        assertThat(service.calcRetentionAmount("CONSTRUCTION", 20L, new BigDecimal("100000")))
                .isEqualByComparingTo("3000.00");
        // 计提超审定额 → 封顶
        assertThat(service.calcRetentionAmount("CONSTRUCTION", 20L, new BigDecimal("100")))
                .isEqualByComparingTo("100.00");

        when(termMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThat(service.calcRetentionAmount("CONSTRUCTION", 20L, new BigDecimal("100000"))).isNull();
    }

    @Test
    @DisplayName("suggestPrepayDeduct - 有 PREPAY 条款按金额建议；无条款为 0")
    void suggestPrepay() {
        when(termMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(term(BizContractPayTerm.TYPE_PREPAY, null, new BigDecimal("300000"))));
        assertThat(service.suggestPrepayDeduct("CONSTRUCTION", 20L)).isEqualByComparingTo("300000");

        when(termMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThat(service.suggestPrepayDeduct("CONSTRUCTION", 20L)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("list - 类别必填、合同ID必填")
    void list_guards() {
        assertThatThrownBy(() -> service.list(null, 20L)).hasMessageContaining("合同类别");
        assertThatThrownBy(() -> service.list("CONSTRUCTION", null)).hasMessageContaining("合同ID");
    }
}
