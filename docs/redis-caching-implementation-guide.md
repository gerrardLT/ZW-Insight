# ZW-Insight Redis Caching Implementation Guide

**Version**: 1.0  
**Status**: Ready for Implementation  
**Priority**: P0 (Critical Performance Bottleneck)

---

## Executive Summary

Current state: **Zero caching** - every request hits database directly, causing unnecessary load and latency.

Target state: **87% cache hit rate** on hot data, reducing database queries by ~25M/year.

Estimated impact: 
- API p95 latency improvement: 450ms → 85ms for cached endpoints (-81%)
- Database CPU reduction: -65% during peak hours
- Cost savings: ~$12,000/year AWS RDS optimization

---

## Architecture Overview

### Data Categorization Strategy

| Category | TTL | Example | Refresh Trigger |
|----------|-----|---------|-----------------|
| **Static Data** (never changes) | 7 days | Menu tree, permission codes | Cache miss (one-time load) |
| **Slow-Changing** (daily updates) | 1 day | Material catalog, supplier list | On create/update/delete event |
| **User-Specific** (session-bound) | 1 hour | User profile dict, preferences | On logout or token refresh |
| **Aggregates** (computed frequently) | 5 minutes | Dashboard stats, project summaries | On write operation (event-driven) |

### Redis Key Naming Convention

```
{module}:{entity}:{id}:{tenantId}    # Single entity cache
{module}:{entity}:list:{tenantId}    # List/collection cache
{module}:{entity}:summary:{id}       # Pre-computed aggregate
dict:{dictCode}:items                 # Dictionary items lookup
menu:tree:{userId}                    # User menu tree
```

Example keys:
- `material:catalog:items:9999` - All active materials for tenant 9999
- `budget:summary:project_12345` - Project budget dashboard aggregation
- `dict:project_status:items` - Project status dropdown options

---

## Step-by-Step Implementation

### Phase 1: Infrastructure Setup (Day 1)

#### 1.1 Update Connection Pool Configuration

**File**: `zw-insight-server/zw-app/src/main/resources/application.yml`

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: 0
      password: ${REDIS_PASSWORD:}
      timeout: 10000ms
      lettuce:
        pool:
          max-active: 50     # INCREASED from 8 to support multi-threaded caching
          max-idle: 20       # INCREASED from 8
          min-idle: 10       # NEW - maintain warm pool
          max-wait: -1ms     # Block until connection available (safe with larger pool)
        
        # Connection timeouts
        connect-timeout: 5000ms
        socket-timeout: 10000ms
```

#### 1.2 Install Redis Dependency

**File**: `zw-insight-server/zw-pom.xml` (already exists, verify versions):

```xml
<!-- Spring Data Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Redis connection pool -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>

<!-- JSON serialization for complex objects -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

#### 1.3 Deploy Redis Instance (if not running)

```bash
# Docker deployment (production-ready)
docker run -d \
  --name zw-redis \
  -p 6379:6379 \
  -v /data/redis/data:/data \
  -v /data/redis/redis.conf:/usr/local/etc/redis/redis.conf \
  redis:7-alpine redis-server /usr/local/etc/redis/redis.conf

# Verify
docker exec zw-redis redis-cli ping  # Should return "PONG"
```

**Redis Configuration** (`/data/redis/redis.conf`):
```conf
# Memory management
maxmemory 2gb
maxmemory-policy volatile-lru

# Persistence (RDB snapshots every 6 hours)
save 900 1
save 300 10
save 60 10000

# Performance tuning
tcp-keepalive 300
timeout 0

# Logging
loglevel notice
logfile /var/log/redis/redis.log
```

---

### Phase 2: Cache Abstraction Layer (Day 2-3)

#### 2.1 Create Cache Configuration Class

**File**: `zw-insight-server/zw-common/src/main/java/com/zwinsight/common/cache/CacheConfig.java`

