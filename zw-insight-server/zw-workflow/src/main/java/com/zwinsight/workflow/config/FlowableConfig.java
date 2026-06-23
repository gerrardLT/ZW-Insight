package com.zwinsight.workflow.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flowable 流程引擎配置
 */
@Configuration
public class FlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> engineConfigurer() {
        return config -> {
            config.setActivityFontName("宋体");
            config.setLabelFontName("宋体");
            config.setAnnotationFontName("宋体");
            config.setDatabaseSchemaUpdate("true");
        };
    }
}
