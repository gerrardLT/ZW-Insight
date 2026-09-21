-- ============================================================
-- 46_V2026_44__app_watermark_camera_shortcut.sql
-- 移动端「工程水印相机」快捷入口注册
-- ============================================================

INSERT IGNORE INTO msg_available_shortcut (id, name, icon, route_path, sort_order, status, create_time) VALUES
(23, '水印相机', 'icon-watermark-camera', '/pages/site/watermark-camera/index', 23, 'ENABLED', NOW());
