-- ============================================================
-- V2026_78__print_templates_seed.sql
-- 打印版式模板资产：付款申请单 / 工程结算单
--
-- 背景：打印链路（sys_template PRINT 版式 → ThymeleafRenderService 渲染 →
-- PrintButton 隐藏 iframe window.print）早已就绪，但版式模板从未种入——
-- 打印功能对用户不可用。本迁移补两个最高频单据的版式：
--   2101 付款申请单（business_type=PAYMENT_APPLY）
--   2102 工程结算单（business_type=SETTLEMENT，含明细行 th:each）
-- 前端用法：<PrintButton business-type="PAYMENT_APPLY" :variables="{...单据字段...}" />
-- 模板语法：Thymeleaf HTML 模式（th:*/SpringEL），变量缺失时留空不报错。
-- 幂等：INSERT IGNORE（主键冲突跳过）；ID 段 2101-2102 高于导入模板 2001-2005。
-- 双轨：deploy/db-init/80_V2026_78__print_templates_seed.sql 同内容。
-- ============================================================

INSERT IGNORE INTO sys_template (id, template_name, template_type, module_code, business_type, template_content, engine_type, is_default, created_at, updated_at) VALUES
(2101, '付款申请单', 'PRINT', 'FINANCE', 'PAYMENT_APPLY',
'<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
<meta charset="UTF-8">
<title>付款申请单</title>
<style>
  body { font-family: "SimSun", serif; color: #000; margin: 24px; font-size: 14px; }
  h1 { text-align: center; font-size: 22px; letter-spacing: 6px; margin: 0 0 4px; }
  .doc-no { text-align: right; color: #333; margin-bottom: 12px; }
  table.info { width: 100%; border-collapse: collapse; margin-bottom: 6px; }
  table.info td { border: 1px solid #000; padding: 8px 10px; }
  table.info .lbl { width: 110px; background: #f5f5f5; text-align: center; white-space: nowrap; }
  .reason { border: 1px solid #000; border-top: none; padding: 10px; min-height: 64px; }
  .reason .lbl { display: inline-block; width: 100px; text-align: center; }
  .sign-row { display: flex; margin-top: 28px; }
  .sign-row div { flex: 1; text-align: center; }
  .amount { font-weight: bold; }
</style>
</head>
<body>
<h1>付款申请单</h1>
<div class="doc-no" th:text="${applyNo}">编号</div>
<table class="info">
  <tr>
    <td class="lbl">项目名称</td>
    <td colspan="3" th:text="${projectName}">--</td>
  </tr>
  <tr>
    <td class="lbl">关联合同</td>
    <td colspan="3" th:text="${contractName}">--</td>
  </tr>
  <tr>
    <td class="lbl">收款单位</td>
    <td th:text="${payeeName}">--</td>
    <td class="lbl">计划付款日</td>
    <td th:text="${paymentDate}">--</td>
  </tr>
  <tr>
    <td class="lbl">申请金额(元)</td>
    <td class="amount" th:text="${amount}">0.00</td>
    <td class="lbl">申请日期</td>
    <td th:text="${applyDate}">--</td>
  </tr>
</table>
<div class="reason">
  <span class="lbl">付款事由</span>
  <div style="margin-top:6px" th:text="${reason}">--</div>
</div>
<div class="sign-row">
  <div>申请人：____________</div>
  <div>部门负责人：____________</div>
  <div>财务负责人：____________</div>
  <div>总经理：____________</div>
</div>
</body>
</html>',
'THYMELEAF', 1, NOW(), NOW()),
(2102, '工程结算单', 'PRINT', 'FINANCE', 'SETTLEMENT',
'<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
<meta charset="UTF-8">
<title>工程结算单</title>
<style>
  body { font-family: "SimSun", serif; color: #000; margin: 24px; font-size: 14px; }
  h1 { text-align: center; font-size: 22px; letter-spacing: 6px; margin: 0 0 4px; }
  .doc-no { text-align: right; color: #333; margin-bottom: 12px; }
  table.info { width: 100%; border-collapse: collapse; margin-bottom: 14px; }
  table.info td { border: 1px solid #000; padding: 8px 10px; }
  table.info .lbl { width: 110px; background: #f5f5f5; text-align: center; white-space: nowrap; }
  table.items { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
  table.items th, table.items td { border: 1px solid #000; padding: 6px 8px; text-align: center; }
  table.items th { background: #f5f5f5; }
  .total-row td { font-weight: bold; }
  .sign-row { display: flex; margin-top: 28px; }
  .sign-row div { flex: 1; text-align: center; }
</style>
</head>
<body>
<h1>工程结算单</h1>
<div class="doc-no" th:text="${settlementNo}">编号</div>
<table class="info">
  <tr>
    <td class="lbl">项目名称</td>
    <td th:text="${projectName}">--</td>
    <td class="lbl">关联合同</td>
    <td th:text="${contractName}">--</td>
  </tr>
  <tr>
    <td class="lbl">发包单位</td>
    <td th:text="${firstParty}">--</td>
    <td class="lbl">承包单位</td>
    <td th:text="${secondParty}">--</td>
  </tr>
  <tr>
    <td class="lbl">结算期间</td>
    <td colspan="3"><span th:text="${periodStart}">--</span> 至 <span th:text="${periodEnd}">--</span></td>
  </tr>
</table>
<table class="items">
  <thead>
    <tr>
      <th style="width:36px">序号</th>
      <th>结算内容</th>
      <th style="width:70px">单位</th>
      <th style="width:70px">数量</th>
      <th style="width:90px">单价(元)</th>
      <th style="width:100px">金额(元)</th>
    </tr>
  </thead>
  <tbody>
    <tr th:each="item, stat : ${items}">
      <td th:text="${stat.count}">1</td>
      <td style="text-align:left" th:text="${item.name}">--</td>
      <td th:text="${item.unit}">--</td>
      <td th:text="${item.qty}">--</td>
      <td th:text="${item.price}">--</td>
      <td th:text="${item.amount}">--</td>
    </tr>
    <tr class="total-row">
      <td colspan="5">结算金额合计(元)</td>
      <td th:text="${totalAmount}">0.00</td>
    </tr>
  </tbody>
</table>
<div class="sign-row">
  <div>编制：____________</div>
  <div>对方确认：____________</div>
  <div>审批：____________</div>
  <div>日期：____________</div>
</div>
</body>
</html>',
'THYMELEAF', 1, NOW(), NOW());
