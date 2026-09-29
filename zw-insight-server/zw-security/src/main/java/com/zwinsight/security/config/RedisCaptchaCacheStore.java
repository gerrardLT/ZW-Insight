package com.zwinsight.security.config;

import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.AnyMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 天爱验证码的 Redis 缓存实现：挑战校验数据以 JSON 字符串存 Redis（key = captcha:{id}）。
 * 相比默认 LocalCacheStore：应用重启不丢在途挑战，多实例共享，E2E 夹具可读真实挑战。
 */
public class RedisCaptchaCacheStore implements CacheStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisCaptchaCacheStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public AnyMap getCache(String key) {
        return parse(redis.opsForValue().get(key));
    }

    @Override
    public AnyMap getAndRemoveCache(String key) {
        // GETDEL 原子取删：挑战只能被校验一次，防重放
        return parse(redis.opsForValue().getAndDelete(key));
    }

    @Override
    public boolean setCache(String key, AnyMap data, Long expire, TimeUnit timeUnit) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(data), expire, timeUnit);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    @Override
    public Long incr(String key, long delta, Long expire, TimeUnit timeUnit) {
        Long value = redis.opsForValue().increment(key, delta);
        if (value != null && value == delta) redis.expire(key, expire, timeUnit);
        return value;
    }

    @Override
    public Long getLong(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? null : Long.valueOf(value);
    }

    @Override
    public void close() {
        // 连接由 Spring 管理
    }

    private AnyMap parse(String json) {
        if (json == null) return null;
        try {
            return new AnyMap(mapper.readValue(json, MAP_TYPE));
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
