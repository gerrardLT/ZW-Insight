-- ============================================================
-- V2026_119__fix_labor_output_menu_name.sql
-- 双轨镜像：内容必须与 deploy/db-init/122_V2026_119__fix_labor_output_menu_name.sql 一致
-- 背景：V2026_118 的「工程产值申报」UPDATE 未限定目录，把劳务管理目录下的
--       「产值上报」（id=806, path=output, parent_id=8）一并误改（徽颍已实际发生，
--       与合同域真·工程产值申报 id=910079 跨目录重名）；主环境同 id 行为历史
--       mojibake 脏数据（'äº§å€¼ä¸ŠæŠ¥'），V2026_118 无法命中而漏网。
-- 修复：两机统一把劳务域该菜单归位为「劳务完成产值」（与分包域「分包完成产值」
--       id=1103 命名对齐）；id 定点 + parent/path 语义双定位，只改既有行不 INSERT。
-- 幂等：重复执行 UPDATE 同值无害。已按双机纪律做过推送前只读探针（两机均命中 1 行）。
-- 回滚：UPDATE sys_menu SET menu_name=<旧名> WHERE id=806（徽颍旧名「工程产值申报」，
--       主环境旧名为 mojibake 串，回滚前先确认必要性）。
-- ============================================================

UPDATE sys_menu
SET menu_name = '劳务完成产值'
WHERE (id = 806 OR (parent_id = 8 AND path = 'output'))
  AND deleted = 0;
