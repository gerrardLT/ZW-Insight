#!/bin/bash
# 清空前盘点：全库表清单（按前缀分组）
docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -e "
SELECT table_name FROM information_schema.tables WHERE table_schema='zw_insight' ORDER BY table_name;" 2>/dev/null > /tmp/all_tables.txt
echo "总表数: $(wc -l < /tmp/all_tables.txt)"
echo ""
echo "--- biz_* ($(grep -c '^biz_' /tmp/all_tables.txt)) ---"
grep '^biz_' /tmp/all_tables.txt | tr '\n' ' '; echo
echo ""
echo "--- sys_* ($(grep -c '^sys_' /tmp/all_tables.txt)) ---"
grep '^sys_' /tmp/all_tables.txt | tr '\n' ' '; echo
echo ""
echo "--- wf_* / ACT_* / FLW_* / flyway / serial / 其他 ---"
grep -vE '^(biz_|sys_)' /tmp/all_tables.txt | tr '\n' ' '; echo
