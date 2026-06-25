package com.zwinsight.site.task;

import com.zwinsight.common.event.ReminderNotifyEvent;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizReminderConfig;
import com.zwinsight.site.domain.BizReminderLog;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.BizReminderConfigMapper;
import com.zwinsight.site.mapper.BizReminderLogMapper;
import com.zwinsight.site.service.ReminderConfigService;
import com.zwinsight.site.service.ReminderDeduplicationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 催办定时任务集成测试
 * <p>
 * 验证 RectificationReminderTask 全流程：
 * 配置加载 → 超期扫描 → 频率控制 → 消息发送 → 日志记录
 * </p>
 * <p>
 * 使用 Mockito 模拟所有外部依赖（数据库 Mapper、Redis、事件发布器），
 * 聚焦验证核心业务逻辑的正确性。
 * </p>
 * <p>
 * Requirements: 6.1-6.4, 7.1-7.4, 9.1-9.5
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ReminderTaskIntegrationTest {

    @Mock
    private BizInspectionMapper inspectionMapper;

    @Mock
    private BizReminderConfigMapper reminderConfigMapper;

    @Mock
    private BizReminderLogMapper reminderLogMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private BizProjectMemberMapper projectMemberMapper;

    @Mock
    private ReminderConfigService reminderConfigService;

    @Mock
    private ReminderDeduplicationService deduplicationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RectificationReminderTask reminderTask;

    @Captor
    private ArgumentCaptor<ReminderNotifyEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<BizReminderLog> logCaptor;

    // 测试数据常量
    private static final Long TENANT_ID = 1L;
    private static final Long INSPECTION_ID = 1001L;
    private static final Long RESPONSIBLE_PERSON_ID = 200L;
    private static final Long PROJECT_ID = 500L;
    private static final Long MANAGER_ID = 300L;
    private static final String PROJECT_NAME = "测试工程项目";

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ===========================================
    // Requirement 6.1: 配置加载 - enabled=false 时跳过
    // ===========================================

    @Test
    @DisplayName("配置未启用时跳过催办逻辑")
    void configDisabled_shouldSkipReminder() {
        BizReminderConfig config = createConfig(false);
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));

        reminderTask.doExecute();

        // 不应该查询超期记录
        verify(inspectionMapper, never()).selectList(any());
        // 不应该发送任何事件
        verify(eventPublisher, never()).publishEvent(any(ReminderNotifyEvent.class));
    }

    @Test
    @DisplayName("无催办配置时使用默认配置")
    void noConfig_shouldUseDefaultConfig() {
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of());
        BizReminderConfig defaultConfig = createConfig(false);
        when(reminderConfigService.getConfig(1L)).thenReturn(defaultConfig);

        reminderTask.doExecute();

        verify(inspectionMapper, never()).selectList(any());
    }

    // ===========================================
    // Requirement 6.2: 超期扫描 - PENDING + deadline < today
    // ===========================================

    @Test
    @DisplayName("查询超期记录 - 调用 inspectionMapper")
    void queryOverdueRecords_shouldCallMapper() {
        LocalDate today = LocalDate.now();
        reminderTask.queryOverdueRecords(TENANT_ID, today);
        verify(inspectionMapper).selectList(any());
    }

    // ===========================================
    // Requirement 6.3: 超期天数计算
    // ===========================================

    @Test
    @DisplayName("超期天数计算 - deadline 3天前 → 超期3天")
    void calculateOverdueDays_3daysAgo() {
        LocalDate today = LocalDate.of(2025, 7, 1);
        LocalDate deadline = today.minusDays(3);
        long days = reminderTask.calculateOverdueDays(deadline, today);
        assertThat(days).isEqualTo(3);
    }

    @Test
    @DisplayName("超期天数计算 - deadline 30天前 → 超期30天")
    void calculateOverdueDays_30daysAgo() {
        LocalDate today = LocalDate.of(2025, 7, 1);
        LocalDate deadline = today.minusDays(30);
        long days = reminderTask.calculateOverdueDays(deadline, today);
        assertThat(days).isEqualTo(30);
    }

    @Test
    @DisplayName("超期天数计算 - deadline 1天前 → 超期1天")
    void calculateOverdueDays_1dayAgo() {
        LocalDate today = LocalDate.of(2025, 7, 1);
        LocalDate deadline = today.minusDays(1);
        long days = reminderTask.calculateOverdueDays(deadline, today);
        assertThat(days).isEqualTo(1);
    }

    // ===========================================
    // Requirement 7.1: 发送催办通知给责任人
    // ===========================================

    @Test
    @DisplayName("正常超期 - 发送催办通知给责任人")
    void normalOverdue_shouldSendReminderToResponsiblePerson() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(3);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record = createOverdueInspection(5); // 超期5天
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record));
        when(deduplicationService.shouldSend(eq(INSPECTION_ID), any(LocalDate.class), eq(3)))
                .thenReturn(true);
        mockProjectInfo();

        reminderTask.doExecute();

        // 验证发送了催办事件
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

        List<ReminderNotifyEvent> events = eventCaptor.getAllValues();
        boolean hasNormalReminder = events.stream()
                .anyMatch(e -> e.getTargetUserId().equals(RESPONSIBLE_PERSON_ID)
                        && "REMINDER".equals(e.getMessageType()));
        assertThat(hasNormalReminder)
                .as("应发送普通催办通知给责任人")
                .isTrue();
    }

    // ===========================================
    // Requirement 7.2: 催办内容包含五项信息
    // ===========================================

    @Test
    @DisplayName("催办内容包含项目名称、检查类型、问题描述、整改期限、超期天数")
    void reminderContent_shouldContainAllRequiredInfo() {
        String projectName = "智慧工地一期";
        String inspectionType = "质量";
        String problemDesc = "混凝土强度不达标";
        LocalDate deadline = LocalDate.of(2025, 6, 26);
        int overdueDays = 5;

        String content = reminderTask.buildReminderContent(
                projectName, inspectionType, problemDesc, deadline, overdueDays);

        assertThat(content).contains(projectName);
        assertThat(content).contains(inspectionType);
        assertThat(content).contains(problemDesc);
        assertThat(content).contains(deadline.toString());
        assertThat(content).contains(String.valueOf(overdueDays));
    }

    @Test
    @DisplayName("催办内容 - 问题描述为 null 时使用默认文本")
    void reminderContent_nullDescription_shouldUseDefault() {
        String content = reminderTask.buildReminderContent(
                "项目A", "安全", null, LocalDate.of(2025, 6, 28), 3);

        assertThat(content).contains("未描述");
    }

    // ===========================================
    // Requirement 7.3: 升级通知 - overdueDays >= escalationDays
    // ===========================================

    @Test
    @DisplayName("超期天数达到升级阈值 - 额外通知项目经理")
    void overdueExceedsEscalation_shouldNotifyManager() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(1);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record = createOverdueInspection(10); // 超期10天 >= 7天阈值
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record));
        when(deduplicationService.shouldSend(eq(INSPECTION_ID), any(LocalDate.class), eq(1)))
                .thenReturn(true);
        mockProjectInfo();
        mockProjectManager();

        reminderTask.doExecute();

        verify(eventPublisher, atLeast(2)).publishEvent(eventCaptor.capture());

        List<ReminderNotifyEvent> events = eventCaptor.getAllValues();
        boolean hasEscalation = events.stream()
                .anyMatch(e -> e.getTargetUserId().equals(MANAGER_ID)
                        && "ESCALATION".equals(e.getMessageType()));
        assertThat(hasEscalation)
                .as("超期达到升级阈值时应通知项目经理")
                .isTrue();
    }

    @Test
    @DisplayName("超期天数未达升级阈值 - 不通知项目经理")
    void overdueBelowEscalation_shouldNotNotifyManager() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(1);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record = createOverdueInspection(3); // 超期3天 < 7天阈值
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record));
        when(deduplicationService.shouldSend(eq(INSPECTION_ID), any(LocalDate.class), eq(1)))
                .thenReturn(true);
        mockProjectInfo();

        reminderTask.doExecute();

        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

        List<ReminderNotifyEvent> events = eventCaptor.getAllValues();
        boolean hasEscalation = events.stream()
                .anyMatch(e -> "ESCALATION".equals(e.getMessageType()));
        assertThat(hasEscalation)
                .as("未达升级阈值时不应通知项目经理")
                .isFalse();
    }

    // ===========================================
    // Requirement 7.4: 长期超期停止催办
    // ===========================================

    @Test
    @DisplayName("长期超期 - 超过 longOverdueDays 停止催办")
    void longOverdue_shouldStopReminder() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(1);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record = createOverdueInspection(35); // 超期35天 > 30天阈值
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record));

        reminderTask.doExecute();

        // 不应发送任何催办通知
        verify(eventPublisher, never()).publishEvent(any(ReminderNotifyEvent.class));
        // 不应调用频率控制（因为长期超期直接跳过）
        verify(deduplicationService, never()).shouldSend(anyLong(), any(), anyInt());
    }

    // ===========================================
    // Requirement 9.2: 频率控制 - 未达间隔跳过
    // ===========================================

    @Test
    @DisplayName("频率控制 - 未达间隔天数时跳过催办")
    void frequencyControl_shouldSkipWhenNotReached() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(3);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record = createOverdueInspection(5);
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record));
        when(deduplicationService.shouldSend(eq(INSPECTION_ID), any(LocalDate.class), eq(3)))
                .thenReturn(false);

        reminderTask.doExecute();

        verify(eventPublisher, never()).publishEvent(any(ReminderNotifyEvent.class));
    }

    @Test
    @DisplayName("频率控制 - 达到间隔天数时发送催办")
    void frequencyControl_shouldSendWhenReached() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(3);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record = createOverdueInspection(5);
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record));
        when(deduplicationService.shouldSend(eq(INSPECTION_ID), any(LocalDate.class), eq(3)))
                .thenReturn(true);
        mockProjectInfo();

        reminderTask.doExecute();

        verify(eventPublisher, atLeastOnce()).publishEvent(any(ReminderNotifyEvent.class));
    }

    // ===========================================
    // Requirement 9.1: 发送后标记已发送
    // ===========================================

    @Test
    @DisplayName("催办发送成功后标记 markSent")
    void afterSend_shouldMarkSent() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(3);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record = createOverdueInspection(5);
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record));
        when(deduplicationService.shouldSend(eq(INSPECTION_ID), any(LocalDate.class), eq(3)))
                .thenReturn(true);
        mockProjectInfo();

        reminderTask.doExecute();

        verify(deduplicationService).markSent(eq(INSPECTION_ID), any(LocalDate.class));
    }

    // ===========================================
    // Requirement 6.4: 单条异常不中断循环
    // ===========================================

    @Test
    @DisplayName("单条记录处理异常 - 继续处理后续记录")
    void singleRecordException_shouldContinueProcessing() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(1);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record1 = createOverdueInspection(5);
        record1.setId(1001L);

        BizInspection record2 = createOverdueInspection(5);
        record2.setId(1002L);
        record2.setResponsiblePersonId(201L);

        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record1, record2));

        // 第一条 shouldSend 抛异常
        when(deduplicationService.shouldSend(eq(1001L), any(LocalDate.class), eq(1)))
                .thenThrow(new RuntimeException("Redis connection lost"));
        // 第二条正常
        when(deduplicationService.shouldSend(eq(1002L), any(LocalDate.class), eq(1)))
                .thenReturn(true);
        mockProjectInfo();

        // 不应抛出异常
        Assertions.assertDoesNotThrow(() -> reminderTask.doExecute());

        // 第二条记录应被正常处理
        verify(eventPublisher, atLeastOnce()).publishEvent(any(ReminderNotifyEvent.class));
    }

    // ===========================================
    // 日志记录验证 (Requirement 10.1, 10.2)
    // ===========================================

    @Test
    @DisplayName("催办发送后写入催办日志")
    void afterSend_shouldSaveReminderLog() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(1);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record = createOverdueInspection(5);
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record));
        when(deduplicationService.shouldSend(eq(INSPECTION_ID), any(LocalDate.class), eq(1)))
                .thenReturn(true);
        mockProjectInfo();

        reminderTask.doExecute();

        verify(reminderLogMapper, atLeastOnce()).insert(logCaptor.capture());

        BizReminderLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getInspectionId()).isEqualTo(INSPECTION_ID);
        assertThat(savedLog.getReceiverId()).isEqualTo(RESPONSIBLE_PERSON_ID);
        assertThat(savedLog.getReminderLevel()).isEqualTo("NORMAL");
        assertThat(savedLog.getSendStatus()).isEqualTo("SENT");
        assertThat(savedLog.getOverdueDays()).isEqualTo(5);
        assertThat(savedLog.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("升级通知也写入催办日志")
    void escalationSent_shouldSaveEscalatedLog() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(1);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record = createOverdueInspection(10);
        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record));
        when(deduplicationService.shouldSend(eq(INSPECTION_ID), any(LocalDate.class), eq(1)))
                .thenReturn(true);
        mockProjectInfo();
        mockProjectManager();

        reminderTask.doExecute();

        verify(reminderLogMapper, atLeast(2)).insert(logCaptor.capture());

        List<BizReminderLog> allLogs = logCaptor.getAllValues();
        boolean hasEscalatedLog = allLogs.stream()
                .anyMatch(log -> "ESCALATED".equals(log.getReminderLevel()));
        assertThat(hasEscalatedLog)
                .as("升级通知应记录 ESCALATED 级别日志")
                .isTrue();
    }

    // ===========================================
    // 检查类型标签映射
    // ===========================================

    @Test
    @DisplayName("检查类型标签 - QUALITY → 质量")
    void inspectionTypeLabel_quality() {
        assertThat(reminderTask.getInspectionTypeLabel("QUALITY")).isEqualTo("质量");
    }

    @Test
    @DisplayName("检查类型标签 - SAFETY → 安全")
    void inspectionTypeLabel_safety() {
        assertThat(reminderTask.getInspectionTypeLabel("SAFETY")).isEqualTo("安全");
    }

    @Test
    @DisplayName("检查类型标签 - 未知类型原样返回")
    void inspectionTypeLabel_unknown() {
        assertThat(reminderTask.getInspectionTypeLabel("ENVIRONMENTAL")).isEqualTo("ENVIRONMENTAL");
    }

    @Test
    @DisplayName("检查类型标签 - null 返回未知")
    void inspectionTypeLabel_null() {
        assertThat(reminderTask.getInspectionTypeLabel(null)).isEqualTo("未知");
    }

    // ===========================================
    // 多条超期记录批量处理
    // ===========================================

    @Test
    @DisplayName("多条超期记录 - 全部逐条处理")
    void multipleOverdueRecords_shouldProcessAll() {
        BizReminderConfig config = createConfig(true);
        config.setIntervalDays(1);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);

        BizInspection record1 = createOverdueInspection(3);
        record1.setId(1001L);
        BizInspection record2 = createOverdueInspection(5);
        record2.setId(1002L);
        BizInspection record3 = createOverdueInspection(8);
        record3.setId(1003L);

        when(reminderConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(inspectionMapper.selectList(any())).thenReturn(List.of(record1, record2, record3));
        when(deduplicationService.shouldSend(anyLong(), any(LocalDate.class), eq(1)))
                .thenReturn(true);
        mockProjectInfo();
        mockProjectManager();

        reminderTask.doExecute();

        // 三条记录都应被处理（至少3条日志）
        verify(reminderLogMapper, atLeast(3)).insert(any(BizReminderLog.class));
        // 应标记3次 markSent
        verify(deduplicationService, times(3)).markSent(anyLong(), any(LocalDate.class));
    }

    // ===========================================
    // Helper Methods
    // ===========================================

    private BizReminderConfig createConfig(boolean enabled) {
        BizReminderConfig config = new BizReminderConfig();
        config.setId(1L);
        config.setTenantId(TENANT_ID);
        config.setIntervalDays(3);
        config.setEscalationDays(7);
        config.setLongOverdueDays(30);
        config.setEnabled(enabled);
        return config;
    }

    private BizInspection createOverdueInspection(int overdueDays) {
        BizInspection record = new BizInspection();
        record.setId(INSPECTION_ID);
        record.setTenantId(TENANT_ID);
        record.setProjectId(PROJECT_ID);
        record.setInspectionType("QUALITY");
        record.setProblemDescription("混凝土强度不达标");
        record.setResponsiblePersonId(RESPONSIBLE_PERSON_ID);
        record.setRectificationStatus("PENDING");
        record.setRectificationDeadline(LocalDate.now().minusDays(overdueDays));
        return record;
    }

    private void mockProjectInfo() {
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setProjectName(PROJECT_NAME);
        lenient().when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
    }

    private void mockProjectManager() {
        BizProjectMember manager = new BizProjectMember();
        manager.setUserId(MANAGER_ID);
        manager.setProjectId(PROJECT_ID);
        manager.setStatus(1);
        lenient().when(projectMemberMapper.selectList(any())).thenReturn(List.of(manager));
    }
}
