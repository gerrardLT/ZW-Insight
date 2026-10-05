package com.zwinsight.system.domain.vo;

import lombok.Data;

/**
 * 品牌配置视图对象（公开接口返回，免登录可读）
 */
@Data
public class BrandConfigVO {

    /**
     * 系统名称（如"中维智营"、"徽颖智营"）
     */
    private String systemName;

    /**
     * 系统副标题/代号（如"INSIGHT OS"）
     */
    private String systemSub;

    /**
     * 深色主题 Logo 图片地址（用于浅色/暗色侧边栏反白匹配）
     */
    private String logoUrl;

    /**
     * 浅色主题 Logo 图片地址（如果提供，暗黑模式下优先读取）
     */
    private String logoLightUrl;

    /**
     * 浏览器 Tab Favicon 地址
     */
    private String faviconUrl;

    /**
     * 登录页底部版权信息
     */
    private String copyright;

    /**
     * 品牌主色（#RRGGBB；空 = 内置主题色）。
     * 前端读取后覆盖 --zw-brand* CSS 变量家族，实现按部署环境换肤。
     */
    private String brandColor;
}
