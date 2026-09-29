package com.zwinsight.security.config;

import cloud.tianai.captcha.common.AnyMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 天爱挑战数据经 Redis JSON 往返后仍可被校验器读取；取删为原子一次性。 */
class RedisCaptchaCacheStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisCaptchaCacheStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        store = new RedisCaptchaCacheStore(redis, new ObjectMapper());
    }

    @Test
    void setThenGetAndRemove_roundTripsValidData() {
        AnyMap data = new AnyMap();
        data.put("percentage", 0.42F);
        data.put("type", "SLIDER");

        assertThat(store.setCache("captcha:id", data, 120_000L, TimeUnit.MILLISECONDS)).isTrue();
        verify(ops).set(eq("captcha:id"), argThat(json -> json.contains("\"percentage\"")),
                eq(120_000L), eq(TimeUnit.MILLISECONDS));

        when(ops.getAndDelete("captcha:id")).thenReturn("{\"percentage\":0.42,\"type\":\"SLIDER\"}");
        AnyMap read = store.getAndRemoveCache("captcha:id");

        assertThat(read.getFloat("percentage")).isEqualTo(0.42F);
        assertThat(read.getString("type")).isEqualTo("SLIDER");
    }

    @Test
    void missingOrCorruptEntry_returnsNull() {
        when(ops.getAndDelete("captcha:gone")).thenReturn(null);
        when(ops.get("captcha:bad")).thenReturn("not-json");

        assertThat(store.getAndRemoveCache("captcha:gone")).isNull();
        assertThat(store.getCache("captcha:bad")).isNull();
    }

    @Test
    void incr_setsExpireOnlyOnFirstIncrement() {
        when(ops.increment("k", 1L)).thenReturn(1L, 2L);

        assertThat(store.incr("k", 1L, 60L, TimeUnit.SECONDS)).isEqualTo(1L);
        assertThat(store.incr("k", 1L, 60L, TimeUnit.SECONDS)).isEqualTo(2L);
        verify(redis, times(1)).expire("k", 60L, TimeUnit.SECONDS);

        when(ops.get("k")).thenReturn("2");
        assertThat(store.getLong("k")).isEqualTo(2L);
    }
}
