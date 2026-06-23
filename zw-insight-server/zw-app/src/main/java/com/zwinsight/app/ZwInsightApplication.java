package com.zwinsight.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ZW Insight 工程项目管理平台启动类
 */
@SpringBootApplication(scanBasePackages = "com.zwinsight")
public class ZwInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZwInsightApplication.class, args);
    }

}