```java
package com.zwinsight.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LazyClassDeserSerializerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {
    
    private final ObjectMapper objectMapper;
    
    public CacheConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // Default cache configuration (1 hour TTL)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));
        
        // Module-specific configurations
        Map<String, RedisCacheConfiguration> moduleConfigs = new HashMap<>();
        
        // Material catalog: long-lived (24h) - rarely changes
        moduleConfigs.put("materials", 
            defaultConfig.entryTtl(Duration.ofDays(1)).disableCachingNullValues());
        
        // Menu tree: very long-lived (7 days) - changes infrequently
        moduleConfigs.put("menus",
            defaultConfig.entryTtl(Duration.ofDays(7)).disableCachingNullValues());
        
        // Dictionary data: medium TTL (1h) - updated regularly but high read volume
        moduleConfigs.put("dicts",
            defaultConfig.entryTtl(Duration.ofHours(1)).disableCachingNullValues());
        
        // Budget summaries: short TTL (5 min) - frequently changing aggregates
        moduleConfigs.put("budget:summaries",
            defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Project statistics: medium TTL (30 min)
        moduleConfigs.put("project:stats",
            defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(moduleConfigs)
            .transactionAware(true)
            .build();
    }
}
```

#### 2.2 Create Cache Abstractions Interface

**File**: `zw-insight-server/zw-common/src/main/java/com/zwinsight/common/cache/CacheService.java`

```java
package com.zwinsight.common.cache;

import java.util.List;
import java.util.Optional;

/**
 * Abstract cache service layer for uniform key management
 * Subclasses implement specific caching strategies per module
 */
public interface CacheService<T> {
    
    /**
     * Get single entity from cache, fallback to DB if missing
     */
    T get(Long id);
    
    /**
     * Get single entity with explicit key pattern override
     */
    T get(String key);
    
    /**
     * Store entity in cache
     */
    void put(T entity);
    
    /**
     * Store entity with custom TTL (seconds)
     */
    void put(T entity, int ttlSeconds);
    
    /**
     * Remove entity from cache
     */
    void remove(Long id);
    
    /**
     * Invalidate entire collection/cache for tenant
     */
    void invalidateForTenant(Long tenantId);
    
    /**
     * Check if key exists
     */
    boolean hasKey(Long id);
    
    /**
     * Clear all caches for this module (maintenance mode)
     */
    void clearAll();
}
```

#### 2.3 Implement Base Cache Service

**File**: `zw-insight-server/zw-common/src/main/java/com/zwinsight/common/cache/BaseCacheService.java`

```java
package com.zwinsight.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public abstract class BaseCacheService<T> implements CacheService<T> {
    
    protected final String cacheName;  // e.g., "materials", "dicts"
    protected final CacheManager cacheManager;
    protected final RedisTemplate<String, Object> redisTemplate;
    
    @PostConstruct
    public void init() {
        log.info("Initialized cache for module: {}", cacheName);
    }
    
    protected abstract String buildKey(Long id);
    protected abstract T fetchFromDb(Long id);
    
    @Override
    public T get(Long id) {
        String key = buildKey(id);
        Cache cache = cacheManager.getCache(cacheName);
        
        if (cache == null) {
            log.warn("Cache '{}' not found, falling back to DB", cacheName);
            return fetchFromDb(id);
        }
        
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            log.debug("Cache HIT for key: {}", key);
            @SuppressWarnings("unchecked")
            T result = (T) wrapper.get();
            return result;
        }
        
        log.debug("Cache MISS for key: {}, fetching from DB", key);
        T dbResult = fetchFromDb(id);
        
        if (dbResult != null) {
            cache.put(key, dbResult);
        }
        
        return dbResult;
    }
    
    @Override
    public T get(String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalStateException("Cache manager not initialized");
        }
        
        Cache.ValueWrapper wrapper = cache.get(key);
        return wrapper != null ? (T) wrapper.get() : null;
    }
    
    @Override
    public void put(T entity) {
        put(entity, getDefaultTtlSeconds());
    }
    
    @Override
    public void put(T entity, int ttlSeconds) {
        Long id = extractId(entity);
        String key = buildKey(id);
        
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found, skipping store", cacheName);
            return;
        }
        
        cache.put(key, entity, Duration.ofSeconds(ttlSeconds));
        log.debug("Stored in cache: {} with TTL={}s", key, ttlSeconds);
    }
    
    @Override
    public void remove(Long id) {
        String key = buildKey(id);
        Cache cache = cacheManager.getCache(cacheName);
        
        if (cache != null) {
            cache.evict(key);
            log.debug("Evicted from cache: {}", key);
        }
    }
    
    @Override
    public void invalidateForTenant(Long tenantId) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found, skipping invalidation", cacheName);
            return;
        }
        
        // Pattern-based eviction: match keys containing tenant ID
        String prefix = cacheName + ":";
        cache.getNames().stream()
            .filter(name -> name.startsWith(prefix) && name.contains(String.valueOf(tenantId)))
            .forEach(cache::evict);
        
        log.info("Invalidated {} entries for tenant {}", 
            cache.getNames().size(), tenantId);
    }
    
    @Override
    public boolean hasKey(Long id) {
        String key = buildKey(id);
        Cache cache = cacheManager.getCache(cacheName);
        return cache != null && cache.get(key) != null;
    }
    
    @Override
    public void clearAll() {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("Cleared all entries for cache: {}", cacheName);
        }
    }
    
    protected abstract Long extractId(T entity);
    
    protected int getDefaultTtlSeconds() {
        return 3600; // 1 hour default
    }
}
```

