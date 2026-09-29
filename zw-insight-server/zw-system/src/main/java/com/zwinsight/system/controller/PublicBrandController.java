package com.zwinsight.system.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.vo.BrandConfigVO;
import com.zwinsight.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开品牌配置接口（免登录，路径匹配 WebMvcConfig 的 {@code /api/v1/public/**} 白名单）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/brand")
@RequiredArgsConstructor
public class PublicBrandController {

    private static final String DEFAULT_SYSTEM_NAME = "中维智营";
    private static final String DEFAULT_SYSTEM_SUB = "INSIGHT OS";
    private static final String DEFAULT_COPYRIGHT = "© 2026 中维智营 · 工程项目管理平台";

    private final SystemConfigService systemConfigService;

    @GetMapping
    public R<BrandConfigVO> getBrandConfig() {
        BrandConfigVO vo = new BrandConfigVO();
        vo.setSystemName(safeGet("brand_system_name", DEFAULT_SYSTEM_NAME));
        vo.setSystemSub(safeGet("brand_system_sub", DEFAULT_SYSTEM_SUB));
        vo.setLogoUrl(safeGet("brand_logo_url", ""));
        vo.setLogoLightUrl(safeGet("brand_logo_light_url", ""));
        vo.setFaviconUrl(safeGet("brand_favicon_url", ""));
        vo.setCopyright(safeGet("brand_copyright", DEFAULT_COPYRIGHT));
        return R.ok(vo);
    }

    private String safeGet(String configKey, String fallback) {
        try {
            String value = systemConfigService.getConfigValue(configKey);
            return (value != null && !value.isBlank()) ? value : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
