-- ============================================================
-- V2026_55__sys_user_phone_unique.sql
-- 背景：短信验证码登录 AuthService.loginBySms 按 phone 全局 selectOne 定位用户，
--       此前 sys_user.phone 无唯一约束（预检 2026-09-21 全库 24 个绑定手机号的账号
--       互不重复，但另有 1 个空串脏行）——两个账号录入相同手机号时 selectOne 抛
--       TooManyResultsException，该号码短信登录整体瘫痪（500），属真实故障面。
-- 口径：手机号定义为「全局唯一」登录凭据（跨租户唯一，与 loginBySms 查询语义一致）；
--       NULL 不参与唯一约束（MySQL 允许多 NULL）。
-- 安全：建索引前把空串规范化为 NULL（否则多个空串行互撞唯一键）；若仍存量重复，
--       ADD UNIQUE KEY 原生报 ERROR 1062、迁移事务中止——这是真守卫而非打印式假警告，
--       运维清理重复数据后重启自动重跑（Flyway 只记录成功迁移）。
-- 幂等：uk_phone 已存在时跳过（information_schema.STATISTICS 检查 + PREPARE）。
-- ============================================================

-- 1) 空串手机号规范化为 NULL（幂等）
UPDATE sys_user SET phone = NULL WHERE phone = '';

-- 2) 诊断输出：存量重复组（仅便于运维读日志；真正的中止由第 3 步 1062 原生触发）
SELECT phone AS dup_phone, COUNT(*) AS cnt
FROM sys_user WHERE phone IS NOT NULL GROUP BY phone HAVING COUNT(*) > 1;

-- 3) 条件建唯一索引（幂等；存量重复时 ADD UNIQUE KEY 直接 1062 失败中止迁移）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_phone') = 0,
    'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_phone` (`phone`)',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