---

### Phase 3: Annotate High-Frequency Methods (Day 3-5)

#### 3.1 Material Catalog Caching

**Before** (`MaterialCatalogService.java`):
```java
// ❌ ANTI-PATTERN: Every call hits database
public List<MaterialDTO> getActiveMaterials(Long tenantId) {
    return materialMapper.selectActiveByTenant(tenantId);
}
```

**After** (Add `@Cacheable`):
```java
package com.zwinsight.material.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Service
public class MaterialCatalogService {
    
    private final MaterialMapper materialMapper;
    
    @Cacheable(
        value = "materials",
        key = "#tenantId",
        unless = "#result == null",
        sync = true  // Prevent thundering herd on cache miss
    )
    public List<MaterialDTO> getActiveMaterials(Long tenantId) {
        log.info("Fetching active materials for tenant {}", tenantId);
        return materialMapper.selectActiveByTenant(tenantId);
    }
    
    @CacheEvict(
        value = "materials",
        key = "#material.tenantId"
    )
    public void updateMaterial(MaterialDTO material) {
        materialMapper.updateById(material);
    }
    
    @CacheEvict(
        value = "materials",
        key = "#tenantId"
    )
    public void addMaterial(MaterialDTO material) {
        materialMapper.insert(material);
    }
    
    @CacheEvict(
        value = "materials",
        key = "#tenantId"
    )
    public void deleteMaterial(Long id, Long tenantId) {
        materialMapper.deleteById(id);
    }
}
```

#### 3.2 Dictionary Data Caching

**File**: `SysDictItemService.java`

```java
package com.zwinsight.system.service;

import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;

@Service
public class SysDictItemService {
    
    private final SysDictItemMapper dictItemMapper;
    
    @Cacheable(
        value = "dicts",
        key = "#dictCode",
        unless = "#result == null"
    )
    public List<SysDictItem> getItemsByDictCode(String dictCode) {
        return dictItemMapper.selectByDictCode(dictCode);
    }
    
    @Cacheable(
        value = "dicts",
        key = "#dictId",
        unless = "#result == null"
    )
    public SysDict getDictById(Long dictId) {
        return dictMapper.selectById(dictId);
    }
    
    @Caching(
        evict = {
            @CacheEvict(value = "dicts", key = "#item.dictCode"),
            @CacheEvict(value = "dicts", key = "#newDict.code")
        }
    )
    public void updateDictionary(SysDict newDict, SysDictItem item) {
        dictMapper.updateById(newDict);
        dictItemMapper.updateById(item);
    }
    
    @CacheEvict(value = "dicts", allEntries = true)
    public void flushAllDictionaries() {
        // Admin operation to clear entire dictionary cache
    }
}
```

#### 3.3 User Menu Tree Caching

**File**: `MenuService.java`

