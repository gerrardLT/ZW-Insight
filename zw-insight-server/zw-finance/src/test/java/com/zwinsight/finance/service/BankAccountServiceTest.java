package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizBankAccount;
import com.zwinsight.finance.mapper.BizBankAccountMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BankAccountService 单元测试（P0 FIN-BNK-01~06 补测，2026-08-12：原整体零测试）
 */
@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizBankAccount.class);
    }

    @Mock
    private BizBankAccountMapper bankAccountMapper;

    @InjectMocks
    private BankAccountService service;

    @Test
    @DisplayName("save - status 缺省置 1")
    void save_defaultsStatus() {
        BizBankAccount account = new BizBankAccount();
        service.save(account);

        assertThat(account.getStatus()).isEqualTo(1);
        verify(bankAccountMapper).insert(account);
    }

    @Test
    @DisplayName("save - 已有 status 不覆盖")
    void save_keepsExistingStatus() {
        BizBankAccount account = new BizBankAccount();
        account.setStatus(0);
        service.save(account);

        assertThat(account.getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("update - 存在则更新；不存在抛异常（FIN-BNK-05）")
    void update_variants() {
        BizBankAccount existing = new BizBankAccount();
        existing.setId(1L);
        when(bankAccountMapper.selectById(1L)).thenReturn(existing);
        BizBankAccount updated = new BizBankAccount();
        updated.setId(1L);
        service.update(updated);
        verify(bankAccountMapper).updateById(updated);

        when(bankAccountMapper.selectById(999L)).thenReturn(null);
        BizBankAccount missing = new BizBankAccount();
        missing.setId(999L);
        assertThatThrownBy(() -> service.update(missing))
                .isInstanceOf(BusinessException.class).hasMessageContaining("银行账户不存在");
    }

    @Test
    @DisplayName("delete - 存在则删除；不存在抛异常（P0 FIN-BNK-06：原实现任意 id 静默删除）")
    void delete_variants() {
        when(bankAccountMapper.selectById(1L)).thenReturn(new BizBankAccount());
        service.delete(1L);
        verify(bankAccountMapper).deleteById(1L);

        when(bankAccountMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("银行账户不存在");
        verify(bankAccountMapper, never()).deleteById(999L);
    }

    @Test
    @DisplayName("page - 分页透传（账户类型/项目筛选）")
    void page_delegates() {
        Page<BizBankAccount> page = new Page<>(1, 10);
        page.setRecords(List.of(new BizBankAccount()));
        page.setTotal(1);
        when(bankAccountMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<BizBankAccount> result = service.page(1, 10, "BASIC", 100L);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
    }
}
