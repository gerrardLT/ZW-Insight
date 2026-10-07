package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.domain.BizTenderPersonBinding;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.mapper.BizDepositApplyMapper;
import com.zwinsight.tender.mapper.BizTenderPersonBindingMapper;
import com.zwinsight.tender.mapper.BizTenderRegisterMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

/**
 * 投标不变量单元测试（P1-M2 深度优化，蓝图 docs/deep-opt/02-tender.md）
 * 覆盖：
 *   - TI-1 投标保证金上限守卫（不得超预算 2%）
 *   - TI-2 拟派人员押证排他锁定与解锁
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("投标业务不变量 TI-1 & TI-2 测试")
class TenderInvariantsTest {

    @Mock private BizTenderPersonBindingMapper bindingMapper;
    @Mock private BizTenderRegisterMapper registerMapper;
    @Mock private BizProjectMapper projectMapper;
    @Mock private BizDepositApplyMapper depositApplyMapper;

    @InjectMocks private TenderPersonBindingService bindingService;
    @InjectMocks private DepositApplyService depositApplyService;

    private BizTenderRegister register;

    @BeforeEach
    void setUp() {
        register = new BizTenderRegister();
        register.setId(10L);
        register.setProjectId(100L);
        register.setStatus("REGISTERED");
    }

    // ==================== TI-1 法定保证金上限守卫 ====================

    @Test
    @DisplayName("TI-1: 申请保证金未超 2% 上限正常放行")
    void depositCap_withinTwoPercent_allowed() {
        BizProject project = new BizProject();
        project.setId(100L);
        project.setBudgetAmount(new BigDecimal("10000000")); // 1000万，2% = 20万
        when(projectMapper.selectById(100L)).thenReturn(project);

        BizDepositApply apply = new BizDepositApply();
        apply.setProjectId(100L);
        apply.setDepositAmount(new BigDecimal("150000")); // 15万 <= 20万

        depositApplyService.save(apply);

        verify(depositApplyMapper).insert(apply);
        assertThat(apply.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("TI-1: 申请保证金超 2% 法定上限拦截并明确提示")
    void depositCap_exceedTwoPercent_rejected() {
        BizProject project = new BizProject();
        project.setId(100L);
        project.setBudgetAmount(new BigDecimal("10000000")); // 1000万，2% = 20万
        when(projectMapper.selectById(100L)).thenReturn(project);

        BizDepositApply apply = new BizDepositApply();
        apply.setProjectId(100L);
        apply.setDepositAmount(new BigDecimal("250000")); // 25万 > 20万

        assertThatThrownBy(() -> depositApplyService.save(apply))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过项目预算金额 2% 法定上限");

        verify(depositApplyMapper, never()).insert(any());
    }

    // ==================== TI-2 人员押证排他锁定 ====================

    @Test
    @DisplayName("TI-2: 绑定人员证件正常锁定")
    void personBinding_happyPath_locked() {
        when(registerMapper.selectById(10L)).thenReturn(register);
        when(bindingMapper.countActiveLockOnOtherRegisters(500L, 10L)).thenReturn(0L);
        when(bindingMapper.selectCount(any())).thenReturn(0L);

        BizTenderPersonBinding binding = new BizTenderPersonBinding();
        binding.setPersonCertificateId(500L);
        binding.setPersonName("张建造");
        binding.setCertificateType("BUILDER");

        bindingService.bind(10L, binding);

        verify(bindingMapper).insert(binding);
        assertThat(binding.getStatus()).isEqualTo("LOCKED");
        assertThat(binding.getProjectId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("TI-2: 同一人证已在其他在投项目中锁定则拦截（防一证多投）")
    void personBinding_conflictOnOtherRegister_rejected() {
        when(registerMapper.selectById(10L)).thenReturn(register);
        // 发现其他在投项目中已有 1 处活跃锁定
        when(bindingMapper.countActiveLockOnOtherRegisters(500L, 10L)).thenReturn(1L);

        BizTenderPersonBinding binding = new BizTenderPersonBinding();
        binding.setPersonCertificateId(500L);
        binding.setPersonName("张建造");

        assertThatThrownBy(() -> bindingService.bind(10L, binding))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已在其他在投项目中被锁定")
                .hasMessageContaining("禁止一证多投");

        verify(bindingMapper, never()).insert(any());
    }

    @Test
    @DisplayName("TI-2: 开标后释放指定报名的全部押证人员")
    void releaseBindings_onOpenBid() {
        BizTenderPersonBinding b1 = new BizTenderPersonBinding();
        b1.setId(1L);
        b1.setStatus("LOCKED");
        when(bindingMapper.selectList(any())).thenReturn(List.of(b1));

        int count = bindingService.releaseAllByRegister(10L, "开标中标释放");

        assertThat(count).isEqualTo(1);
        assertThat(b1.getStatus()).isEqualTo("RELEASED");
        assertThat(b1.getReleasedReason()).isEqualTo("开标中标释放");
        verify(bindingMapper).updateById(b1);
    }
}
