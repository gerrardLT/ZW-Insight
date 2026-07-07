#!/usr/bin/env bash
docker logs zwi-backend > /root/zwi-deploy/be.log 2>&1
# 提取最后一次 "Application run failed" 之后的完整堆栈
awk '/Application run failed/{f=1} f' /root/zwi-deploy/be.log | tail -120 > /root/zwi-deploy/stack.out
# 同时把所有 Caused by 行单列
grep -n "Caused by" /root/zwi-deploy/be.log | tail -15 >> /root/zwi-deploy/stack.out
echo WROTE