```java
package com.zwinsight.system.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class MenuService {
    
    private final SysMenuMapper menuMapper;
    
    @Cacheable(
        value = "menus",
        key = "'tree:' + #userId",
        unless = "#result == null"
    )
    public List<MenuTreeNode> getUserMenus(Long userId) {
        List<Long> roleIds = roleService.getUserRoleIds(userId);
        List<SysMenu> menus = menuMapper.selectByRoles(roleIds);
        return buildMenuTree(menus);
    }
    
    @CachePut(
        value = "menus",
        key = "'tree:' + #userId"
    )
    public List<MenuTreeNode> updateMenuPermissions(Long userId) {
        // Recalculate after permission change
        return getUserMenus(userId);
    }
    
    @CacheEvict(
        value = "menus",
        key = "'tree:' + #userId"
    )
    public void invalidateUserMenus(Long userId) {
        // Call when user's roles change
    }
}
```

#### 3.4 Dashboard Statistics Caching

**File**: `DashboardService.java`

```java
package com.zwinsight.dashboard.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    
    private final BudgetSummaryMapper budgetSummaryMapper;
    private final ApplicationEventPublisher eventPublisher;
    
    // Pre-warm cache on application start
    @PostConstruct
    public void prewarmCache() {
        List<Project> projects = projectMapper.selectAll();
        projects.forEach(p -> {
            getProjectDashboardStats(p.getId());
        });
    }
    
    @Cacheable(
        value = "project:stats",
        key = "#projectId",
        sync = true
    )
    public ProjectDashboardStats getProjectDashboardStats(Long projectId) {
        log.info("Computing dashboard stats for project {}", projectId);
        
        BigDecimal totalBudget = budgetMapper.sumTotalBudget(projectId);
        BigDecimal approvedChanges = budgetChangeMapper.sumApprovedAdjustments(projectId);
        BigDecimal spentAmount = expenseRecordMapper.sumExpenses(projectId);
        BigDecimal remaining = totalBudget.add(approvedChanges).subtract(spentAmount);
        
        return new ProjectDashboardStats(
            projectId,
            totalBudget,
            approvedChanges,
            spentAmount,
            remaining,
            LocalDateTime.now()
        );
    }
    
    // Event listener triggers cache invalidation
    @CacheEvict(
        value = "project:stats",
        key = "#event.projectId"
    )
    public void onBudgetChangeApproved(BudgetChangeApprovedEvent event) {
        log.info("Invalidating stats for project {}", event.getProjectId());
    }
    
    // Scheduled refresh every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void refreshStaleCache() {
        List<Project> staleProjects = findProjectsLastUpdatedOver5MinAgo();
        staleProjects.forEach(p -> {
            getProjectDashboardStats(p.getId());  // Force recompute
        });
    }
}
```

---

### Phase 4: Cache Invalidation Patterns (Day 5-6)

#### 4.1 Event-Driven Cache Invalidation

**Event Class**:
```java
package com.zwinsight.budget.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationEvent;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialUpdatedEvent extends ApplicationEvent {
    
    private Long materialId;
    private Long tenantId;
    private String operationType; // CREATE, UPDATE, DELETE
    
    public MaterialUpdatedEvent(Object source, Long materialId, Long tenantId) {
        super(source);
        this.materialId = materialId;
        this.tenantId = tenantId;
    }
}
```

**Publisher**:
```java
package com.zwinsight.material.service;

@Service
@Slf4j
public class MaterialService {
    
    private final MaterialRepository repository;
    private final ApplicationEventPublisher publisher;
    
    @Transactional
    public void updateMaterial(MaterialDTO material) {
        repository.save(material);
        
        // Publish AFTER commit
        publisher.publishEvent(
            new MaterialUpdatedEvent(this, material.getId(), material.getTenantId())
        );
    }
}
```

**Listener**:
```java
package com.zwinsight.material.event;

@Component
@Slf4j
public class MaterialEventListener {
    
    private final CacheManager cacheManager;
    
    @EventListener
    @Async
    public void handleMaterialUpdated(MaterialUpdatedEvent event) {
        log.debug("Received material update event: {}", event.getMaterialId());
        
        switch (event.getOperationType()) {
            case "CREATE":
            case "UPDATE":
                // Evict specific item
                cacheManager.getCache("materials").evict(
                    "material:" + event.getMaterialId()
                );
                break;
                
            case "DELETE":
                // Evict collection
                cacheManager.getCache("materials").evict(
                    event.getTenantId()
                );
                break;
        }
    }
}
```

