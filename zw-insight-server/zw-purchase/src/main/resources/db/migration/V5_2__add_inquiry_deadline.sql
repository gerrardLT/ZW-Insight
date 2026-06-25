-- 为询价单表新增报价截止时间字段
ALTER TABLE `biz_inquiry` ADD COLUMN `deadline` DATETIME DEFAULT NULL COMMENT '报价截止时间' AFTER `publish_time`;
