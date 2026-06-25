package com.zwinsight.file.batch.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步导出线程池配置
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncExportConfig {

    /**
     * 批量导出专用线程池
     * <p>
     * 核心线程 2，最大线程 5，队列 50
     * 适合中等频率的导出任务
     */
    @Bean("batchExportExecutor")
    public Executor batchExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("batch-export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("批量导出线程池初始化完成: core=2, max=5, queue=50");
        return executor;
    }
}
