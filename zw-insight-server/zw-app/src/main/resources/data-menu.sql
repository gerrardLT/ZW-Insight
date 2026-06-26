-- ============================================================
-- ZW-Insight 菜单初始化数据
-- 与前端 router/index.ts 路由配置一一对应
-- ============================================================

-- 一级目录
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1,  '首页',       'MENU', 0, '/dashboard',    'views/dashboard/index',    'HomeFilled',     1,  1, 0),
(2,  '系统管理',   'DIR',  0, '/system',       NULL,                       'Setting',        99, 1, 0),
(3,  '项目管理',   'DIR',  0, '/project',      NULL,                       'Briefcase',      2,  1, 0),
(4,  '合同管理',   'DIR',  0, '/contract',     NULL,                       'Notebook',       3,  1, 0),
(5,  '财务管理',   'DIR',  0, '/finance',      NULL,                       'Money',          4,  1, 0),
(6,  '预算管理',   'DIR',  0, '/budget',       NULL,                       'Coin',           5,  1, 0),
(7,  '采购管理',   'DIR',  0, '/purchase',     NULL,                       'ShoppingCart',   6,  1, 0),
(8,  '劳务管理',   'DIR',  0, '/labor',        NULL,                       'Avatar',         7,  1, 0),
(9,  '材料库存',   'DIR',  0, '/material',     NULL,                       'Box',            8,  1, 0),
(10, '机械管理',   'DIR',  0, '/machine',      NULL,                       'Van',            9,  1, 0),
(11, '分包管理',   'DIR',  0, '/subcontract',  NULL,                       'Connection',     10, 1, 0),
(12, '现场管理',   'DIR',  0, '/site',         NULL,                       'Place',          11, 1, 0),
(13, '投标管理',   'DIR',  0, '/tender',       NULL,                       'Trophy',         12, 1, 0),
(14, '行政人事',   'DIR',  0, '/hr',           NULL,                       'School',         13, 1, 0),
(15, '档案管理',   'DIR',  0, '/archive',      NULL,                       'FolderOpened',   14, 1, 0),
(16, '工作流管理', 'DIR',  0, '/workflow',     NULL,                       'Share',          15, 1, 0),
(17, '消息管理',   'DIR',  0, '/message',      NULL,                       'Bell',           16, 1, 0),
(18, '基础数据',   'DIR',  0, '/basedata',     NULL,                       'Grid',           17, 1, 0);

-- ============================================================
-- 一级菜单：项目看板（独立项目维度数据看板，P2 新增）
-- 路由 children 挂载于根布局，path 为 /project-dashboard
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(19, '项目看板', 'MENU', 0, '/project-dashboard', 'views/dashboard/project-dashboard', 'DataAnalysis', 2, 1, 0);

-- ============================================================
-- 二级菜单：系统管理 (parent_id=2)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(201, '机构管理',     'MENU', 2, 'org',           'views/system/org/index',           'OfficeBuilding', 1, 1, 0),
(202, '人员管理',     'MENU', 2, 'user',          'views/system/user/index',          'User',           2, 1, 0),
(203, '角色管理',     'MENU', 2, 'role',          'views/system/role/index',          'UserFilled',     3, 1, 0),
(204, '菜单管理',     'MENU', 2, 'menu',          'views/system/menu/index',          'Menu',           4, 1, 0),
(205, '数据字典',     'MENU', 2, 'dict',          'views/system/dict/index',          'Collection',     5, 1, 0),
(206, '岗位管理',     'MENU', 2, 'post',          'views/system/post/index',          'Stamp',          6, 1, 0),
(207, '日志管理',     'MENU', 2, 'log',           'views/system/log/index',           'Document',       7, 1, 0),
(208, '编号规则管理', 'MENU', 2, 'serial-number', 'views/system/serial-number/index', 'Odometer',       8, 1, 0);

-- 打印模板管理（p2-advanced 需求3.1 新增，parent_id=2）
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(209, '打印模板', 'MENU', 2, 'print-template', 'views/system/print-template/index', 'Printer', 9, 1, 0);

