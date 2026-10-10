-- 回款登记改为「登记即已认领」（2026-10-10）
-- 背景：认领/核销（V2026_47 引入）是纯文档状态机——claim_status 无任何业务消费方，
-- 而登记时项目/合同已绑定、金额回写与应收 FIFO 核销均已立即生效，「待认领」在本流程中不存在。
-- 本脚本回填存量待认领行，使 claim_status 与新的登记语义一致。
-- 幂等：二次执行匹配 0 行；不写金额字段，不影响项目总收入/合同累计收款/应收台账勾稽。
UPDATE biz_payment_received
   SET claim_status = 'CLAIMED',
       claimed_by   = COALESCE(claimed_by, created_by),
       claimed_at   = COALESCE(claimed_at, created_at)
 WHERE claim_status = 'UNCLAIMED';
