package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysAuditLog;
import com.zwinsight.system.mapper.SysAuditLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计日志服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SysAuditLog.class);
    }

    @Mock private SysAuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @DisplayName("分页查询：返回 PageResult 结构")
    void testPage_returnsPageResult() {
        Page<SysAuditLog> page = new Page<>(1, 10);
        SysAuditLog log = new SysAuditLog();
        log.setTableName("sys_user");
        page.setRecords(List.of(log));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<SysAuditLog> result = auditLogService.page(1, 10, "sys_user", 100L);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("记录审计日志：字段完整落库")
    void testSave_fieldsCaptured() {
        auditLogService.save("biz_contract", 88L, "contract_amount", "100", "200");

        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        SysAuditLog saved = captor.getValue();
        assertThat(saved.getTableName()).isEqualTo("biz_contract");
        assertThat(saved.getRecordId()).isEqualTo(88L);
        assertThat(saved.getFieldName()).isEqualTo("contract_amount");
        assertThat(saved.getOldValue()).isEqualTo("100");
        assertThat(saved.getNewValue()).isEqualTo("200");
        assertThat(saved.getOperTime()).isNotNull();
    }
}