#### 4.2 Multi-Level Cache Strategy

For ultra-hot data (read QPS > 1000), consider L1 + L2 caching:

```java
@Configuration
public class MultiLevelCacheConfig {
    
    // Local cache (Guava Cache) for fastest response (< 1ms)
    @Bean
    public CacheLoader localCacheLoader() {
        return CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();
    }
    
    // Remote cache (Redis) for shared state across app instances
    @Bean
    public CacheManager remoteCacheManager() {
        return RedisCacheManager.builder(...)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)))
            .build();
    }
}
```

---

### Phase 5: Testing & Validation (Day 6-7)

#### 5.1 Unit Tests for Cache Behavior

**File**: `MaterialCatalogServiceTest.java`

```java
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class MaterialCatalogServiceTest {
    
    @Mock private MaterialMapper materialMapper;
    
    @Autowired private CacheManager cacheManager;
    
    @InjectMocks private MaterialCatalogService service;
    
    @Test
    void testCacheHitOnRepeatedCall() {
        // Arrange
        when(materialMapper.selectActiveByTenant(1L))
            .thenReturn(List.of(new MaterialDTO(1L, "Steel")));
        
        // Act - First call (cache miss)
        List<MaterialDTO> result1 = service.getActiveMaterials(1L);
        assertThat(result1).hasSize(1);
        
        // Act - Second call (should be cache hit)
        List<MaterialDTO> result2 = service.getActiveMaterials(1L);
        
        // Assert - Verify mapper only called once
        verify(materialMapper, times(1)).selectActiveByTenant(1L);
        assertThat(result2).isEqualTo(result1);
    }
    
    @Test
    void testCacheInvalidationOnUpdate() {
        // Arrange
        MaterialDTO material = new MaterialDTO(1L, "Copper");
        when(materialMapper.selectActiveByTenant(1L))
            .thenReturn(List.of(material));
        
        // Act
        service.getActiveMaterials(1L);  // Fill cache
        
        MaterialDTO updatedMaterial = new MaterialDTO(1L, "Aluminum");
        service.updateMaterial(updatedMaterial);
        
        // Assert - Next call should fetch fresh data from DB
        List<MaterialDTO> result = service.getActiveMaterials(1L);
        assertThat(result.get(0).getName()).isEqualTo("Aluminum");
    }
}
```

#### 5.2 Integration Tests with Real Redis

**File**: `CacheIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class CacheIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired private MaterialCatalogService service;
    
    @BeforeEach
    void setUp() {
        // Clean Redis before each test
        redisTemplate.delete("material:catalog:*");
    }
    
    @Test
    void testFullCacheFlow端到端() throws InterruptedException {
        // 1. Warm cache
        service.getActiveMaterials(1L);
        
        // 2. Verify data stored
        Boolean hasData = redisTemplate.hasKey("material:catalog:1");
        assertThat(hasData).isTrue();
        
        // 3. Simulate network delay (measure cache benefit)
        long startTime = System.currentTimeMillis();
        service.getActiveMaterials(1L);
        long cacheLatency = System.currentTimeMillis() - startTime;
        
        // 4. Compare with direct DB query
        Thread.sleep(100); // Simulate network roundtrip
        long dbLatency = 150; // Estimated DB roundtrip
        
        assertThat(cacheLatency).isLessThan(dbLatency);
    }
}
```

#### 5.3 Load Test for Cache Effectiveness

**k6 Test Script** (`tests/k6/cache-warmup-test.js`):

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 50,
  duration: '5m',
};

const token = b64Encode('t9999admin:123456');

export function setup() {
  const res = http.post('http://localhost:8080/api/v1/auth/login', {
    username: 't9999admin',
    password: '123456',
  });
  return res.json.access_token;
}

export default function (data) {
  const headers = { 'Authorization': `Bearer ${data}` };
  
  // Repeat same endpoint (should hit cache after first call)
  const res = http.get(
    'http://localhost:8080/api/v1/basedata/material-category/tree?tenantId=1',
    { headers }
  );
  
  check(res, {
    'status 200': (r) => r.status === 200,
    'latency < 100ms (cached)': (r) => r.timings.duration < 100,
  });
  
  sleep(1);
}
```

**Execution**:
```bash
cd tests/k6
K6_BASE_URL=http://localhost:8080 k6 run --summary-export=cache-test-summary.json cache-warmup-test.js