-- 数据运维（p2-advanced 需求11.4/12.2/13.3 新增，parent_id=2）
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission) VALUES
(210, '数据备份', 'MENU', 2, 'backup',  'views/system/backup/index',  'FolderChecked', 10, 1, 0, 'system:backup:list'),
(211, '版本管理', 'MENU', 2, 'version', 'views/system/version/index', 'Tickets',       11, 1, 0, 'system:version:list'),
(212, '系统监控', 'MENU', 2, 'monitor', 'views/system/monitor/index', 'Monitor',       12, 1, 0, 'system:monitor:view');

-- ============================================================
-- 二级菜单：项目管理 (parent_id=3)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(301, '项目报备', 'MENU', 3, 'list',     'views/project/index',  'Document', 1, 1, 0),
(302, '新增项目', 'MENU', 3, 'create',   'views/project/form',   NULL,       2, 1, 1),
(303, '编辑项目', 'MENU', 3, 'edit/:id', 'views/project/form',   NULL,       3, 1, 1);

-- ============================================================
-- 二级菜单：合同管理 (parent_id=4)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(401, '施工合同', 'MENU', 4, 'list',     'views/contract/index', 'Document', 1, 1, 0),
(402, '新增合同', 'MENU', 4, 'create',   'views/contract/form',  NULL,       2, 1, 1),
(403, '编辑合同', 'MENU', 4, 'edit/:id', 'views/contract/form',  NULL,       3, 1, 1);

-- ============================================================
-- 二级菜单：财务管理 (parent_id=5)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(501, '开票申请', 'MENU', 5, 'invoice-apply',    'views/finance/invoice-apply',    'Ticket',      1, 1, 0),
(502, '回款登记', 'MENU', 5, 'payment-received', 'views/finance/payment-received', 'WalletFilled',2, 1, 0),
(503, '付款申请', 'MENU', 5, 'payment-apply',    'views/finance/payment-apply',    'CreditCard',  3, 1, 0),
(504, '项目最终结算', 'MENU', 5, 'settlement', 'views/finance/settlement/index', 'Money', 7, 1, 0);

-- 财务封账与税率管理（P2 新增，parent_id=5）
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(505, '财务封账', 'MENU', 5, 'finance-lock', 'views/finance/finance-lock/index', 'Lock',      5, 1, 0),
(506, '税率管理', 'MENU', 5, 'tax-rate',     'views/finance/tax-rate/index',     'Histogram', 6, 1, 0);

-- 收票登记（p2-business-enhance 需求6.2 新增，parent_id=5）
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(507, '收票登记', 'MENU', 5, 'invoice-received', 'views/finance/invoice-received', 'Tickets', 4, 1, 0);

-- ============================================================
-- 二级菜单：预算管理 (parent_id=6)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(601, '预算编制', 'MENU', 6, 'list',   'views/budget/index',  'Document', 1, 1, 0),
(602, '预算配置', 'MENU', 6, 'config', 'views/budget/config', 'Setting',  2, 1, 0),
(603, '目标成本变更', 'MENU', 6, 'change', 'views/budget/change/index', 'Edit', 3, 1, 0),
(604, '预算控制配置', 'MENU', 6, 'control-config', 'views/budget/control-config/index', 'Setting', 4, 1, 0);

-- ============================================================
-- 二级菜单：采购管理 (parent_id=7)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(701, '采购合同', 'MENU', 7, 'contract',   'views/purchase/contract',   'Document',     1, 1, 0),
(702, '采购结算', 'MENU', 7, 'settlement', 'views/purchase/settlement', 'Tickets',      2, 1, 0),
(703, '询价比价', 'MENU', 7, 'inquiry',    'views/purchase/inquiry',    'DataAnalysis', 3, 1, 0);

-- ============================================================
-- 二级菜单：劳务管理 (parent_id=8)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(801, '劳务合同',   'MENU', 8, 'contract',   'views/labor/contract',   'Document',  1, 1, 0),
(802, '班组管理',   'MENU', 8, 'team',       'views/labor/team',       'UserFilled',2, 1, 0),
(803, '劳务花名册', 'MENU', 8, 'roster',     'views/labor/roster',     'List',      3, 1, 0),
(804, '用工单',     'MENU', 8, 'work-order', 'views/labor/work-order', 'Memo',      4, 1, 0),
(805, '工资单',     'MENU', 8, 'payroll',    'views/labor/payroll',    'Wallet',    5, 1, 0);

