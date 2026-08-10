package com.zwinsight.site.sign;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LocationSignService（位置签到）单元测试
 *
 * 覆盖场景:
 * - 签到参数校验与地理围栏判断（配置内/外/未配置默认放行）
 * - Haversine 距离计算正确性
 * - Redis 签到范围配置的读写与校验
 * - 月度日历与全员统计的按日去重逻辑
 */
@ExtendWith(MockitoExtension.class)
class LocationSignServiceTest {

    @Mock
    private BizSignRecordMapper signRecordMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private LocationSignService locationSignService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSignRecord.class);
    }

    private SignRequestDTO request(Long projectId, String lat, String lng) {
        SignRequestDTO request = new SignRequestDTO();
        request.setProjectId(projectId);
        request.setLatitude(lat == null ? null : new BigDecimal(lat));
        request.setLongitude(lng == null ? null : new BigDecimal(lng));
        request.setAddress("测试地址");
        return request;
    }

    private BizSignRecord record(Long userId, LocalDateTime time, int inRange) {
        BizSignRecord record = new BizSignRecord();
        record.setUserId(userId);
        record.setProjectId(10L);
        record.setSignTime(time);
        record.setIsInRange(inRange);
        return record;
    }

    @Test
    @DisplayName("签到：项目ID为空抛异常")
    void sign_nullProjectId_throwsException() {
        assertThatThrownBy(() -> locationSignService.sign(1L, request(null, "30.0", "120.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目ID不能为空");
    }

    @Test
    @DisplayName("签到：位置为空抛异常")
    void sign_nullLocation_throwsException() {
        assertThatThrownBy(() -> locationSignService.sign(1L, request(10L, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签到位置不能为空");
    }

    @Test
    @DisplayName("签到：未配置签到范围默认在范围内")
    void sign_noConfig_defaultsInRange() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("sign:config:10")).thenReturn(null);

        BizSignRecord result = locationSignService.sign(1L, request(10L, "30.0", "120.0"));

        assertThat(result.getIsInRange()).isEqualTo(1);
        verify(signRecordMapper).insert(result);
    }

    @Test
    @DisplayName("签到：在配置半径内标记为范围内")
    void sign_withinRadius_inRange() throws Exception {
        String configJson = configJson("30.0", "120.0", 500);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("sign:config:10")).thenReturn(configJson);

        BizSignRecord result = locationSignService.sign(1L, request(10L, "30.0", "120.0"));

        assertThat(result.getIsInRange()).isEqualTo(1);
    }

    @Test
    @DisplayName("签到：超出配置半径标记为范围外")
    void sign_outsideRadius_outOfRange() throws Exception {
        String configJson = configJson("30.0", "120.0", 500);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("sign:config:10")).thenReturn(configJson);

        // 距离约 150km，远超 500m 半径
        BizSignRecord result = locationSignService.sign(1L, request(10L, "31.0", "121.0"));

        assertThat(result.getIsInRange()).isZero();
    }

    private String configJson(String lat, String lng, int radius) throws Exception {
        SignConfigDTO config = new SignConfigDTO();
        config.setLatitude(new BigDecimal(lat));
        config.setLongitude(new BigDecimal(lng));
        config.setRadius(radius);
        return objectMapper.writeValueAsString(config);
    }

    @Test
    @DisplayName("Haversine：相同坐标距离为0，1纬度差约111公里")
    void haversine_distanceSanityCheck() {
        assertThat(LocationSignService.calculateHaversineDistance(30.0, 120.0, 30.0, 120.0))
                .isZero();
        double oneDegree = LocationSignService.calculateHaversineDistance(30.0, 120.0, 31.0, 120.0);
        assertThat(oneDegree).isBetween(110_000.0, 112_000.0);
    }

    @Test
    @DisplayName("配置签到范围：坐标为空抛异常")
    void updateSignConfig_nullCoords_throwsException() {
        SignConfigDTO config = new SignConfigDTO();
        config.setRadius(500);

        assertThatThrownBy(() -> locationSignService.updateSignConfig(10L, config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签到中心坐标不能为空");
    }

    @Test
    @DisplayName("配置签到范围：半径非正数抛异常")
    void updateSignConfig_invalidRadius_throwsException() {
        SignConfigDTO config = new SignConfigDTO();
        config.setLatitude(new BigDecimal("30.0"));
        config.setLongitude(new BigDecimal("120.0"));
        config.setRadius(0);

        assertThatThrownBy(() -> locationSignService.updateSignConfig(10L, config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签到半径必须大于0");
    }

    @Test
    @DisplayName("配置签到范围：序列化后写入 Redis")
    void updateSignConfig_success_writesRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        SignConfigDTO config = new SignConfigDTO();
        config.setLatitude(new BigDecimal("30.0"));
        config.setLongitude(new BigDecimal("120.0"));
        config.setRadius(500);

        locationSignService.updateSignConfig(10L, config);

        verify(valueOperations).set(eq("sign:config:10"), anyString());
    }

    @Test
    @DisplayName("读取签到配置：Redis 无值返回 null")
    void getSignConfig_empty_returnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("sign:config:10")).thenReturn(null);

        assertThat(locationSignService.getSignConfig(10L)).isNull();
    }

    @Test
    @DisplayName("月度日历：按日去重并统计签到/范围内天数")
    void getMonthlyCalendar_deduplicatesByDay() {
        when(signRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        record(1L, LocalDateTime.of(2026, 7, 1, 8, 0), 1),
                        record(1L, LocalDateTime.of(2026, 7, 1, 18, 0), 0),
                        record(1L, LocalDateTime.of(2026, 7, 2, 9, 0), 0)));

        MonthlySignVO vo = locationSignService.getMonthlyCalendar(10L, 1L, "2026-07");

        assertThat(vo.getSignDays()).isEqualTo(2);
        assertThat(vo.getInRangeDays()).isEqualTo(1);
        assertThat(vo.getDailyRecords()).hasSize(31);
        assertThat(vo.getDailyRecords().get(0).isSigned()).isTrue();
        assertThat(vo.getDailyRecords().get(0).isInRange()).isTrue();
    }

    @Test
    @DisplayName("全员统计：按用户分组且每天只计一次签到")
    void getStatistics_groupsByUserAndDeduplicates() {
        when(signRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        record(1L, LocalDateTime.of(2026, 7, 1, 8, 0), 1),
                        record(1L, LocalDateTime.of(2026, 7, 1, 18, 0), 0),
                        record(1L, LocalDateTime.of(2026, 7, 2, 9, 0), 0),
                        record(2L, LocalDateTime.of(2026, 7, 1, 8, 30), 1)));

        SignStatisticsVO vo = locationSignService.getStatistics(10L, "2026-07");

        assertThat(vo.getTotalUsers()).isEqualTo(2);
        SignStatisticsVO.UserSignStat user1 = vo.getUserStats().stream()
                .filter(s -> s.getUserId() == 1L).findFirst().orElseThrow();
        assertThat(user1.getSignDays()).isEqualTo(2);
        assertThat(user1.getInRangeDays()).isEqualTo(1);
        assertThat(user1.getOutRangeDays()).isEqualTo(1);
    }
}
