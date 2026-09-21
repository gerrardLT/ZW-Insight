-- ============================================================
-- 50_V2026_48__mojibake_fix_round2.sql
-- UTF-8 双重编码乱码订正（第二轮）
--
-- 根因：与 48_V2026_46__mojibake_fix.sql 同根因——49 号迁移
--   （49_V2026_47__p0_gap_closeout.sql）在生产手动执行时，mysql 客户端
--   连接字符集非 utf8mb4，中文文本双重编码写入。INSERT IGNORE 幂等导致
--   源文件后来无法覆盖坏行。
--
-- 取证（2026-08-22 远程实证）：
--   * sys_menu 4 条：20082/20083/20084/20085 全部带 0xC3 乱码特征
--   * 全表扫描 CAST(menu_name AS BINARY) LIKE CONCAT(0xC3,'%') 仅剩此 4 条，
--     无其他遗漏行（48 号订正后基线为 0）
--
-- 正确文本来源：49 号迁移 SQL 原文 L73-77（仓库内可对照）：
--   20082 回款认领 / 20083 回款核销 / 20084 库存预警配置维护 / 20085 库存预警配置删除
--
-- 幂等与安全：每条 UPDATE 均为「精确 id + 乱码特征」双条件。
--   订正后乱码特征消失，重复执行影响 0 行；只改文本不改 id，
--   sys_role_menu 等引用关系不断。
--
-- 执行方式（必须带字符集参数，防复发）：
--   经 deploy/run-migration.sh（--default-character-set=utf8mb4 已固化）：
--   bash run-migration.sh 50_V2026_48__mojibake_fix_round2.sql
-- ============================================================

-- 1) sys_menu：49 号迁移插入的 4 条按钮权限
UPDATE sys_menu SET menu_name = '回款认领'         WHERE id = 20082 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE sys_menu SET menu_name = '回款核销'         WHERE id = 20083 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE sys_menu SET menu_name = '库存预警配置维护' WHERE id = 20084 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE sys_menu SET menu_name = '库存预警配置删除' WHERE id = 20085 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');

-- 2) 订正后自检：应返回 0
SELECT COUNT(*) AS sys_menu_left FROM sys_menu WHERE CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
