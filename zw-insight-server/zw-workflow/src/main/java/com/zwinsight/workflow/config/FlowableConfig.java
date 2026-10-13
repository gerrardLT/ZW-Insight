package com.zwinsight.workflow.config;

import com.zwinsight.workflow.listener.ApprovalRejectListener;
import com.zwinsight.workflow.listener.DelegateTaskAssignListener;
import com.zwinsight.workflow.listener.ProcessCompleteListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 流程引擎配置
 */
@Configuration
@EnableAsync
public class FlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> engineConfigurer(
            DelegateTaskAssignListener delegateTaskAssignListener,
            ProcessCompleteListener processCompleteListener,
            ApprovalRejectListener approvalRejectListener) {
        return config -> {
            config.setActivityFontName("宋体");
            config.setLabelFontName("宋体");
            config.setAnnotationFontName("宋体");
            config.setDatabaseSchemaUpdate("true");

            // 注册全局事件监听器：按事件类型分组
            Map<String, List<FlowableEventListener>> typedListeners = new HashMap<>();
            typedListeners.put("TASK_ASSIGNED",
                    Collections.singletonList(delegateTaskAssignListener));
            typedListeners.put("PROCESS_COMPLETED",
                    Collections.singletonList(processCompleteListener));
            typedListeners.put("PROCESS_CANCELLED",
                    Collections.singletonList(approvalRejectListener));
            config.setTypedEventListeners(typedListeners);
        };
    }
}
