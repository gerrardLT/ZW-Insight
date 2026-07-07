package com.zwinsight.app.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * Flyway 迁移策略配置
 * 在执行 migrate 之前先调用 repair()，自动清理历史中的失败记录
 * 解决 "Detected failed migration" 导致应用无法启动的问题
 */
@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("Flyway: 执行 repair() 清理失败迁移记录...");
            flyway.repair();
            log.info("Flyway: 执行 migrate()...");
            flyway.migrate();
            log.info("Flyway: 迁移完成");
        };
    }
}
