-- 为询价单表新增报价截止时间字段（幂等：字段可能已在基线 schema 中存在）
ALTER TABLE `biz_inquiry` ADD COLUMN IF NOT EXISTS `deadline` DATETIME DEFAULT NULL COMMENT '报价截止时间' AFTER `publish_time`;
