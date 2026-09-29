-- ============================================================
-- V2026_72__brand_config_seed.sql
-- 品牌白标（White-Label）配置种子：新增 sys_config 的 brand 分组，
-- 支撑「系统名称/副标题/Logo/登录页版权信息」按部署环境差异化展示，
-- 替代原先前端硬编码「中维智营」「INSIGHT OS」等文案。
--
-- 【设计要点】
-- - 全局配置（sys_config 无 tenant_id 列，一套部署=一个品牌），不与
--   业务租户（biz_ 表 tenant_id）混淆
-- - 默认值与迁移前既有前端硬编码完全一致，确保已上线服务器
--   （129.204.3.200）应用本迁移后前端展示不发生任何变化
-- - INSERT IGNORE 幂等；ID 段使用 1041-1044，紧邻 V2026_07 已占用的
--   1001-1033 段，避免与后续系统配置扩展冲突
-- - 新服务器（如 43.142.44.145）部署后，通过「系统设置-品牌设置」页面
--   或直接 UPDATE sys_config 即可完成换肤，无需改代码/切分支/重新构建镜像
-- ============================================================

INSERT IGNORE INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1041, 'brand_system_name', '中维智营', '系统名称', 'brand', 'STRING', '中维智营', NULL, '侧边栏/登录页/浏览器标题展示的系统名称', NOW(), NOW()),
(1042, 'brand_system_sub', 'INSIGHT OS', '系统副标题', 'brand', 'STRING', 'INSIGHT OS', NULL, '侧边栏 Logo 旁展示的英文代号/副标题', NOW(), NOW()),
(1043, 'brand_logo_url', '', 'Logo 图片地址', 'brand', 'STRING', '', NULL, '相对路径（如 /brand/xxx.png）或完整 URL；留空则使用内置默认 Logo', NOW(), NOW()),
(1044, 'brand_copyright', '© 2026 中维智营 · 工程项目管理平台', '登录页版权信息', 'brand', 'STRING', '© 2026 中维智营 · 工程项目管理平台', NULL, '登录页底部展示的版权文字', NOW(), NOW());
