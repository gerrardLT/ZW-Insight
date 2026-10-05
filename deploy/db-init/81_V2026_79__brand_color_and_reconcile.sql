-- ============================================================
-- V2026_79__brand_color_and_reconcile.sql
-- 品牌资产收口：品牌主色（brand_color）+ V2026_72 品牌行缺失环境自愈
--
-- 背景（2026-10-05 双库漂移检查）：
--   1) 43（徽颍，2026-09-28 initdb）上 V2026_72 标记成功但 sys_config 品牌
--      行 0 条——initdb 字典序下 04_SystemConfig 种子晚于编号迁移时 UPDATE/INSERT
--      类迁移同样可能扑空（同 99_data-menu 时序坑家族）；品牌行缺失使 43 一直
--      跑「中维智营」默认品牌，白标能力对客户环境未生效。
--      本迁移用 INSERT IGNORE 重放 V2026_72 全部行（已存在的环境零影响）。
--   2) 新增品牌主色 brand_color：允许按部署环境覆盖系统主色（如徽颍用蓝色系），
--      前端 brand store 读取后改写 --zw-brand* 变量（见 stores/brand.ts）。
--      默认空 = 用内置橙色主题，行为与既有部署完全一致。
-- 幂等：INSERT IGNORE + NOT EXISTS 双守卫。
-- 双轨：deploy/db-init/81_V2026_79__brand_color_and_reconcile.sql 同内容。
-- ============================================================

-- 1) V2026_72 品牌行重放（补 initdb 扑空的环境；已存在的 IGNORE 跳过）
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1041, 'brand_system_name', '中维智营', '系统名称', 'brand', 'STRING', '中维智营', NULL, '侧边栏/登录页/浏览器标题展示的系统名称', NOW(), NOW()),
(1042, 'brand_system_sub', 'INSIGHT OS', '系统副标题', 'brand', 'STRING', 'INSIGHT OS', NULL, '侧边栏 Logo 旁展示的英文代号/副标题', NOW(), NOW()),
(1043, 'brand_logo_url', '', 'Logo 图片地址', 'brand', 'STRING', '', NULL, '相对路径（如 /brand/xxx.png）或完整 URL；留空则使用内置默认 Logo', NOW(), NOW()),
(1044, 'brand_copyright', '© 2026 中维智营 · 工程项目管理平台', '登录页版权信息', 'brand', 'STRING', '© 2026 中维智营 · 工程项目管理平台', NULL, '登录页底部展示的版权文字', NOW(), NOW());

-- 2) 品牌主色：空 = 内置主题色；填写十六进制色值（#RRGGBB）后前端覆盖 --zw-brand 家族
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at)
SELECT 1045, 'brand_color', '', '品牌主色', 'brand', 'STRING', '', NULL,
       '十六进制色值（#RRGGBB）；留空使用内置主题色。填写后按钮/链接/高亮等主色随环境换肤',
       NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'brand_color');

-- 3) V2026_72 漏种的 logoLight/favicon 行补齐（1046/1047 延续该段）
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1046, 'brand_logo_light_url', '', 'Logo 图片地址（亮色）', 'brand', 'STRING', '', NULL, '深色侧边栏使用的亮色 Logo；留空回退 brand_logo_url', NOW(), NOW()),
(1047, 'brand_favicon_url', '', '浏览器标签图标地址', 'brand', 'STRING', '', NULL, '留空使用内置 favicon；建议 32x32 PNG 或 ICO', NOW(), NOW());
