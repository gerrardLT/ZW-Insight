-- ============================================================
-- V2026_118__menu_reorganization.sql
-- 双轨镜像：内容必须与 zw-insight-server/zw-app/src/main/resources/db/migration/V2026_118__menu_reorganization.sql 一致
-- 背景：项目管理与资金财务信息架构深度重塑（下沉真实工程词汇 + 二级分类收敛）。
-- 原则：UPDATE 菜单名与排序，保持 path 与权限码不变，确保安全幂等。
-- ============================================================

-- 1) 项目管理域：全面下沉为正规工程常用词
UPDATE sys_menu SET menu_name = '项目台账' WHERE (menu_name = '项目报备' OR menu_name = '项目台账') AND deleted = 0;
UPDATE sys_menu SET menu_name = '工程分解与任务节点（WBS）' WHERE (menu_name = 'WBS结构' OR menu_name = '工作分解结构（WBS）') AND deleted = 0;
UPDATE sys_menu SET menu_name = '投标项目管理' WHERE (menu_name = '投标报名' OR menu_name = '投标记录') AND deleted = 0;
UPDATE sys_menu SET menu_name = '投标证件管理' WHERE menu_name = '证件管理' AND deleted = 0;
UPDATE sys_menu SET menu_name = '施工总承包合同' WHERE (menu_name = '施工合同' OR menu_name = '施工总承包合同') AND deleted = 0;
UPDATE sys_menu SET menu_name = '工程产值申报' WHERE (menu_name = '产值上报' OR menu_name = '工程产值申报') AND deleted = 0;
UPDATE sys_menu SET menu_name = '工程变更签证' WHERE (menu_name = '变更事件' OR menu_name = '工程变更签证') AND deleted = 0;

-- 2) 财务资金域：彻底梳理为 7 大工程业务组命名
UPDATE sys_menu SET menu_name = '工程开票申请' WHERE (menu_name = '开票申请' OR menu_name = '工程开票申请') AND deleted = 0;
UPDATE sys_menu SET menu_name = '工程回款登记' WHERE (menu_name = '回款登记' OR menu_name = '工程回款登记') AND deleted = 0;
UPDATE sys_menu SET menu_name = '工程竣工结算' WHERE (menu_name = '项目最终结算' OR menu_name = '竣工结算' OR menu_name = '工程竣工结算') AND deleted = 0;
UPDATE sys_menu SET menu_name = '应收工程账款台账' WHERE (menu_name = '应收台账' OR menu_name = '应收账款台账' OR menu_name = '应收工程账款台账') AND deleted = 0;

UPDATE sys_menu SET menu_name = '工程付款申请' WHERE (menu_name = '付款申请' OR menu_name = '工程付款申请') AND deleted = 0;
UPDATE sys_menu SET menu_name = '应付账款台账' WHERE (menu_name = '应付账款台账' OR menu_name = '应付台账') AND deleted = 0;
UPDATE sys_menu SET menu_name = '项目现场报销' WHERE (menu_name = '项目报销' OR menu_name = '项目现场报销') AND deleted = 0;
UPDATE sys_menu SET menu_name = '员工日常报销' WHERE (menu_name = '个人报销' OR menu_name = '员工日常报销') AND deleted = 0;

UPDATE sys_menu SET menu_name = '进项发票台账' WHERE (menu_name = '收票登记' OR menu_name = '进项发票' OR menu_name = '进项发票台账') AND deleted = 0;
UPDATE sys_menu SET menu_name = '销项发票台账' WHERE (menu_name = '销项发票台账' OR menu_name = '销项发票') AND deleted = 0;
UPDATE sys_menu SET menu_name = '发票税负统计' WHERE (menu_name = '发票汇总' OR menu_name = '发票税负统计') AND deleted = 0;

