-- V3: 采购结算关联入库单
-- 增加 inbound_id 字段，建立结算与入库的关联关系，防止重复结算

ALTER TABLE biz_purchase_settlement
    ADD COLUMN inbound_id BIGINT NULL COMMENT '关联入库单ID（结算依据）' AFTER status;

-- 添加索引便于查询未结算入库批次
CREATE INDEX idx_settlement_inbound ON biz_purchase_settlement(inbound_id);
CREATE INDEX idx_settlement_contract_status ON biz_purchase_settlement(contract_id, status);
