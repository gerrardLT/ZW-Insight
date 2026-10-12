-- =============================================================================
-- V2026_97__office_supply_archive_backfill.sql
-- 办公用品档案四列存量对齐（2026-10-12）
--
-- 背景：biz_office_supply 同时存在两套库存口径：
--   - stock_quantity（INT）——HR 侧口径，出入库提交只更新它
--   - current_stock / total_inbound / total_issued / last_inbound_date ——档案页口径
--     （views/archive/office-supply.vue 读这四列），此前**无任何写入方**，恒为 0
-- 演示种子（31_V2026_26）也只写 stock_quantity，导致档案页对已有用品恒显示 0/0/0。
--
-- 2026-10-12 已修写入侧：OfficeSupplyInOutService.submit 同步维护四列；
-- 本迁移把**存量数据**对齐，避免修复后出现「当前库存 170 而累计入库 50」这类自相矛盾。
--
-- 幂等：两条 UPDATE 均按现值重算，可重复执行；不新增列、不改结构。
-- =============================================================================

-- 1) current_stock 与 stock_quantity 对齐（current_stock 为档案页「当前库存」）
UPDATE biz_office_supply
SET current_stock = stock_quantity
WHERE deleted = 0
  AND stock_quantity IS NOT NULL
  AND (current_stock IS NULL OR current_stock <> stock_quantity);

-- 2) 按已审批出入库记录重算累计入库/累计出库/最近入库日
UPDATE biz_office_supply s
LEFT JOIN (
    SELECT supply_id,
           SUM(CASE WHEN io_type = 'IN'  THEN quantity ELSE 0 END) AS total_in,
           SUM(CASE WHEN io_type = 'OUT' THEN quantity ELSE 0 END) AS total_out,
           MAX(CASE WHEN io_type = 'IN'  THEN DATE(created_at) END) AS last_in
    FROM biz_office_supply_in_out
    WHERE status = 'APPROVED' AND deleted = 0
    GROUP BY supply_id
) agg ON agg.supply_id = s.id
SET s.total_inbound     = COALESCE(agg.total_in, 0),
    s.total_issued      = COALESCE(agg.total_out, 0),
    s.last_inbound_date = agg.last_in
WHERE s.deleted = 0;
