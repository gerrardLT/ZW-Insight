package com.zwinsight.contract.service;

import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.contract.dto.ContractExpiryDTO;
import com.zwinsight.contract.mapper.BizContractExpiryLogMapper;
import com.zwinsight.contract.mapper.BizExpenseContractMapper;
import com.zwinsight.message.service.MessageService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.assertj.core.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 10: 到期提醒去重幂等性
 * <p>
 * 首次执行某合同+级别组合时应允许发送通知（shouldSendNotification = true），
 * 执行 markAsSent 后，第二次执行相同合同+级别应跳过（shouldSendNotification = false）。
 * </p>
 * <p>
 * <b>Validates: Requirements 5.5</b>
 * </p>
 *
 * Property 9: 到期提醒消息完整性
 * <p>
 * 对于任意合同到期通知，消息内容必须包含：合同编号、合同名称、供应商/分包商名称、到期日期、剩余天数。
 * </p>
 * <p>
 * <b>Validates: Requirements 5.4</b>
 * </p>
 */
@Tag("Feature: p1-business-completion, Property 10: 到期提醒去重幂等性")
@Tag("Feature: p1-business-completion, Property 9: 到期提醒消息完整性")
class ContractExpiryDeduplicationPropertyTest {

    // ==================== Property 10: 到期提醒去重幂等性 ====================

    @Property(tries = 200)
    @Label("首次发送应允许（shouldSendNotification = true），markAsSent 后第二次应跳过")
    void deduplication_firstSendAllowed_secondSendBlocked(
            @ForAll @LongRange(min = 1, max = 100000) long contractId,
            @ForAll("notificationLevels") String level) {

        // 使用内存 Set 模拟 Redis hasKey/set 行为
        Set<String> redisStore = new HashSet<>();
        RedisUtils mockRedisUtils = Mockito.mock(RedisUtils.class);

        // hasKey: 查 redisStore 中是否存在
        when(mockRedisUtils.hasKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisStore.contains(key);
        });