-- ============================================================
-- 二级菜单：材料库存 (parent_id=9)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(901, '到货入库', 'MENU', 9, 'inbound',  'views/material/inbound',  'Download', 1, 1, 0),
(902, '领料出库', 'MENU', 9, 'outbound', 'views/material/outbound', 'Upload',   2, 1, 0),
(903, '材料调拨', 'MENU', 9, 'transfer', 'views/material/transfer', 'Switch',   3, 1, 0),
(904, '库存查询', 'MENU', 9, 'stock',    'views/material/stock',    'Search',   4, 1, 0);

-- ============================================================
-- 二级菜单：机械管理 (parent_id=10)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1001, '机械合同',   'MENU', 10, 'contract', 'views/machine/contract', 'Document',            1, 1, 0),
(1002, '机械台账',   'MENU', 10, 'ledger',   'views/machine/ledger',   'Notebook',            2, 1, 0),
(1003, '进出场登记', 'MENU', 10, 'entry',    'views/machine/entry',    'MapLocation',         3, 1, 0),
(1004, '台班/工作量','MENU', 10, 'work-log', 'views/machine/work-log', 'Timer',               4, 1, 0),
(1005, '故障维修',   'MENU', 10, 'repair',   'views/machine/repair',   'WarnTriangleFilled',  5, 1, 0);

-- ============================================================
-- 二级菜单：分包管理 (parent_id=11)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1101, '分包合同', 'MENU', 11, 'contract',   'views/subcontract/contract',   'Document', 1, 1, 0),
(1102, '分包结算', 'MENU', 11, 'settlement', 'views/subcontract/settlement', 'Tickets',  2, 1, 0);

-- ============================================================
-- 二级菜单：现场管理 (parent_id=12)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1201, '进度计划',     'MENU', 12, 'schedule',         'views/site/schedule',         'DataLine',    1, 1, 0),
(1202, '施工日志',     'MENU', 12, 'construction-log', 'views/site/construction-log', 'Memo',        2, 1, 0),
(1203, '质量安全检查', 'MENU', 12, 'inspection',       'views/site/inspection',       'CircleCheck', 3, 1, 0);

-- ============================================================
-- 二级菜单：投标管理 (parent_id=13)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1301, '投标报名', 'MENU', 13, 'register',    'views/tender/register',    'EditPen',  1, 1, 0),
(1302, '证件管理', 'MENU', 13, 'certificate', 'views/tender/certificate', 'Postcard', 2, 1, 0);

-- ============================================================
-- 二级菜单：行政人事 (parent_id=14)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1401, '入职申请', 'MENU', 14, 'entry',         'views/hr/entry',         'Plus',  1, 1, 0),
(1402, '办公用品', 'MENU', 14, 'office-supply', 'views/hr/office-supply', 'Goods', 2, 1, 0),
(1403, '车辆管理', 'MENU', 14, 'vehicle',       'views/hr/vehicle',       'Van',   3, 1, 0);

-- ============================================================
-- 二级菜单：档案管理 (parent_id=15)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1501, '档案查询', 'MENU', 15, 'index', 'views/archive/index', 'Search', 1, 1, 0);

-- ============================================================
-- 二级菜单：工作流管理 (parent_id=16)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1601, '流程设计器', 'MENU', 16, 'designer',      'views/workflow/designer/index',       'EditPen',    1, 1, 0),
(1602, '流程定义',   'MENU', 16, 'process',       'views/workflow/process/index',        'Document',   2, 1, 0),
(1603, '业务类型',   'MENU', 16, 'business-type', 'views/workflow/business-type/index',  'Collection', 3, 1, 0),
(1604, '审批管理',   'MENU', 16, 'approval',      'views/workflow/approval/index',       'Checked',    4, 1, 0);

-- ============================================================
-- 二级菜单：消息管理 (parent_id=17)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1701, '通知管理',     'MENU', 17, 'notice',      'views/message/notice/index',      'Notification',   1, 1, 0),
(1702, '公告管理',     'MENU', 17, 'announcement','views/message/announcement/index','ChatDotSquare',  2, 1, 0),
(1703, '推送渠道配置', 'MENU', 17, 'push-config', 'views/message/push-config/index', 'Connection',     3, 1, 0),
(1704, '消息中心',     'MENU', 17, 'center',      'views/message/center/index',      'Message',        4, 1, 0);

