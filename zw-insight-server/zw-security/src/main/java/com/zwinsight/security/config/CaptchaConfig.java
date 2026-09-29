package com.zwinsight.security.config;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.TACBuilder;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 天爱验证码（tianai-captcha core 1.5.5，Apache-2.0）装配。
 * 不使用其 Spring Boot starter：starter 基于 Boot 2.2，与本项目 Boot 3 不兼容。
 */
@Configuration
public class CaptchaConfig {

    @Bean(destroyMethod = "close")
    public ImageCaptchaApplication imageCaptchaApplication(StringRedisTemplate redis, ObjectMapper mapper) {
        return TACBuilder.builder()
                .addDefaultTemplate()
                // ponytail: 仅用 jar 自带 1 张背景图；需要多样化时在此追加 classpath/url 资源
                .addResource(CaptchaTypeConstant.SLIDER,
                        new Resource("classpath", "META-INF/cut-image/resource/1.jpg"))
                .expire(CaptchaTypeConstant.SLIDER, 120_000L)
                .setCacheStore(new RedisCaptchaCacheStore(redis, mapper))
                .build();
    }
}
