#!/usr/bin/env bash
# 逐个尝试候选 JRE 镜像，哪个能在限时内拉下来就用哪个
CANDS="eclipse-temurin:21-jre eclipse-temurin:17-jre amazoncorretto:21 amazoncorretto:17 openjdk:21-jdk-slim openjdk:17-jdk-slim bellsoft/liberica-openjre-debian:21"
> /root/zwi-deploy/pull.out
for img in $CANDS; do
  echo "=== try $img ===" >> /root/zwi-deploy/pull.out
  if timeout 120 docker pull "$img" >> /root/zwi-deploy/pull.out 2>&1; then
    echo "SUCCESS: $img" >> /root/zwi-deploy/pull.out
    echo "WINNER=$img" >> /root/zwi-deploy/pull.out
    break
  else
    echo "FAIL($?): $img" >> /root/zwi-deploy/pull.out
  fi
done
tail -3 /root/zwi-deploy/pull.out