-- ============================================================
-- 二级菜单：基础数据 (parent_id=18)
-- ============================================================
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(1801, '材料字典',     'MENU', 18, 'material',             'views/basedata/material',             'Document',      1, 1, 0),
(1802, '供应商',       'MENU', 18, 'supplier',             'views/basedata/supplier',             'OfficeBuilding',2, 1, 0),
(1803, '甲方单位',     'MENU', 18, 'owner',                'views/basedata/owner',                'Star',          3, 1, 0),
(1804, '自持公司',     'MENU', 18, 'company',              'views/basedata/company',              'HomeFilled',    4, 1, 0),
(1805, '检查方案',     'MENU', 18, 'inspection-scheme',    'views/basedata/inspection-scheme',    'Checked',       5, 1, 0),
(1806, '供应商评价',   'MENU', 18, 'supplier-evaluation',  'views/basedata/supplier-evaluation',  'Star',          6, 1, 0),
(1807, '供应商黑名单', 'MENU', 18, 'supplier-blacklist',   'views/basedata/supplier-blacklist',   'CloseBold',     7, 1, 0);

-- ============================================================
-- 超级管理员角色
-- ============================================================
INSERT INTO sys_role (id, role_name, role_code, sort_order, status, tenant_id, created_at) VALUES
(1, '超级管理员', 'SUPER_ADMIN', 1, 1, NULL, NOW());

-- 超级管理员关联所有菜单
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
(1, 1, 1), (2, 1, 2), (3, 1, 3), (4, 1, 4), (5, 1, 5), (6, 1, 6), (7, 1, 7), (8, 1, 8),
(9, 1, 9), (10, 1, 10), (11, 1, 11), (12, 1, 12), (13, 1, 13), (14, 1, 14), (15, 1, 15),
(16, 1, 16), (17, 1, 17), (18, 1, 18),
(19, 1, 201), (20, 1, 202), (21, 1, 203), (22, 1, 204), (23, 1, 205), (24, 1, 206), (25, 1, 207), (83, 1, 208),
(26, 1, 301), (27, 1, 302), (28, 1, 303),
(29, 1, 401), (30, 1, 402), (31, 1, 403),
(32, 1, 501), (33, 1, 502), (34, 1, 503), (80, 1, 504),
(35, 1, 601), (36, 1, 602), (81, 1, 603), (82, 1, 604),
(37, 1, 701), (38, 1, 702), (39, 1, 703),
(40, 1, 801), (41, 1, 802), (42, 1, 803), (43, 1, 804), (44, 1, 805),
(45, 1, 901), (46, 1, 902), (47, 1, 903), (48, 1, 904),
(49, 1, 1001), (50, 1, 1002), (51, 1, 1003), (52, 1, 1004), (53, 1, 1005),
(54, 1, 1101), (55, 1, 1102),
(56, 1, 1201), (57, 1, 1202), (58, 1, 1203),
(59, 1, 1301), (60, 1, 1302),
(61, 1, 1401), (62, 1, 1402), (63, 1, 1403),
(64, 1, 1501),
(65, 1, 1601), (66, 1, 1602), (67, 1, 1603), (68, 1, 1604),
(69, 1, 1701), (70, 1, 1702), (71, 1, 1703), (72, 1, 1704),
(73, 1, 1801), (74, 1, 1802), (75, 1, 1803), (76, 1, 1804), (77, 1, 1805), (78, 1, 1806), (79, 1, 1807),
(84, 1, 19), (85, 1, 505), (86, 1, 506), (87, 1, 507), (88, 1, 209);

-- 数据运维菜单授权超级管理员（p2-advanced 需求11.4/12.2/13.3）
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
(89, 1, 210), (90, 1, 211), (91, 1, 212);

-- 默认管理员账号（密码BCrypt加密后的123456）
INSERT INTO sys_user (id, username, password, real_name, phone, org_id, status, tenant_id, created_at) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13800000000', NULL, 1, NULL, NOW());

INSERT INTO sys_user_role (id, user_id, role_id) VALUES (1, 1, 1);
