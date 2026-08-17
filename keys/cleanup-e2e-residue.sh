#!/usr/bin/env bash
# 批量清理全部 E2E_TEST_ 残留询价（6 张）及关联数据
# 逆序：报价明细→报价→定标结果→询价明细→询价（biz_open_bid_record 无 inquiry_id 列，已排除）
MYSQL="docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -B -e"
docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -e "
DELETE qd FROM biz_quotation_detail qd
  JOIN biz_quotation q ON qd.quotation_id=q.id
  JOIN biz_inquiry i ON q.inquiry_id=i.id
  WHERE i.tenant_id=1 AND i.title LIKE 'E2E_TEST_%';
SELECT ROW_COUNT() AS quotation_detail_deleted;
DELETE q FROM biz_quotation q
  JOIN biz_inquiry i ON q.inquiry_id=i.id
  WHERE i.tenant_id=1 AND i.title LIKE 'E2E_TEST_%';
SELECT ROW_COUNT() AS quotation_deleted;
DELETE br FROM biz_bid_result br
  JOIN biz_inquiry i ON br.inquiry_id=i.id
  WHERE i.tenant_id=1 AND i.title LIKE 'E2E_TEST_%';
SELECT ROW_COUNT() AS bid_result_deleted;
DELETE it FROM biz_inquiry_item it
  JOIN biz_inquiry i ON it.inquiry_id=i.id
  WHERE i.tenant_id=1 AND i.title LIKE 'E2E_TEST_%';
SELECT ROW_COUNT() AS item_deleted;
DELETE FROM biz_inquiry WHERE tenant_id=1 AND title LIKE 'E2E_TEST_%';
SELECT ROW_COUNT() AS inquiry_deleted;
" 2>&1 | grep -v "Using a password"
# 终验
$MYSQL "SELECT CONCAT('inquiry_residue=', COUNT(*)) FROM biz_inquiry WHERE tenant_id=1 AND title LIKE 'E2E_TEST_%';
SELECT CONCAT('quotation_residue=', COUNT(*)) FROM biz_quotation WHERE tenant_id=1 AND inquiry_id NOT IN (SELECT id FROM biz_inquiry WHERE tenant_id=1);
SELECT CONCAT('bid_result_residue=', COUNT(*)) FROM biz_bid_result WHERE tenant_id=1 AND inquiry_id NOT IN (SELECT id FROM biz_inquiry WHERE tenant_id=1);" 2>&1 | grep -v "Using a password"
