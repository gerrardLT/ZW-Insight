package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.domain.BizMachineOilRecord;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
import com.zwinsight.machine.mapper.BizMachineOilRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MachineOilRecordService（机械加油记录）单元测试
 *
 * 覆盖场景:
 * - 分页查询
 * - 新增的在场机械校验（不存在/非在场拒绝）
 * - 删除
 */
@ExtendWith(MockitoExtension.class)
class MachineOilRecordServiceTest {

    @Mock
    private BizMachineOilRecordMapper oilRecordMapper;

    @Mock
    private BizMachineLedgerMapper ledgerMapper;

    @InjectMocks
    private MachineOilRecordService machineOilRecordService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizMachineOilRecord.class);
    }

    private BizMachineLedger ledger(String status) {
        BizMachineLedger ledger = new BizMachineLedger();
        ledger.setId(10L);
        ledger.setStatus(status);
        return ledger;
    }

    @Test
    @DisplayName("新增加油记录：机械不存在抛异常")
    void save_machineNotFound_throwsException() {
        BizMachineOilRecord record = new BizMachineOilRecord();
        record.setMachineId(999L);
        when(ledgerMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> machineOilRecordService.save(record))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机械不存在");
    }

    @Test
    @DisplayName("新增加油记录：非在场机械拒绝记录")
    void save_notInField_rejected() {
        BizMachineOilRecord record = new BizMachineOilRecord();
        record.setMachineId(10L);
        when(ledgerMapper.selectById(10L)).thenReturn(ledger("OUT_FIELD"));

        assertThatThrownBy(() -> machineOilRecordService.save(record))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅在场机械可记录加油");
    }

    @Test
    @DisplayName("新增加油记录：在场机械正常插入")
    void save_inField_success() {
        BizMachineOilRecord record = new BizMachineOilRecord();
        record.setMachineId(10L);
        when(ledgerMapper.selectById(10L)).thenReturn(ledger("IN_FIELD"));

        machineOilRecordService.save(record);

        verify(oilRecordMapper).insert(record);
    }

    @Test
    @DisplayName("删除加油记录：透传 deleteById")
    void delete_delegatesToMapper() {
        machineOilRecordService.delete(1L);

        verify(oilRecordMapper).deleteById(1L);
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizMachineOilRecord> stubPage = new Page<>(1, 10);
        stubPage.setTotal(3);
        when(oilRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizMachineOilRecord> result = machineOilRecordService.page(1, 10, 10L, 20L);

        assertThat(result.getTotal()).isEqualTo(3);
    }
}
