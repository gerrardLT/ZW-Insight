-- ============================================================
-- V2026_80__brand_color_fix.sql
-- 补种 brand_color（V2026_79 的 1045 号在部分环境已被占用致 INSERT IGNORE
-- 静默跳过——129 实测：sys_config 1045 已是 brand_logo_light_url）。
-- 按 config_key 幂等补种，ID 用 1048（各环境 1041-1047 段均空闲）。
-- 双轨：deploy/db-init/82_V2026_80__brand_color_fix.sql 同内容。
-- ============================================================

INSERT INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at)
SELECT 1048, 'brand_color', '', '品牌主色', 'brand', 'STRING', '', NULL,
       '十六进制色值（#RRGGBB）；留空使用内置主题色。填写后按钮/链接/高亮等主色随环境换肤',
       NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'brand_color')
  AND NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 1048);
