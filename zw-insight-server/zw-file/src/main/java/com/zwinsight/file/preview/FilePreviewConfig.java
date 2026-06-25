package com.zwinsight.file.preview;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * KKFileView 文件预览配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.kkfileview")
public class FilePreviewConfig {

    /**
     * KKFileView 服务地址
     */
    private String baseUrl = "http://localhost:8012";
}
