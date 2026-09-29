-- 新环境 initdb 双轨：与 Flyway V2026_74 保持一致。
INSERT IGNORE INTO sys_config
(id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at)
VALUES
(1045, 'brand_logo_light_url', '', '浅色模式 Logo 地址', 'brand', 'STRING', '', NULL,
 '用于深色背景的 Logo；相对路径（如 /brand/xxx.png）或完整 URL', NOW(), NOW()),
(1046, 'brand_favicon_url', '', '浏览器图标（Favicon）', 'brand', 'STRING', '', NULL,
 '浏览器标签页图标；支持 png、ico、svg 或完整 URL', NOW(), NOW());