UPDATE sys_menu SET menu_name = '银行账户管理' WHERE (menu_name = '账户分组' OR menu_name = '银行账户' OR menu_name = '银行账户管理') AND deleted = 0;
UPDATE sys_menu SET menu_name = '银行流水明细' WHERE (menu_name = '银行流水' OR menu_name = '银行流水明细') AND deleted = 0;
UPDATE sys_menu SET menu_name = '月度资金收支计划' WHERE (menu_name = '资金计划' OR menu_name = '月度资金收支计划') AND deleted = 0;
UPDATE sys_menu SET menu_name = '每日资金头寸日报' WHERE (menu_name = '资金日报' OR menu_name = '每日资金头寸日报') AND deleted = 0;
UPDATE sys_menu SET menu_name = '银行余额调节表' WHERE menu_name = '余额调节表' AND deleted = 0;

UPDATE sys_menu SET menu_name = '投标与履约保证金' WHERE (menu_name = '保证金台账' OR menu_name = '投标与履约保证金') AND deleted = 0;
UPDATE sys_menu SET menu_name = '工程质量保证金' WHERE (menu_name = '质保金台账' OR menu_name = '质保金管理' OR menu_name = '工程质量保证金') AND deleted = 0;
UPDATE sys_menu SET menu_name = '农民工工资专户' WHERE (menu_name = '工资专户' OR menu_name = '农民工工资专户') AND deleted = 0;
UPDATE sys_menu SET menu_name = '备用金借支与核销' WHERE (menu_name = '备用金' OR menu_name = '备用金管理' OR menu_name = '备用金借支与核销') AND deleted = 0;
UPDATE sys_menu SET menu_name = '承兑汇票台账' WHERE (menu_name = '票据台账' OR menu_name = '票据（承兑）' OR menu_name = '承兑汇票台账') AND deleted = 0;
UPDATE sys_menu SET menu_name = '项目融资借款' WHERE (menu_name = '融资借贷' OR menu_name = '融资借款' OR menu_name = '项目融资借款') AND deleted = 0;

UPDATE sys_menu SET menu_name = '资金大屏看板' WHERE (menu_name = '资金看板' OR menu_name = '资金大屏看板') AND deleted = 0;
UPDATE sys_menu SET menu_name = '业务招待费专项' WHERE (menu_name = '招待费分析' OR menu_name = '业务招待费专项') AND deleted = 0;
UPDATE sys_menu SET menu_name = '月度综合经营分析' WHERE (menu_name = '月度经营分析' OR menu_name = '月度综合经营分析') AND deleted = 0;

-- 财务设置类（下沉）
UPDATE sys_menu SET menu_name = '收支科目设置' WHERE (menu_name = '资金分类科目' OR menu_name = '资金科目' OR menu_name = '收支科目设置') AND deleted = 0;
UPDATE sys_menu SET menu_name = '增值税率设置' WHERE (menu_name = '税率管理' OR menu_name = '增值税率设置') AND deleted = 0;
UPDATE sys_menu SET menu_name = '财务关账封账' WHERE (menu_name = '财务封账' OR menu_name = '财务关账封账') AND deleted = 0;

-- 3) 成本管理域（原预算管理）：下沉真实常用词
UPDATE sys_menu SET menu_name = '项目目标成本预算' WHERE (menu_name = '预算编制' OR menu_name = '项目预算' OR menu_name = '项目目标成本预算') AND deleted = 0;
UPDATE sys_menu SET menu_name = '目标成本调整单' WHERE (menu_name = '目标成本变更' OR menu_name = '成本变更单' OR menu_name = '目标成本调整单') AND deleted = 0;
UPDATE sys_menu SET menu_name = '目标成本科目（CBS）' WHERE (menu_name = '成本账户CBS' OR menu_name = '成本科目（CBS）' OR menu_name = '目标成本科目（CBS）') AND deleted = 0;
UPDATE sys_menu SET menu_name = '成本预算控制配置' WHERE (menu_name = '预算控制配置' OR menu_name = '成本预算控制配置') AND deleted = 0;

