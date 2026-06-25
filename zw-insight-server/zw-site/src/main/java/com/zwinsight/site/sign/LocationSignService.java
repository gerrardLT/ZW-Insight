package com.zwinsight.site.sign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 位置签到服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationSignService {

    private final BizSignRecordMapper signRecordMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis Key 前缀：签到范围配置
     */
    private static final String SIGN_CONFIG_KEY_PREFIX = "sign:config:";

    /**
     * 地球半径（米）
     */
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * 默认签到半径（米）
     */
    private static final int DEFAULT_RADIUS = 500;

    // ======================== 签到 ========================

    /**
     * 执行签到
     *
     * @param userId  当前用户ID
     * @param request 签到请求（包含项目ID、经纬度、地址）
     * @return 签到记录
     */
    public BizSignRecord sign(Long userId, SignRequestDTO request) {
        if (request.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new BusinessException("签到位置不能为空");
        }

        // 获取项目签到范围配置
        SignConfigDTO config = getSignConfig(request.getProjectId());

        // 判断是否在签到范围内
        boolean inRange = false;
        if (config != null && config.getLatitude() != null && config.getLongitude() != null) {
            double distance = calculateHaversineDistance(
                    request.getLatitude().doubleValue(), request.getLongitude().doubleValue(),
                    config.getLatitude().doubleValue(), config.getLongitude().doubleValue()
            );
            int allowedRadius = config.getRadius() != null ? config.getRadius() : DEFAULT_RADIUS;
            inRange = distance <= allowedRadius;
            log.debug("签到距离计算: userId={}, projectId={}, distance={}m, radius={}m, inRange={}",
                    userId, request.getProjectId(), String.format("%.2f", distance), allowedRadius, inRange);
        } else {
            // 未配置签到范围，默认在范围内
            inRange = true;
            log.debug("项目未配置签到范围，默认在范围内: projectId={}", request.getProjectId());
        }

        // 保存签到记录
        BizSignRecord record = new BizSignRecord();
        record.setUserId(userId);
        record.setProjectId(request.getProjectId());
        record.setSignTime(LocalDateTime.now());
        record.setLatitude(request.getLatitude());
        record.setLongitude(request.getLongitude());
        record.setAddress(request.getAddress());
        record.setIsInRange(inRange ? 1 : 0);
        signRecordMapper.insert(record);

        return record;
    }

    // ======================== 签到记录查询 ========================

    /**
     * 查询签到记录（按项目和月份）
     */
    public List<BizSignRecord> getRecords(Long projectId, String month) {
        YearMonth ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX);

        LambdaQueryWrapper<BizSignRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizSignRecord::getProjectId, projectId)
                .ge(BizSignRecord::getSignTime, start)
                .le(BizSignRecord::getSignTime, end)
                .orderByDesc(BizSignRecord::getSignTime);
        return signRecordMapper.selectList(wrapper);
    }

    /**
     * 查询月度签到日历（某用户某项目某月）
     */
    public MonthlySignVO getMonthlyCalendar(Long projectId, Long userId, String month) {
        YearMonth ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX);

        // 查询该用户该项目本月的签到记录
        LambdaQueryWrapper<BizSignRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizSignRecord::getUserId, userId)
                .eq(BizSignRecord::getProjectId, projectId)
                .ge(BizSignRecord::getSignTime, start)
                .le(BizSignRecord::getSignTime, end)
                .orderByAsc(BizSignRecord::getSignTime);
        List<BizSignRecord> records = signRecordMapper.selectList(wrapper);

        // 按日期分组（取每天最早的一条作为当天签到记录）
        Map<LocalDate, BizSignRecord> dailyMap = new LinkedHashMap<>();
        for (BizSignRecord record : records) {
            LocalDate date = record.getSignTime().toLocalDate();
            dailyMap.putIfAbsent(date, record);
        }

        // 构建日历数据
        MonthlySignVO vo = new MonthlySignVO();
        vo.setUserId(userId);
        vo.setProjectId(projectId);
        vo.setMonth(month);

        List<MonthlySignVO.DailySign> dailyRecords = new ArrayList<>();
        int totalDays = ym.lengthOfMonth();
        int signDays = 0;
        int inRangeDays = 0;

        for (int day = 1; day <= totalDays; day++) {
            LocalDate date = ym.atDay(day);
            MonthlySignVO.DailySign daily = new MonthlySignVO.DailySign();
            daily.setDate(date);

            BizSignRecord record = dailyMap.get(date);
            if (record != null) {
                daily.setSigned(true);
                daily.setInRange(Integer.valueOf(1).equals(record.getIsInRange()));
                signDays++;
                if (daily.isInRange()) {
                    inRangeDays++;
                }
            } else {
                daily.setSigned(false);
                daily.setInRange(false);
            }
            dailyRecords.add(daily);
        }

        vo.setSignDays(signDays);
        vo.setInRangeDays(inRangeDays);
        vo.setDailyRecords(dailyRecords);
        return vo;
    }

    /**
     * 项目全员签到统计
     */
    public SignStatisticsVO getStatistics(Long projectId, String month) {
        YearMonth ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX);

        // 查询该项目本月所有签到记录
        LambdaQueryWrapper<BizSignRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizSignRecord::getProjectId, projectId)
                .ge(BizSignRecord::getSignTime, start)
                .le(BizSignRecord::getSignTime, end);
        List<BizSignRecord> allRecords = signRecordMapper.selectList(wrapper);

        // 按用户分组统计
        Map<Long, List<BizSignRecord>> userRecordsMap = allRecords.stream()
                .collect(Collectors.groupingBy(BizSignRecord::getUserId));

        List<SignStatisticsVO.UserSignStat> userStats = new ArrayList<>();
        for (Map.Entry<Long, List<BizSignRecord>> entry : userRecordsMap.entrySet()) {
            Long userId = entry.getKey();
            List<BizSignRecord> userRecords = entry.getValue();

            // 按日期去重（每天只算一次签到）
            Set<LocalDate> signDates = new HashSet<>();
            int inRangeDays = 0;
            int outRangeDays = 0;

            for (BizSignRecord record : userRecords) {
                LocalDate date = record.getSignTime().toLocalDate();
                if (signDates.add(date)) {
                    // 首次出现的日期
                    if (Integer.valueOf(1).equals(record.getIsInRange())) {
                        inRangeDays++;
                    } else {
                        outRangeDays++;
                    }
                }
            }

            SignStatisticsVO.UserSignStat stat = new SignStatisticsVO.UserSignStat();
            stat.setUserId(userId);
            stat.setSignDays(signDates.size());
            stat.setInRangeDays(inRangeDays);
            stat.setOutRangeDays(outRangeDays);
            userStats.add(stat);
        }

        SignStatisticsVO vo = new SignStatisticsVO();
        vo.setProjectId(projectId);
        vo.setMonth(month);
        vo.setTotalUsers(userStats.size());
        vo.setUserStats(userStats);
        return vo;
    }

    // ======================== 签到范围配置 ========================

    /**
     * 配置项目签到范围（存储到 Redis）
     */
    public void updateSignConfig(Long projectId, SignConfigDTO config) {
        if (config.getLatitude() == null || config.getLongitude() == null) {
            throw new BusinessException("签到中心坐标不能为空");
        }
        if (config.getRadius() == null || config.getRadius() <= 0) {
            throw new BusinessException("签到半径必须大于0");
        }

        String key = SIGN_CONFIG_KEY_PREFIX + projectId;
        try {
            String json = objectMapper.writeValueAsString(config);
            redisTemplate.opsForValue().set(key, json);
            log.info("更新项目签到配置: projectId={}, config={}", projectId, json);
        } catch (JsonProcessingException e) {
            throw new BusinessException("签到配置序列化失败");
        }
    }

    /**
     * 获取项目签到范围配置
     */
    public SignConfigDTO getSignConfig(Long projectId) {
        String key = SIGN_CONFIG_KEY_PREFIX + projectId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SignConfigDTO.class);
        } catch (JsonProcessingException e) {
            log.error("签到配置反序列化失败: projectId={}", projectId, e);
            return null;
        }
    }

    // ======================== 距离计算 ========================

    /**
     * Haversine 公式计算两点间球面距离（单位：米）
     *
     * @param lat1 纬度1
     * @param lng1 经度1
     * @param lat2 纬度2
     * @param lng2 经度2
     * @return 距离（米）
     */
    public static double calculateHaversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}
