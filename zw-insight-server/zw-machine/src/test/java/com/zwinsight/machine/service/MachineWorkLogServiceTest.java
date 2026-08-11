package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.domain.BizMachineWorkLog;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
import com.zwinsight.machine.mapper.BizMachineWorkLogMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MachineWorkLogService（机械工作日志）单元测试
 *
 * 覆盖场景:
 * - 分页：机械名称无匹配早返回
 * - 新增的在场机械校验与 DRAFT 默认状态
 * - 更新/删除的草稿状态校验
 */
@ExtendWith(MockitoExtension.class)
class MachineWorkLogServiceTest {

    @Mock
    private BizMachineWorkLogMapper workLogMapper;

    @Mock
    private BizMachineLedgerMapper ledgerMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private MachineWorkLogService machineWorkLogService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizMachineWorkLog.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizMachineLedger.class);
    }

    private BizMachineLedger ledger(String status) {
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setId(10L);
        ledger.setStatus(status);
        return ledger;
    }

    private BizMachineWorkLog workLog(String status) {
        BizMachineWorkLog workLog = new BizMachineWorkLog();
        workLog.setId(1L);
        workLog.setMachineId(10L);
        workLog.setStatus(status);
        return workLog;
    }

    @Test
    @DisplayName("分页：机械名称无匹配时早返回空页")
    void page_machineNameNoMatch_returnsEmptyEarly() {
        when(ledgerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        PageResult<BizMachineWorkLog> result =
                machineWorkLogService.page(1, 10, null, null, "不存在的机械", null);

        assertThat(result.getTotal()).isZero();
        verify(workLogMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("分页：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizMachineWorkLog> stubPage = new Page<>(1, 10);
        stubPage.setTotal(4);
        when(workLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizMachineWorkLog> result =
                machineWorkLogService.page(1, 10, 10L, 20L, null, "2026-07-01");

        assertThat(result.getTotal()).isEqualTo(4);
    }

    @Test
    @DisplayName("新增工作日志：机械不存在抛异常")
    void save_machineNotFound_throwsException() {
        BizMachineWorkLog workLog = workLog(null);
        workLog.setMachineId(999L);
        when(ledgerMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> machineWorkLogService.save(workLog))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机械不存在");
    }

    @Test
    @DisplayName("新增工作日志：非在场机械拒绝记录")
    void save_notInField_rejected() {
        BizMachineWorkLog workLog = workLog(null);
        when(ledgerMapper.selectById(10L)).thenReturn(ledger("OUT_FIELD"));

        assertThatThrownBy(() -> machineWorkLogService.save(workLog))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅在场机械可记录工作日志");
    }

    @Test
    @DisplayName("新增工作日志：在场机械保存为 DRAFT")
    void save_inField_setsDraft() {
        BizMachineWorkLog workLog = workLog(null);
        when(ledgerMapper.selectById(10L)).thenReturn(ledger("IN_FIELD"));

        machineWorkLogService.save(workLog);

        assertThat(workLog.getStatus()).isEqualTo("DRAFT");
        verify(workLogMapper).insert(workLog);
    }

    @Test
    @DisplayName("更新工作日志：不存在抛异常")
    void update_notFound_throwsException() {
        BizMachineWorkLog workLog = workLog("DRAFT");
        when(workLogMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> machineWorkLogService.update(workLog))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作日志不存在");
    }

    @Test
    @DisplayName("更新工作日志：非草稿状态拒绝编辑")
    void update_nonDraft_rejected() {
        BizMachineWorkLog workLog = workLog("DRAFT");
        when(workLogMapper.selectById(1L)).thenReturn(workLog("SUBMITTED"));

        assertThatThrownBy(() -> machineWorkLogService.update(workLog))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可编辑");
    }

    @Test
    @DisplayName("更新工作日志：草稿状态正常更新")
    void update_draft_success() {
        BizMachineWorkLog workLog = workLog("DRAFT");
        when(workLogMapper.selectById(1L)).thenReturn(workLog("DRAFT"));

        machineWorkLogService.update(workLog);

        verify(workLogMapper).updateById(workLog);
    }

    @Test
    @DisplayName("删除工作日志：不存在抛异常")
    void delete_notFound_throwsException() {
        when(workLogMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> machineWorkLogService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作日志不存在");
    }

    @Test
    @DisplayName("删除工作日志：非草稿状态拒绝删除")
    void delete_nonDraft_rejected() {
        when(workLogMapper.selectById(1L)).thenReturn(workLog("SUBMITTED"));

        assertThatThrownBy(() -> machineWorkLogService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿状态可删除");
    }

    @Test
    @DisplayName("删除工作日志：草稿状态正常删除")
    void delete_draft_success() {
        when(workLogMapper.selectById(1L)).thenReturn(workLog("DRAFT"));

        machineWorkLogService.delete(1L);

        verify(workLogMapper).deleteById(1L);
    }

    @Test
    @DisplayName("编辑已结算日志拒绝（B4：结算审批后 status 仍 DRAFT 但 settlementStatus=SETTLED，台班/工作量是结算依据）")
    void update_settled_rejected() {
        BizMachineWorkLog log = workLog("DRAFT");
        log.setSettlementStatus("SETTLED");
        when(workLogMapper.selectById(1L)).thenReturn(log);

        assertThatThrownBy(() -> machineWorkLogService.update(log))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结算的工作日志不可编辑");

        verify(workLogMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("删除已结算日志拒绝（B4）")
    void delete_settled_rejected() {
        BizMachineWorkLog log = workLog("DRAFT");
        log.setSettlementStatus("SETTLED");
        when(workLogMapper.selectById(1L)).thenReturn(log);

        assertThatThrownBy(() -> machineWorkLogService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结算的工作日志不可删除");

        verify(workLogMapper, never()).deleteById(any());
    }
}