        // set: 向 redisStore 中添加 key
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            redisStore.add(key);
            return null;
        }).when(mockRedisUtils).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        ContractExpiryService service = new ContractExpiryService(
                mockRedisUtils, null, null, null);

        // 第一次：应该允许发送
        boolean firstResult = service.shouldSendNotification(contractId, level);
        Assertions.assertThat(firstResult)
                .as("首次调用 shouldSendNotification 应返回 true（允许发送）")
                .isTrue();

        // 执行标记已发送
        service.markAsSent(contractId, level);

        // 第二次：应该被阻止
        boolean secondResult = service.shouldSendNotification(contractId, level);
        Assertions.assertThat(secondResult)
                .as("markAsSent 后再次调用 shouldSendNotification 应返回 false（跳过）")
                .isFalse();
    }

    @Property(tries = 200)
    @Label("不同合同或不同级别之间的去重互不影响")
    void deduplication_differentContractOrLevel_independent(
            @ForAll @LongRange(min = 1, max = 50000) long contractId1,
            @ForAll @LongRange(min = 50001, max = 100000) long contractId2,
            @ForAll("notificationLevels") String level) {

        Set<String> redisStore = new HashSet<>();
        RedisUtils mockRedisUtils = Mockito.mock(RedisUtils.class);

        when(mockRedisUtils.hasKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisStore.contains(key);
        });

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            redisStore.add(key);
            return null;
        }).when(mockRedisUtils).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        ContractExpiryService service = new ContractExpiryService(
                mockRedisUtils, null, null, null);

        // 标记 contractId1 已发送
        service.markAsSent(contractId1, level);

        // contractId2 同级别仍然应该允许发送
        boolean result = service.shouldSendNotification(contractId2, level);
        Assertions.assertThat(result)
                .as("不同合同ID之间的去重应互不影响")
                .isTrue();
    }

    @Property(tries = 200)
    @Label("同一合同不同级别之间的去重互不影响")
    void deduplication_samContract_differentLevels_independent(
            @ForAll @LongRange(min = 1, max = 100000) long contractId) {

        Set<String> redisStore = new HashSet<>();
        RedisUtils mockRedisUtils = Mockito.mock(RedisUtils.class);

        when(mockRedisUtils.hasKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisStore.contains(key);
        });

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            redisStore.add(key);
            return null;
        }).when(mockRedisUtils).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        ContractExpiryService service = new ContractExpiryService(
                mockRedisUtils, null, null, null);

        // 标记 URGENT 级别已发送
        service.markAsSent(contractId, ContractExpiryService.LEVEL_URGENT);

        // UPCOMING 级别仍然应该允许发送
        boolean result = service.shouldSendNotification(contractId, ContractExpiryService.LEVEL_UPCOMING);
        Assertions.assertThat(result)
                .as("同一合同不同级别的去重应独立运行")
                .isTrue();
    }

    // ==================== Property 9: 到期提醒消息完整性 ====================

    @Property(tries = 200)
    @Label("到期提醒消息内容必须包含：合同编号、合同名称、供应商/分包商名称、到期日期、剩余天数")
    void notificationContent_containsAllRequiredFields(
            @ForAll("validContractExpiryDTO") ContractExpiryDTO contract,
            @ForAll("notificationLevels") String level,
            @ForAll @IntRange(min = 1, max = 30) int remainingDays) {

        // 设置 endDate 使其与 remainingDays 一致
        LocalDate today = LocalDate.of(2025, 6, 1);
        contract.setEndDate(today.plusDays(remainingDays));

        // Mock MessageService 用于捕获消息内容
        MessageService mockMessageService = Mockito.mock(MessageService.class);
        BizContractExpiryLogMapper mockLogMapper = Mockito.mock(BizContractExpiryLogMapper.class);
        when(mockLogMapper.insert(any())).thenReturn(1);

        ContractExpiryService service = new ContractExpiryService(
                null, mockMessageService, null, mockLogMapper);

        // 调用发送通知
        service.sendExpiryNotification(contract, level, today);

        // 捕获 sendMessage 的 content 参数
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockMessageService).sendMessage(
                eq(contract.getResponsibleUserId()),
                anyString(),
                contentCaptor.capture(),
                anyString(),
                anyString(),
                eq(contract.getId())
        );

        String content = contentCaptor.getValue();

        // 验证消息内容包含所有必需字段
        Assertions.assertThat(content)
                .as("消息内容应包含合同编号")
                .contains(contract.getContractCode());

        Assertions.assertThat(content)
                .as("消息内容应包含合同名称")
                .contains(contract.getContractName());

        Assertions.assertThat(content)
                .as("消息内容应包含供应商/分包商名称")
                .contains(contract.getCounterpartName());

        Assertions.assertThat(content)
                .as("消息内容应包含到期日期")
                .contains(contract.getEndDate().toString());

        Assertions.assertThat(content)
                .as("消息内容应包含剩余天数")
                .contains(String.valueOf(remainingDays));
    }

    @Property(tries = 200)
    @Label("无负责人的合同不发送消息（容错处理）")
    void notificationContent_noResponsibleUser_skipsSending(
            @ForAll("validContractExpiryDTO") ContractExpiryDTO contract,
            @ForAll("notificationLevels") String level) {

        contract.setResponsibleUserId(null);
        LocalDate today = LocalDate.of(2025, 6, 1);
        contract.setEndDate(today.plusDays(10));

        MessageService mockMessageService = Mockito.mock(MessageService.class);

        ContractExpiryService service = new ContractExpiryService(
                null, mockMessageService, null, null);

        // 调用发送通知
        service.sendExpiryNotification(contract, level, today);

        // 验证未调用 sendMessage
        verify(mockMessageService, never()).sendMessage(
                any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    // ==================== Arbitraries (数据提供器) ====================

    @Provide
    Arbitrary<String> notificationLevels() {
        return Arbitraries.of(ContractExpiryService.LEVEL_UPCOMING, ContractExpiryService.LEVEL_URGENT);
    }

    @Provide
    Arbitrary<ContractExpiryDTO> validContractExpiryDTO() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 100000L);
        Arbitrary<String> codes = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofMinLength(3).ofMaxLength(6)
                .map(prefix -> "HT-" + prefix + "-" + System.nanoTime() % 10000);
        Arbitrary<String> names = Arbitraries.of(
                "钢材采购合同", "混凝土供应合同", "劳务分包合同",
                "机械租赁合同", "装修工程合同", "脚手架租赁合同",
                "土方施工合同", "门窗安装合同", "电气安装合同");
        Arbitrary<String> counterparts = Arbitraries.of(
                "华远建材有限公司", "同城物流科技", "鑫泰劳务派遣",
                "大力机械租赁", "安居装饰工程", "鹏程建筑材料",
                "万通钢铁集团", "城建工程公司", "蓝天幕墙工程");
        Arbitrary<Long> userIds = Arbitraries.longs().between(1L, 5000L);
        Arbitrary<String> categories = Arbitraries.of(
                "MATERIAL", "LABOR", "MACHINE", "SUBCONTRACT");

        return Combinators.combine(ids, codes, names, counterparts, userIds, categories)
                .as((id, code, name, counterpart, userId, category) -> {
                    ContractExpiryDTO dto = new ContractExpiryDTO();
                    dto.setId(id);
                    dto.setContractCode(code);
                    dto.setContractName(name);
                    dto.setCounterpartName(counterpart);
                    dto.setResponsibleUserId(userId);
                    dto.setContractCategory(category);
                    dto.setStatus("ACTIVE");
                    dto.setContractTable("biz_expense_contract");
                    return dto;
                });
    }
}
