package com.zwinsight;

import org.springframework.boot.SpringBootConfiguration;

/**
 * 测试根配置 - 为 @WebMvcTest 提供 @SpringBootConfiguration 锚点。
 * <p>
 * Controller 测试分布在 com.zwinsight.xxx.controller 各子包，
 * @WebMvcTest 会沿包层级向上查找 @SpringBootConfiguration。
 * 主应用 com.zwinsight.app.ZwInsightApplication 不在其父级链上，
 * 因此在 com.zwinsight 根包放置此配置类供所有 Controller 测试发现。
 * </p>
 */
@SpringBootConfiguration
public class TestApplication {
}
