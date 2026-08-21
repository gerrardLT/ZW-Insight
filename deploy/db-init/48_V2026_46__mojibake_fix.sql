-- ============================================================
-- 48_V2026_46__mojibake_fix.sql
-- UTF-8 双重编码乱码订正（B1）
--
-- 根因：45/47 号迁移在生产手动执行时，mysql 客户端连接字符集非 utf8mb4，
--   中文文本经「UTF-8 字节按 Latin-1 解读再编码」双重转码写入。
-- 取证（2026-08-22，_scan-mojibake 系列脚本远程实证）：
--   * sys_menu 7 条：20101-20105/20141/20142（47 号迁移插入，全表乱码仅此 7 条）
--   * serial_number_rule 2 条：900006(tenant=1)/995006(tenant=9999) MATERIAL_INBOUND
--   * biz_project 1 条：90004（45 号迁移种子，8 个中文列全部乱码）
--   * biz_project_member 1 条：90709（user_name）
--   * 其余中文文本列全库普查（sys_dict/sys_org/sys_user/sys_role/sys_post/
--     bd_supplier/bd_material 等）乱码 0 条。
--
-- 正确文本来源：45/47/43 号迁移 SQL 原文（仓库内可对照）。
--
-- 幂等与安全：每条 UPDATE 均为「精确 id + 乱码特征（CAST AS BINARY LIKE 0xC3%）」
--   双条件。订正后乱码特征消失，重复执行影响 0 行；id 已被 sys_role_menu /
--   biz_project_settlement 等引用的行只改文本不改 id，授权与引用关系不断。
--
-- 执行方式（必须带字符集参数，防复发）：
--   scp deploy/db-init/48_V2026_46__mojibake_fix.sql <server>:/tmp/
--   docker exec -i zwi-mysql mysql -uroot -pzwinsight123 \
--     --default-character-set=utf8mb4 zw_insight < /tmp/48_V2026_46__mojibake_fix.sql
--   或经 deploy/run-migration.sh（字符集已固化）执行。
-- ============================================================

-- 1) sys_menu：47 号迁移插入的 7 条权限目录
UPDATE sys_menu SET menu_name = '新增通知'     WHERE id = 20101 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE sys_menu SET menu_name = '发布通知'     WHERE id = 20102 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE sys_menu SET menu_name = '推送配置编辑' WHERE id = 20103 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE sys_menu SET menu_name = '新增项目'     WHERE id = 20104 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE sys_menu SET menu_name = '编辑项目'     WHERE id = 20105 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE sys_menu SET menu_name = '项目数据视图' WHERE id = 20141 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE sys_menu SET menu_name = '基础数据视图' WHERE id = 20142 AND CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');

-- 2) serial_number_rule：MATERIAL_INBOUND 编号规则描述（43 号迁移原文「材料入库单编号」）
UPDATE serial_number_rule SET description = '材料入库单编号'
WHERE id = 900006 AND CAST(description AS BINARY) LIKE CONCAT(0xC3, '%');
UPDATE serial_number_rule SET description = '材料入库单编号'
WHERE id = 995006 AND CAST(description AS BINARY) LIKE CONCAT(0xC3, '%');

-- 3) biz_project 90004：45 号迁移种子「可结项项目」，8 个中文列全部订正
UPDATE biz_project SET
    project_name         = '城北河道综合整治工程',
    project_nature       = '改建',
    project_type         = '市政工程',
    owner_company_name   = '杭州市城南市政建设管理中心',
    signing_company_name = '中正建设集团有限公司',
    project_overview     = '城北片区河道清淤、驳坎加固与滨水绿道建设，全长2.8公里',
    project_address      = '杭州市拱墅区城北河道沿线',
    contact_name         = '周涛'
WHERE id = 90004 AND CAST(project_name AS BINARY) LIKE CONCAT(0xC3, '%');

-- 4) biz_project_member 90709：90004 的项目经理（45 号迁移原文「陈刚」）
UPDATE biz_project_member SET user_name = '陈刚'
WHERE id = 90709 AND CAST(user_name AS BINARY) LIKE CONCAT(0xC3, '%');

-- 5) 订正后自检：以下 4 项均应返回 0
SELECT COUNT(*) AS sys_menu_left      FROM sys_menu            WHERE CAST(menu_name AS BINARY) LIKE CONCAT(0xC3, '%');
SELECT COUNT(*) AS serial_rule_left   FROM serial_number_rule  WHERE CAST(description AS BINARY) LIKE CONCAT(0xC3, '%');
SELECT COUNT(*) AS biz_project_left   FROM biz_project         WHERE CAST(project_name AS BINARY) LIKE CONCAT(0xC3, '%');
SELECT COUNT(*) AS project_member_left FROM biz_project_member WHERE CAST(user_name AS BINARY) LIKE CONCAT(0xC3, '%');
