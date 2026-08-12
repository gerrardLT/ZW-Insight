package com.zwinsight.contract.service;

import com.zwinsight.contract.domain.BizContractExpiryLog;
import com.zwinsight.contract.dto.ContractExpiryDTO;
import com.zwinsight.contract.mapper.BizContractExpiryLogMapper;
import com.zwinsight.message.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * ContractExpiryService 通知链路单元测试（P1 EXP-07/EXP-08 补测，2026-08-13）
 * <p>
 * 覆盖 sendExpiryNotification 三分支：发送成功记 SENT 日志（EXP-08 断言强化）、
 * 发送失败记 FAILED 日志且不中断任务（EXP-07）、无负责人跳过通知。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ContractExpiryNotifyTest {

    @Mock
    private MessageService messageService;

    @Mock
    private BizContractExpiryLogMapper expiryLogMapper;

    private ContractExpiryService service;

    private final LocalDate today = LocalDate.of(2026, 8, 13);

    @BeforeEach
    void setUp() {
        service = new ContractExpiryService(null, messageService, null, expiryLogMapper);
    }

    private ContractExpiryDTO expiringContract() {
        ContractExpiryDTO dto = new ContractExpiryDTO();
        dto.setId(100L);
        dto.setContractCode("CG-2026-001");
        dto.setContractName("钢材采购合同");
        dto.setContractCategory("MATERIAL");
        dto.setCounterpartName("某某建材公司");
        dto.setStatus("ACTIVE");
        dto.setResponsibleUserId(7L);
        dto.setContractTable("biz_purchase_contract");
        dto.setEndDate(today.plusDays(10));
        return dto;
    }

    @Test
    @DisplayName("发送成功 → 落 SENT 日志且字段完整（P1 EXP-08 断言强化）")
    void sendSuccess_savesSentLog() {
        service.sendExpiryNotification(expiringContract(), ContractExpiryService.LEVEL_UPCOMING, today);

        ArgumentCaptor<BizContractExpiryLog> captor = ArgumentCaptor.forClass(BizContractExpiryLog.class);
        verify(expiryLogMapper).insert(captor.capture());
        BizContractExpiryLog log = captor.getValue();
        assertThat(log.getNotifyStatus()).isEqualTo("SENT");
        assertThat(log.getContractId()).isEqualTo(100L);
        assertThat(log.getLevel()).isEqualTo(ContractExpiryService.LEVEL_UPCOMING);
        assertThat(log.getRemainingDays()).isEqualTo(10);
        assertThat(log.getNotifyUserId()).isEqualTo(7L);
        assertThat(log.getContractCode()).isEqualTo("CG-2026-001");
    }

    @Test
    @DisplayName("通知发送失败 → 记 FAILED 日志且不抛异常中断任务（P1 EXP-07）")
    void sendFailure_savesFailedLogWithoutThrowing() {
        doThrow(new RuntimeException("消息服务不可用"))
                .when(messageService)
                .sendMessage(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());

        assertThatCode(() ->
                service.sendExpiryNotification(expiringContract(), ContractExpiryService.LEVEL_URGENT, today))
                .as("发送失败不应中断到期扫描任务")
                .doesNotThrowAnyException();

        ArgumentCaptor<BizContractExpiryLog> captor = ArgumentCaptor.forClass(BizContractExpiryLog.class);
        verify(expiryLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getNotifyStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getLevel()).isEqualTo(ContractExpiryService.LEVEL_URGENT);
    }

    @Test
    @DisplayName("无负责人 → 跳过通知，不发消息不落日志")
    void noResponsibleUser_skips() {
        ContractExpiryDTO dto = expiringContract();
        dto.setResponsibleUserId(null);

        service.sendExpiryNotification(dto, ContractExpiryService.LEVEL_UPCOMING, today);

        verifyNoInteractions(messageService);
        verifyNoInteractions(expiryLogMapper);
    }
}