-- 4) 施工管理、物资、劳务、机械下沉
UPDATE sys_menu SET menu_name = '现场施工进度计划' WHERE (menu_name = '进度计划' OR menu_name = '现场施工进度计划') AND deleted = 0;
UPDATE sys_menu SET menu_name = '工程施工日志' WHERE (menu_name = '施工日志' OR menu_name = '工程施工日志') AND deleted = 0;
UPDATE sys_menu SET menu_name = '工程竣工验收' WHERE (menu_name = '竣工验收' OR menu_name = '工程竣工验收') AND deleted = 0;
UPDATE sys_menu SET menu_name = '施工质量检查' WHERE (menu_name = '质量检查' OR menu_name = '施工质量检查') AND deleted = 0;
UPDATE sys_menu SET menu_name = '现场安全检查' WHERE (menu_name = '安全检查' OR menu_name = '现场安全检查') AND deleted = 0;

UPDATE sys_menu SET menu_name = '材料入库单' WHERE (menu_name = '到货入库' OR menu_name = '入库单' OR menu_name = '材料入库单') AND deleted = 0;
UPDATE sys_menu SET menu_name = '现场领料单' WHERE (menu_name = '领料出库' OR menu_name = '领料单' OR menu_name = '现场领料单') AND deleted = 0;
UPDATE sys_menu SET menu_name = '材料调拨单' WHERE (menu_name = '材料调拨' OR menu_name = '材料调拨单') AND deleted = 0;
UPDATE sys_menu SET menu_name = '材料库存台账' WHERE (menu_name = '库存查询' OR menu_name = '库存台账' OR menu_name = '材料库存台账') AND deleted = 0;

UPDATE sys_menu SET menu_name = '机械租赁合同' WHERE (menu_name = '机械合同' OR menu_name = '机械租赁合同') AND deleted = 0;
UPDATE sys_menu SET menu_name = '机械设备台账' WHERE (menu_name = '机械台账' OR menu_name = '机械设备台账') AND deleted = 0;
UPDATE sys_menu SET menu_name = '机械现场点检与台班' WHERE (menu_name = '台班/工作量' OR menu_name = '机械现场点检与台班') AND deleted = 0;
UPDATE sys_menu SET menu_name = '机械结算单' WHERE (menu_name = '机械结算' OR menu_name = '机械结算单') AND deleted = 0;

UPDATE sys_menu SET menu_name = '劳务分包合同' WHERE (menu_name = '劳务合同' OR menu_name = '劳务分包合同') AND deleted = 0;
UPDATE sys_menu SET menu_name = '施工班组管理' WHERE (menu_name = '班组管理' OR menu_name = '施工班组管理') AND deleted = 0;
UPDATE sys_menu SET menu_name = '现场劳务花名册' WHERE (menu_name = '劳务花名册' OR menu_name = '现场劳务花名册') AND deleted = 0;
UPDATE sys_menu SET menu_name = '现场点工派工单' WHERE (menu_name = '用工单' OR menu_name = '点工单' OR menu_name = '现场点工派工单') AND deleted = 0;
UPDATE sys_menu SET menu_name = '劳务班组工资单' WHERE (menu_name = '工资单' OR menu_name = '劳务班组工资单') AND deleted = 0;
UPDATE sys_menu SET menu_name = '劳务结算单' WHERE (menu_name = '劳务结算' OR menu_name = '劳务结算单') AND deleted = 0;

UPDATE sys_menu SET menu_name = '专业分包合同' WHERE (menu_name = '分包合同' OR menu_name = '专业分包合同') AND deleted = 0;
UPDATE sys_menu SET menu_name = '分包结算单' WHERE (menu_name = '分包结算' OR menu_name = '分包结算单') AND deleted = 0;
UPDATE sys_menu SET menu_name = '分包完成产值' WHERE (menu_name = '分包产值' OR menu_name = '分包完成产值') AND deleted = 0;

UPDATE sys_menu SET menu_name = '物资采购合同' WHERE (menu_name = '采购合同' OR menu_name = '物资采购合同') AND deleted = 0;
UPDATE sys_menu SET menu_name = '采购结算单' WHERE (menu_name = '采购结算' OR menu_name = '采购结算单') AND deleted = 0;
UPDATE sys_menu SET menu_name = '采购询价与比价' WHERE (menu_name = '询价比价' OR menu_name = '采购询价与比价') AND deleted = 0;