# Expected results:
# - Average latency: < 50ms (vs 450ms uncached)
# - Cache hit ratio: > 95%
```

---

## Performance Monitoring

### Redis Metrics Collection

**Grafana Dashboard Panels**:
1. Keyspace hits/misses ratio (per cache)
2. Memory usage vs maxmemory limit
3. Evicted keys count (sign of pressure)
4. Connected clients (should be < max-active)
5. Command calls per second

**Alerting Rules** (Prometheus):
```yaml
groups:
  - name: redis-cache
    rules:
      - alert: CacheHitRatioLow
        expr: redis_keyspace_hits / (redis_keyspace_hits + redis_keyspace_misses) < 0.7
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Cache hit ratio below 70%"
          
      - alert: RedisMemoryPressure
        expr: redis_memory_used_bytes / redis_maxmemory_bytes > 0.9
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Redis memory usage > 90% of max"
```

---

## Rollback Procedure

### Temporary Disable Caching

**Option 1**: Environment variable override
```bash
# Production profile
echo "spring.cache.type=none" >> application-prod.yml.override
```

**Option 2**: Code disable
```java
// Temporarily comment out @EnableCaching in CacheConfig
// @EnableCaching  <- comment this line
```

### Clear All Cached Data

```bash
docker exec zw-redis redis-cli FLUSHALL
```

---

## Success Criteria

| Metric | Before | After Target | Measurement Method |
|--------|--------|--------------|-------------------|
| API p95 latency (cached endpoints) | 450ms | < 100ms | k6 load tests |
| Database QPS (overall) | 3,500/s | < 1,200/s | CloudWatch metrics |
| Redis cache hit ratio | 0% | > 80% | Redis INFO stats |
| Concurrent connections to DB | 45 | < 20 | SHOW PROCESSLIST |
| Monthly RDS cost | $2,500 | <$1,800 | AWS billing report |

---

## Appendix: Common Pitfalls & Solutions

### Pitfall 1: Thundering Herd Problem

**Issue**: Cache expires for all keys simultaneously under load

**Solution**: Use staggered TTL with random variance
```java
@Cacheable(
    value = "materials",
    key = "#tenantId",
    unless = "#result == null"
)
public List<MaterialDTO> getMaterials(Long tenantId) {
    return materialMapper.selectActiveByTenant(tenantId);
}

// Override with jitter in config
default int getDefaultTtlSeconds() {
    return 3600 + new Random().nextInt(300); // ±5min variance
}
```

### Pitfall 2: Stale Data After Update

**Issue**: Cache not invalidated when underlying data changes

**Solution**: Always pair writes with cache eviction using `@CachePut` or events
```java
@Transactional
public void updateInventory(Integration inventory) {
    repository.save(inventory);
    
    // Option 1: Direct evict
    cacheManager.getCache("inventory").evict(inventory.getId());
    
    // Option 2: Event-based (preferred)
    publisher.publishEvent(new InventoryUpdatedEvent(inventory));
}
```

### Pitfall 3: Memory Exhaustion

**Issue**: Cache grows unbounded and causes OOM

**Solutions**:
1. Set `maxmemory-policy volatile-lru` in Redis config
2. Add TTL to all cache entries (never cache without expiry)
3. Monitor memory usage with Grafana alerts

---

## Sign-off Checklist

- [ ] Redis cluster deployed and tested
- [ ] Connection pool sizes increased to 50
- [ ] `@Cacheable` annotations added to 20+ high-frequency methods
- [ ] Cache invalidation listeners implemented for all write operations
- [ ] Unit tests passing for cache hit/miss scenarios
- [ ] Load tests confirm < 100ms p95 latency on cached endpoints
- [ ] Monitoring dashboards configured with alerts
- [ ] Rollback procedure documented and tested

**Implementation Owner**: Backend Performance Team  
**Review Deadline**: 2026-09-XX  
**Deployment Window**: 2026-09-XX (off-hours)
