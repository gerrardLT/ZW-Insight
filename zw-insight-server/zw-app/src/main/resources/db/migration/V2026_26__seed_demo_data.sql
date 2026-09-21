-- ============================================
-- ZW-Insight 全模块演示数据种子脚本
-- 租户: tenant_id=1
-- ID段: 90001-99999（避免与业务ID冲突）
-- 幂等: INSERT IGNORE 可重复执行
-- 系统管理(Layer S): 机构/岗位/角色/业务用户(密码 123456)/授权/字典
-- 项目矩阵:
--   90001 滨江花园一期工程 (CONSTRUCTION 施工中) - 全模块完整数据
--   90002 城南市政道路改造 (COMPLETED 已竣工) - 结算+质保金
--   90003 高新区产业园二期 (FILED 已报备) - 投标+初始预算
-- ============================================

-- 强制连接字符集为 utf8mb4，防止在 character_set_client=latin1 的客户端环境下
-- 导入时把 UTF-8 字节按 latin1 误读造成双重编码损坏（根因规避）。
SET NAMES utf8mb4;

-- ============ Layer S: 系统管理 ============

-- 机构（公司 + 职能部门 + 项目部，树形）
INSERT IGNORE INTO sys_org (id, org_name, org_code, org_type, parent_id, sort_order, status, ancestors, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90021, '中正建设集团有限公司', 'ORG-ZZ', 'COMPANY',    0,     1, 1, '0',            1, 1, NOW(), NOW(), 0, 0),
(90022, '工程管理部',         'ORG-GC', 'DEPARTMENT', 90021, 1, 1, '0,90021',      1, 1, NOW(), NOW(), 0, 0),
(90023, '财务部',             'ORG-CW', 'DEPARTMENT', 90021, 2, 1, '0,90021',      1, 1, NOW(), NOW(), 0, 0),
(90024, '物资采购部',         'ORG-WZ', 'DEPARTMENT', 90021, 3, 1, '0,90021',      1, 1, NOW(), NOW(), 0, 0),
(90025, '商务合约部',         'ORG-SW', 'DEPARTMENT', 90021, 4, 1, '0,90021',      1, 1, NOW(), NOW(), 0, 0),
(90026, '滨江花园项目部',     'ORG-BJ', 'DEPARTMENT', 90022, 1, 1, '0,90021,90022', 1, 1, NOW(), NOW(), 0, 0),
(90027, '城南市政项目部',     'ORG-CN', 'DEPARTMENT', 90022, 2, 1, '0,90021,90022', 1, 1, NOW(), NOW(), 0, 0);

-- 岗位
INSERT IGNORE INTO sys_post (id, post_name, post_code, status, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90041, '项目经理',   'PM',          1, 1, 1, 1, NOW(), NOW(), 0, 0),
(90042, '技术负责人', 'TECH',        1, 2, 1, 1, NOW(), NOW(), 0, 0),
(90043, '财务主管',   'FINANCE',     1, 3, 1, 1, NOW(), NOW(), 0, 0),
(90044, '材料员',     'MATERIAL',    1, 4, 1, 1, NOW(), NOW(), 0, 0),
(90045, '施工员',     'CONSTRUCTOR', 1, 5, 1, 1, NOW(), NOW(), 0, 0),
(90046, '商务经理',   'COMMERCE',    1, 6, 1, 1, NOW(), NOW(), 0, 0),
(90047, '资料员',     'ARCHIVIST',   1, 7, 1, 1, NOW(), NOW(), 0, 0);

-- 角色（租户级业务角色）
INSERT IGNORE INTO sys_role (id, role_name, role_code, remark, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90061, '项目经理', 'PROJECT_MANAGER', '项目全流程管理权限', 1, 1, 1, NOW(), NOW(), 0, 0),
(90062, '财务人员', 'FINANCE_STAFF',   '财务与预算相关权限', 1, 1, 1, NOW(), NOW(), 0, 0),
(90063, '材料员',   'MATERIAL_STAFF',  '采购与材料库存权限', 1, 1, 1, NOW(), NOW(), 0, 0),
(90064, '商务人员', 'COMMERCE_STAFF',  '合同与投标相关权限', 1, 1, 1, NOW(), NOW(), 0, 0),
(90065, '普通员工', 'STAFF',           '基础查看权限',       1, 1, 1, NOW(), NOW(), 0, 0);

-- 用户（密码均为 123456，BCrypt 哈希与 admin 相同）
INSERT IGNORE INTO sys_user (id, username, password, real_name, phone, email, avatar, status, org_id, post_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90071, 'zhangwei',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张伟', '13811110001', 'zhangwei@zwjs.com',  NULL, 1, 90026, 90041, 1, 1, NOW(), NOW(), 0, 0),
(90072, 'lina',      '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李娜', '13811110002', 'lina@zwjs.com',      NULL, 1, 90026, 90042, 1, 1, NOW(), NOW(), 0, 0),
(90073, 'wangqiang', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王强', '13811110003', 'wangqiang@zwjs.com', NULL, 1, 90023, 90043, 1, 1, NOW(), NOW(), 0, 0),
(90074, 'liumin',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '刘敏', '13811110004', 'liumin@zwjs.com',    NULL, 1, 90024, 90044, 1, 1, NOW(), NOW(), 0, 0),
(90075, 'chengang',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '陈刚', '13811110005', 'chengang@zwjs.com',  NULL, 1, 90027, 90041, 1, 1, NOW(), NOW(), 0, 0),
(90076, 'zhaolei',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵磊', '13811110006', 'zhaolei@zwjs.com',   NULL, 1, 90025, 90046, 1, 1, NOW(), NOW(), 0, 0),
(90077, 'sunli',     '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '孙丽', '13811110007', 'sunli@zwjs.com',     NULL, 1, 90022, 90047, 1, 1, NOW(), NOW(), 0, 0);

-- 用户-角色关联
INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES
(90081, 90071, 90061),
(90082, 90072, 90065),
(90083, 90073, 90062),
(90084, 90074, 90063),
(90085, 90075, 90061),
(90086, 90076, 90064),
(90087, 90077, 90065);

-- 用户-项目数据权限（业务用户登录后可见的项目范围）
INSERT IGNORE INTO sys_user_project (id, user_id, project_id, tenant_id, created_at) VALUES
(90091, 90071, 90001, 1, NOW()),
(90092, 90072, 90001, 1, NOW()),
(90093, 90074, 90001, 1, NOW()),
(90094, 90077, 90001, 1, NOW()),
(90095, 90075, 90002, 1, NOW()),
(90096, 90073, 90002, 1, NOW()),
(90097, 90071, 90003, 1, NOW()),
(90098, 90072, 90003, 1, NOW());

-- 角色-菜单授权（业务角色的功能菜单范围，菜单 ID 见 99_data-menu.sql）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
-- 项目经理：首页/看板/项目/合同/预算/现场/劳务/材料/机械/分包/消息
(90001, 90061, 1),(90002, 90061, 19),(90003, 90061, 3),(90004, 90061, 301),(90005, 90061, 4),(90006, 90061, 401),(90007, 90061, 6),(90008, 90061, 601),(90009, 90061, 12),(90010, 90061, 8),(90011, 90061, 801),(90012, 90061, 802),(90013, 90061, 803),(90014, 90061, 804),(90015, 90061, 805),(90016, 90061, 9),(90017, 90061, 901),(90018, 90061, 902),(90019, 90061, 903),(90020, 90061, 904),(90021, 90061, 10),(90022, 90061, 1001),(90023, 90061, 1002),(90024, 90061, 1003),(90025, 90061, 1004),(90026, 90061, 1005),(90027, 90061, 11),(90028, 90061, 17),
-- 财务人员：首页/财务/预算/消息
(90029, 90062, 1),(90030, 90062, 5),(90031, 90062, 501),(90032, 90062, 502),(90033, 90062, 503),(90034, 90062, 504),(90035, 90062, 505),(90036, 90062, 506),(90037, 90062, 507),(90038, 90062, 6),(90039, 90062, 601),(90040, 90062, 17),
-- 材料员：首页/采购/材料/消息
(90041, 90063, 1),(90042, 90063, 7),(90043, 90063, 701),(90044, 90063, 702),(90045, 90063, 703),(90046, 90063, 9),(90047, 90063, 901),(90048, 90063, 902),(90049, 90063, 903),(90050, 90063, 904),(90051, 90063, 17),
-- 商务人员：首页/合同/投标/采购/消息
(90052, 90064, 1),(90053, 90064, 4),(90054, 90064, 401),(90055, 90064, 402),(90056, 90064, 403),(90057, 90064, 13),(90058, 90064, 7),(90059, 90064, 701),(90060, 90064, 703),(90061, 90064, 17),
-- 普通员工：首页/消息
(90062, 90065, 1),(90063, 90065, 17);

-- 数据字典
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90341, '项目性质',   'project_nature', 1, 1, 1, NOW(), NOW(), 0, 0),
(90342, '项目类型',   'project_type',   2, 1, 1, NOW(), NOW(), 0, 0),
(90343, '合同类型',   'contract_type',  3, 1, 1, NOW(), NOW(), 0, 0),
(90344, '供应商类型', 'supplier_type',  4, 1, 1, NOW(), NOW(), 0, 0),
(90345, '费用类别',   'cost_category',  5, 1, 1, NOW(), NOW(), 0, 0),
(90346, '单据状态',   'audit_status',   6, 1, 1, NOW(), NOW(), 0, 0),
(90347, '发票类型',   'invoice_type',   7, 1, 1, NOW(), NOW(), 0, 0),
(90348, '机械类型',   'machine_type',   8, 1, 1, NOW(), NOW(), 0, 0);

-- 字典值
INSERT IGNORE INTO sys_dict_item (id, dict_id, parent_id, label, value, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90361, 90341, 0, '新建', '新建', 1, 1, 1, NOW(), NOW(), 0, 0),
(90362, 90341, 0, '改建', '改建', 2, 1, 1, NOW(), NOW(), 0, 0),
(90363, 90341, 0, '扩建', '扩建', 3, 1, 1, NOW(), NOW(), 0, 0),
(90364, 90341, 0, '迁建', '迁建', 4, 1, 1, NOW(), NOW(), 0, 0),
(90365, 90342, 0, '房建工程', '房建工程', 1, 1, 1, NOW(), NOW(), 0, 0),
(90366, 90342, 0, '市政工程', '市政工程', 2, 1, 1, NOW(), NOW(), 0, 0),
(90367, 90342, 0, '工业厂房', '工业厂房', 3, 1, 1, NOW(), NOW(), 0, 0),
(90368, 90342, 0, '公路工程', '公路工程', 4, 1, 1, NOW(), NOW(), 0, 0),
(90369, 90342, 0, '园林绿化', '园林绿化', 5, 1, 1, NOW(), NOW(), 0, 0),
(90370, 90343, 0, '主合同',   'REGISTER',   1, 1, 1, NOW(), NOW(), 0, 0),
(90371, 90343, 0, '补充协议', 'SUPPLEMENT', 2, 1, 1, NOW(), NOW(), 0, 0),
(90372, 90344, 0, '材料', 'MATERIAL',    1, 1, 1, NOW(), NOW(), 0, 0),
(90373, 90344, 0, '劳务', 'LABOR',       2, 1, 1, NOW(), NOW(), 0, 0),
(90374, 90344, 0, '机械', 'MACHINE',     3, 1, 1, NOW(), NOW(), 0, 0),
(90375, 90344, 0, '分包', 'SUBCONTRACT', 4, 1, 1, NOW(), NOW(), 0, 0),
(90376, 90345, 0, '材料费', 'MATERIAL',    1, 1, 1, NOW(), NOW(), 0, 0),
(90377, 90345, 0, '人工费', 'LABOR',       2, 1, 1, NOW(), NOW(), 0, 0),
(90378, 90345, 0, '机械费', 'MACHINE',     3, 1, 1, NOW(), NOW(), 0, 0),
(90379, 90345, 0, '分包费', 'SUBCONTRACT', 4, 1, 1, NOW(), NOW(), 0, 0),
(90380, 90345, 0, '间接费', 'INDIRECT',    5, 1, 1, NOW(), NOW(), 0, 0),
(90381, 90346, 0, '草稿',   'DRAFT',    1, 1, 1, NOW(), NOW(), 0, 0),
(90382, 90346, 0, '审批中', 'PENDING',  2, 1, 1, NOW(), NOW(), 0, 0),
(90383, 90346, 0, '已通过', 'APPROVED', 3, 1, 1, NOW(), NOW(), 0, 0),
(90384, 90346, 0, '已驳回', 'REJECTED', 4, 1, 1, NOW(), NOW(), 0, 0),
(90385, 90347, 0, '增值税专用发票', 'SPECIAL', 1, 1, 1, NOW(), NOW(), 0, 0),
(90386, 90347, 0, '增值税普通发票', 'NORMAL',  2, 1, 1, NOW(), NOW(), 0, 0),
(90387, 90348, 0, '土方机械', 'EARTH',     1, 1, 1, NOW(), NOW(), 0, 0),
(90388, 90348, 0, '起重机械', 'LIFT',      2, 1, 1, NOW(), NOW(), 0, 0),
(90389, 90348, 0, '运输机械', 'TRANSPORT', 3, 1, 1, NOW(), NOW(), 0, 0),
(90390, 90348, 0, '砼机械',   'CONCRETE',  4, 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 0: 基础数据 ============

-- 材料分类
INSERT IGNORE INTO bd_material_category (id, category_name, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90101, '钢材', 0, 1, 1, 1, NOW(), NOW(), 0, 0),
(90102, '水泥', 0, 2, 1, 1, NOW(), NOW(), 0, 0),
(90103, '砂石', 0, 3, 1, 1, NOW(), NOW(), 0, 0),
(90104, '管材', 0, 4, 1, 1, NOW(), NOW(), 0, 0),
(90105, '电气', 0, 5, 1, 1, NOW(), NOW(), 0, 0);

-- 材料字典
INSERT IGNORE INTO bd_material (id, material_name, specification, unit, category_id, category_name, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90201, '螺纹钢', 'HRB400 Φ25', '吨', 90101, '钢材', 1, 1, NOW(), NOW(), 0, 0),
(90202, '盘螺', 'HRB400 Φ8', '吨', 90101, '钢材', 1, 1, NOW(), NOW(), 0, 0),
(90203, 'H型钢', 'Q235B 200×200', '吨', 90101, '钢材', 1, 1, NOW(), NOW(), 0, 0),
(90204, '硅酸盐水泥', 'P.O 42.5', '吨', 90102, '水泥', 1, 1, NOW(), NOW(), 0, 0),
(90205, '白水泥', 'P.W 42.5', '吨', 90102, '水泥', 1, 1, NOW(), NOW(), 0, 0),
(90206, '水泥砖', 'MU10 240×115×53', '万块', 90102, '水泥', 1, 1, NOW(), NOW(), 0, 0),
(90207, '中砂', '细度模数2.3-3.0', '方', 90103, '砂石', 1, 1, NOW(), NOW(), 0, 0),
(90208, '碎石', '5-25mm', '方', 90103, '砂石', 1, 1, NOW(), NOW(), 0, 0),
(90209, '机制砂', '细度模数2.6-3.6', '方', 90103, '砂石', 1, 1, NOW(), NOW(), 0, 0),
(90210, 'PE给水管', 'DN200 1.0MPa', '米', 90104, '管材', 1, 1, NOW(), NOW(), 0, 0),
(90211, 'PVC排水管', 'DN110', '米', 90104, '管材', 1, 1, NOW(), NOW(), 0, 0),
(90212, '镀锌钢管', 'DN100 4mm壁厚', '米', 90104, '管材', 1, 1, NOW(), NOW(), 0, 0),
(90213, '电力电缆', 'YJV-3×120+1×70', '米', 90105, '电气', 1, 1, NOW(), NOW(), 0, 0),
(90214, '配电箱', 'XL-21 630A', '台', 90105, '电气', 1, 1, NOW(), NOW(), 0, 0),
(90215, 'LED灯具', '100W 防水', '套', 90105, '电气', 1, 1, NOW(), NOW(), 0, 0);

-- 自持公司
INSERT IGNORE INTO bd_company (id, company_name, company_code, legal_person, registered_capital, address, contact_name, contact_phone, bank_name, bank_account, tax_number, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90301, '中正建设集团有限公司', 'ZZ-JS', '张建国', '5000万', '杭州市滨江区江南大道128号', '李明', '13800001111', '中国工商银行杭州分行', '3301001001001001', '91330100MA1234567X', 1, 1, 1, NOW(), NOW(), 0, 0),
(90302, '中正市政工程有限公司', 'ZZ-SZ', '张建国', '3000万', '杭州市滨江区江南大道128号3楼', '王芳', '13800002222', '中国建设银行杭州分行', '3301002002002002', '91330100MA7654321Y', 1, 1, 1, NOW(), NOW(), 0, 0);

-- 供应商
INSERT IGNORE INTO bd_supplier (id, supplier_name, supplier_code, supplier_type, contact_name, contact_phone, address, bank_name, bank_account, tax_number, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90401, '杭州鑫达建材有限公司', 'GYS-001', 'MATERIAL', '陈伟', '13900001111', '杭州市萧山区建材市场A区', '中国农业银行萧山支行', '6228001001001001', '91330100MA1111111A', 1, 1, 1, NOW(), NOW(), 0, 0),
(90402, '浙江宏远钢铁贸易有限公司', 'GYS-002', 'MATERIAL', '刘洋', '13900002222', '杭州市余杭区钢材城B栋', '中国银行余杭支行', '6228002002002002', '91330100MA2222222B', 1, 1, 1, NOW(), NOW(), 0, 0),
(90403, '杭州顺安劳务有限公司', 'GYS-003', 'LABOR', '赵强', '13900003333', '杭州市西湖区文三路88号', '招商银行杭州分行', '6228003003003003', '91330100MA3333333C', 1, 1, 1, NOW(), NOW(), 0, 0),
(90404, '浙江力源机械租赁有限公司', 'GYS-004', 'MACHINE', '孙磊', '13900004444', '杭州市拱墅区石祥路56号', '交通银行杭州分行', '6228004004004004', '91330100MA4444444D', 1, 1, 1, NOW(), NOW(), 0, 0),
(90405, '杭州恒基装饰工程有限公司', 'GYS-005', 'SUBCONTRACT', '周涛', '13900005555', '杭州市上城区解放路12号', '中国工商银行杭州分行', '6228005005005005', '91330100MA5555555E', 1, 1, 1, NOW(), NOW(), 0, 0),
(90406, '浙江中天幕墙工程有限公司', 'GYS-006', 'SUBCONTRACT', '吴斌', '13900006666', '杭州市滨江区网商路699号', '浦发银行杭州分行', '6228006006006006', '91330100MA6666666F', 1, 1, 1, NOW(), NOW(), 0, 0);

-- 甲方单位
INSERT IGNORE INTO bd_owner (id, owner_name, owner_code, contact_name, contact_phone, address, invoice_title, taxpayer_no, bank_name, bank_account, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90501, '杭州滨江房地产开发有限公司', 'JF-001', '钱总', '13700001111', '杭州市滨江区星光大道99号', '杭州滨江房地产开发有限公司', '91330100MAAAAAAA1', '中国工商银行杭州分行', '3301009001001001', 1, 1, 1, NOW(), NOW(), 0, 0),
(90502, '杭州市城南市政建设管理中心', 'JF-002', '孙主任', '13700002222', '杭州市上城区望江路1号', '杭州市城南市政建设管理中心', '91330100MABBBBBB2', '中国建设银行杭州分行', '3301009002002002', 1, 1, 1, NOW(), NOW(), 0, 0),
(90503, '杭州高新产业园区发展有限公司', 'JF-003', '吕经理', '13700003333', '杭州市余杭区文一西路998号', '杭州高新产业园区发展有限公司', '91330100MACCCCCC3', '中国农业银行杭州分行', '3301009003003003', 1, 1, 1, NOW(), NOW(), 0, 0);

-- 检查方案
INSERT IGNORE INTO bd_inspection_scheme (id, scheme_name, scheme_type, content, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90601, '主体结构质量检查方案', 'QUALITY', '[{"item":"混凝土强度","standard":"C30及以上","method":"回弹法"},{"item":"钢筋间距","standard":"偏差±10mm","method":"尺量"},{"item":"保护层厚度","standard":"偏差±5mm","method":"仪器检测"}]', 1, 1, 1, NOW(), NOW(), 0, 0),
(90602, '施工现场安全检查方案', 'SAFETY', '[{"item":"临边防护","standard":"设置1.2m高防护栏杆","method":"目测+尺量"},{"item":"用电安全","standard":"三级配电两级保护","method":"检查配电箱"},{"item":"高处作业","standard":"系安全带+安全网","method":"现场观察"}]', 1, 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 1: 项目核心 ============

-- 项目
INSERT IGNORE INTO biz_project (id, project_code, project_name, project_nature, project_type, owner_company_id, owner_company_name, signing_company_id, signing_company_name, project_overview, project_address, contact_name, contact_phone, need_tender, status, budget_amount, contract_amount, cumulative_output, settlement_amount, total_income, total_expense, total_other_payment, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90001, 'PRJ20260101001', '滨江花园一期工程', '新建', '房建工程', 90501, '杭州滨江房地产开发有限公司', 90301, '中正建设集团有限公司', '滨江花园一期住宅项目，总建筑面积约12万㎡，含6栋高层住宅及地下车库', '杭州市滨江区西兴街道月明路88号', '李明', '13800001111', 0, 'CONSTRUCTION', 45000000.00, 50000000.00, 28000000.00, 0.00, 20000000.00, 15000000.00, 200000.00, 1, 1, NOW(), NOW(), 0, 0),
(90002, 'PRJ20250601001', '城南市政道路改造', '改建', '市政工程', 90502, '杭州市城南市政建设管理中心', 90302, '中正市政工程有限公司', '城南片区3条主干道改造，全长4.2公里，含路面翻新、雨污分流、绿化提升', '杭州市上城区望江路至复兴路段', '王芳', '13800002222', 1, 'COMPLETED', 30000000.00, 32000000.00, 33500000.00, 33500000.00, 31000000.00, 29500000.00, 150000.00, 1, 1, NOW(), NOW(), 0, 0),
(90003, 'PRJ20260301001', '高新区产业园二期', '新建', '工业厂房', 90503, '杭州高新产业园区发展有限公司', 90301, '中正建设集团有限公司', '产业园二期厂房及配套设施，总建筑面积约8万㎡', '杭州市余杭区文一西路998号', '吕经理', '13700003333', 1, 'FILED', 28000000.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 1, 1, NOW(), NOW(), 0, 0);

-- 项目成员（关联 Layer S 的业务用户，project_roles 为合法枚举值的 JSON 数组）
INSERT IGNORE INTO biz_project_member (id, project_id, user_id, user_name, project_roles, join_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90701, 90001, 90071, '张伟', '["PROJECT_MANAGER"]',            '2026-01-01', 1, 1, 1, NOW(), NOW(), 0, 0),
(90702, 90001, 90072, '李娜', '["QUALITY_OFFICER","CONSTRUCTOR"]', '2026-01-01', 1, 1, 1, NOW(), NOW(), 0, 0),
(90707, 90001, 90074, '刘敏', '["MATERIAL_OFFICER"]',            '2026-01-05', 1, 1, 1, NOW(), NOW(), 0, 0),
(90708, 90001, 90077, '孙丽', '["ARCHIVIST"]',                   '2026-01-05', 1, 1, 1, NOW(), NOW(), 0, 0),
(90703, 90002, 90075, '陈刚', '["PROJECT_MANAGER"]',            '2025-06-01', 1, 1, 1, NOW(), NOW(), 0, 0),
(90704, 90002, 90073, '王强', '["FINANCE_OFFICER"]',             '2025-06-01', 1, 1, 1, NOW(), NOW(), 0, 0),
(90705, 90003, 90071, '张伟', '["PROJECT_MANAGER"]',            '2026-03-01', 1, 1, 1, NOW(), NOW(), 0, 0),
(90706, 90003, 90072, '李娜', '["CONSTRUCTOR"]',                 '2026-03-01', 1, 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 2: 投标管理（项目C） ============

INSERT IGNORE INTO biz_tender_register (id, project_id, owner_company, bid_method, register_method, register_date, open_date, tender_method, deposit_amount, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90801, 90003, '杭州高新产业园区发展有限公司', '公开招标', '网上报名', '2026-02-15', '2026-03-10', '综合评估法', 500000.00, 'REGISTERED', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_tender_task (id, register_id, task_type, responsible_person, deadline, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90811, 90801, 'COMMERCIAL', '李明', '2026-03-05', 'COMPLETED', 1, 1, NOW(), NOW(), 0, 0),
(90812, 90801, 'TECHNICAL', '王芳', '2026-03-05', 'COMPLETED', 1, 1, NOW(), NOW(), 0, 0),
(90813, 90801, 'ECONOMIC', '赵强', '2026-03-08', 'PENDING', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_tender_fee (id, register_id, project_id, fee_type, fee_amount, payment_date, receipt_file, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90821, 90801, 90003, '标书费', 2000.00, '2026-02-16', NULL, 'PAID', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_deposit_apply (id, register_id, project_id, deposit_amount, payment_date, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90831, 90801, 90003, 500000.00, '2026-02-20', 'PAID', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 3: 合同体系 ============

-- 施工合同
INSERT IGNORE INTO biz_construction_contract (id, project_id, project_name, contract_code, contract_type, parent_contract_id, party_a_name, party_a_id, signing_date, start_date, end_date, contract_amount, tax_rate, amount_without_tax, tax_amount, cumulative_change_amount, cumulative_output, cumulative_invoice_amount, cumulative_received_amount, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91001, 90001, '滨江花园一期工程', 'HT20260101001', 'REGISTER', NULL, '杭州滨江房地产开发有限公司', 90501, '2026-01-05', '2026-01-15', '2027-12-31', 50000000.00, 9.00, 45871559.63, 4128440.37, 1500000.00, 28000000.00, 25000000.00, 20000000.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(91002, 90002, '城南市政道路改造', 'HT20250601001', 'REGISTER', NULL, '杭州市城南市政建设管理中心', 90502, '2025-06-10', '2025-06-20', '2026-05-31', 32000000.00, 9.00, 29357798.17, 2642201.83, 1500000.00, 33500000.00, 32000000.00, 31000000.00, 'SETTLED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(91003, 90001, '滨江花园一期工程', 'HT20260101001-B1', 'SUPPLEMENT', 91001, '杭州滨江房地产开发有限公司', 90501, '2026-04-01', '2026-04-01', '2027-12-31', 1500000.00, 9.00, 1376146.79, 123853.21, 0.00, 0.00, 0.00, 0.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 合同明细
INSERT IGNORE INTO biz_contract_detail (id, contract_id, contract_table, item_name, specification, unit, quantity, unit_price, total_price, remark, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91101, 91001, 'biz_construction_contract', '土建工程', '主体结构', '项', 1.0000, 28000000.0000, 28000000.00, '含基础+主体+屋面', 1, 1, 1, NOW(), NOW(), 0, 0),
(91102, 91001, 'biz_construction_contract', '安装工程', '水电暖通', '项', 1.0000, 12000000.0000, 12000000.00, '含给排水+电气+暖通', 2, 1, 1, NOW(), NOW(), 0, 0),
(91103, 91001, 'biz_construction_contract', '装饰工程', '精装修', '项', 1.0000, 10000000.0000, 10000000.00, '公共区域精装修', 3, 1, 1, NOW(), NOW(), 0, 0),
(91104, 91002, 'biz_construction_contract', '路面工程', '沥青混凝土路面', '项', 1.0000, 18000000.0000, 18000000.00, '含路基+路面', 1, 1, 1, NOW(), NOW(), 0, 0),
(91105, 91002, 'biz_construction_contract', '管网工程', '雨污分流', '项', 1.0000, 9000000.0000, 9000000.00, '含雨水+污水管道', 2, 1, 1, NOW(), NOW(), 0, 0),
(91106, 91002, 'biz_construction_contract', '绿化工程', '景观绿化', '项', 1.0000, 5000000.0000, 5000000.00, '含苗木+养护', 3, 1, 1, NOW(), NOW(), 0, 0);

-- 工程量清单（BOQ 树形）
INSERT IGNORE INTO biz_boq_item (id, tenant_id, contract_id, parent_id, item_code, item_name, unit, quantity, unit_price, total_price, completed_quantity, level, sort_order, created_by, created_at, updated_at, deleted, version) VALUES
(91201, 1, 91001, 0, '1', '土建工程', NULL, NULL, NULL, 28000000.00, NULL, 1, 1, 1, NOW(), NOW(), 0, 0),
(91202, 1, 91001, 91201, '1.1', '基础工程', NULL, NULL, NULL, 8000000.00, NULL, 2, 1, 1, NOW(), NOW(), 0, 0),
(91203, 1, 91001, 91201, '1.2', '主体结构', NULL, NULL, NULL, 15000000.00, NULL, 2, 2, 1, NOW(), NOW(), 0, 0),
(91204, 1, 91001, 91201, '1.3', '屋面工程', NULL, NULL, NULL, 5000000.00, NULL, 2, 3, 1, NOW(), NOW(), 0, 0),
(91205, 1, 91001, 91202, '1.1.1', '桩基工程', '根', 120.0000, 45000.0000, 5400000.00, 120.0000, 3, 1, 1, NOW(), NOW(), 0, 0),
(91206, 1, 91001, 91202, '1.1.2', '基坑支护', 'm²', 3200.0000, 812.5000, 2600000.00, 3200.0000, 3, 2, 1, NOW(), NOW(), 0, 0),
(91207, 1, 91001, 91203, '1.2.1', '混凝土工程', 'm³', 18000.0000, 550.0000, 9900000.00, 12000.0000, 3, 1, 1, NOW(), NOW(), 0, 0),
(91208, 1, 91001, 91203, '1.2.2', '钢筋工程', '吨', 2800.0000, 4200.0000, 11760000.00, 1800.0000, 3, 2, 1, NOW(), NOW(), 0, 0);

-- 变更签证
INSERT IGNORE INTO biz_change_visa (id, project_id, contract_id, change_type, change_reason, change_content, change_amount, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91301, 90001, 91001, 'DESIGN_CHANGE', '业主调整户型布局，3#楼增加一层', '3#楼由18层调整为19层，增加结构及装修工程量', 1200000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(91302, 90001, 91001, 'SITE_VISA', '地下车库发现软弱地基需处理', '地下车库B区增加换填处理，换填深度1.5m', 300000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 其他合同
INSERT IGNORE INTO biz_other_contract (id, project_id, contract_name, contract_category, party_a_name, party_b_name, contract_amount, tax_rate, amount_without_tax, tax_amount, signing_date, payment_terms, cooperation_content, cumulative_invoice, cumulative_received, cumulative_settlement, cumulative_paid, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91401, 90001, '临时设施租赁协议', 'OTHER_INCOME', '中正建设集团有限公司', '杭州滨江房地产开发有限公司', 500000.00, 9.00, 458715.60, 41284.40, '2026-01-10', '按季支付', '施工期间临时办公设施租赁', 200000.00, 200000.00, 200000.00, 0.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0),
(91402, 90001, '检测服务协议', 'OTHER_EXPENSE', '中正建设集团有限公司', '杭州正信检测有限公司', 300000.00, 6.00, 283018.87, 16981.13, '2026-02-01', '检测完成后30日内支付', '主体结构检测+桩基检测', 100000.00, 0.00, 100000.00, 100000.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0);

-- 采购合同
INSERT IGNORE INTO biz_purchase_contract (id, project_id, contract_code, contract_name, party_a_id, party_a_name, party_b_id, party_b_name, supplier_name, signing_date, budget_id, contract_amount, payment_terms, cumulative_inbound, cumulative_settlement, cumulative_paid, cumulative_invoice_received, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91501, 90001, 'CG20260201001', '水泥砂石采购合同', 90301, '中正建设集团有限公司', 90401, '杭州鑫达建材有限公司', '杭州鑫达建材有限公司', '2026-02-01', 92001, 8000000.00, '月结，次月15日前支付', 8000000.00, 7000000.00, 6000000.00, 7000000.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(91502, 90001, 'CG20260215001', '钢材采购合同', 90301, '中正建设集团有限公司', 90402, '浙江宏远钢铁贸易有限公司', '浙江宏远钢铁贸易有限公司', '2026-02-15', 92001, 7000000.00, '货到付款，7日内支付', 5000000.00, 5000000.00, 4000000.00, 5000000.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 采购合同明细
INSERT IGNORE INTO biz_purchase_contract_detail (id, contract_id, material_name, specification, unit, quantity, contract_quantity, contract_price, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91511, 91501, '硅酸盐水泥', 'P.O 42.5', '吨', 5000.0000, 5000.0000, 450.0000, 1, 1, 1, NOW(), NOW(), 0, 0),
(91512, 91501, '中砂', '细度模数2.3-3.0', '方', 8000.0000, 8000.0000, 120.0000, 2, 1, 1, NOW(), NOW(), 0, 0),
(91513, 91502, '螺纹钢', 'HRB400 Φ25', '吨', 1200.0000, 1200.0000, 3800.0000, 1, 1, 1, NOW(), NOW(), 0, 0),
(91514, 91502, '盘螺', 'HRB400 Φ8', '吨', 600.0000, 600.0000, 3900.0000, 2, 1, 1, NOW(), NOW(), 0, 0);

-- 劳务合同
INSERT IGNORE INTO biz_labor_contract (id, project_id, contract_code, contract_name, party_a_name, party_b_id, party_b_name, team_name, signing_date, start_date, end_date, budget_id, contract_amount, payment_terms, cumulative_output, cumulative_settlement, cumulative_paid, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91601, 90001, 'LW20260201001', '主体结构劳务分包合同', '中正建设集团有限公司', 90403, '杭州顺安劳务有限公司', '顺安结构劳务一队', '2026-02-01', '2026-02-15', '2026-12-31', 92001, 6000000.00, '月结，次月20日前支付', 3800000.00, 3500000.00, 3000000.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(91602, 90001, 'LW20260301001', '装饰装修劳务分包合同', '中正建设集团有限公司', 90403, '杭州顺安劳务有限公司', '顺安装饰劳务二队', '2026-03-01', '2026-03-15', '2027-03-31', 92001, 4000000.00, '月结，次月20日前支付', 1800000.00, 1500000.00, 1000000.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 机械合同
INSERT IGNORE INTO biz_machine_contract (id, project_id, contract_code, supplier_id, supplier_name, signing_date, budget_id, contract_amount, payment_terms, cumulative_settlement, cumulative_paid, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91701, 90001, 'JX20260201001', 90404, '浙江力源机械租赁有限公司', '2026-02-01', 92001, 5000000.00, '月结，次月10日前支付', 3000000.00, 2000000.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0);

-- 分包合同
INSERT IGNORE INTO biz_subcontract (id, project_id, contract_code, supplier_id, supplier_name, signing_date, budget_id, contract_amount, payment_terms, cumulative_settlement, cumulative_paid, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91801, 90001, 'FB20260301001', 90405, '杭州恒基装饰工程有限公司', '2026-03-01', 92001, 5000000.00, '按进度支付，月结', 3000000.00, 2000000.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0),
(91802, 90001, 'FB20260315001', 90406, '浙江中天幕墙工程有限公司', '2026-03-15', 92001, 3000000.00, '按进度支付，月结', 1000000.00, 1000000.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 4: 预算体系 ============

-- 预算主表
INSERT IGNORE INTO biz_budget (id, project_id, budget_type, change_seq, total_amount, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(92001, 90001, 'ORIGINAL', 0, 45000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(92002, 90001, 'CHANGE', 1, 46500000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 预算明细
INSERT IGNORE INTO biz_budget_detail (id, budget_id, cost_category, cost_subcategory, item_name, specification, unit, budget_quantity, budget_unit_price, budget_total_price, remark, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(92101, 92001, 'MATERIAL', '主材', '钢材', 'HRB400系列', '吨', 2800.0000, 4000.0000, 11200000.00, NULL, 1, 1, NOW(), NOW(), 0, 0),
(92102, 92001, 'MATERIAL', '辅材', '水泥砂石', '综合', '批', 1.0000, 6800000.0000, 6800000.00, NULL, 1, 1, NOW(), NOW(), 0, 0),
(92103, 92001, 'LABOR', '主体劳务', '结构劳务', '综合单价', '项', 1.0000, 8000000.0000, 8000000.00, NULL, 1, 1, NOW(), NOW(), 0, 0),
(92104, 92001, 'LABOR', '装饰劳务', '装修劳务', '综合单价', '项', 1.0000, 4000000.0000, 4000000.00, NULL, 1, 1, NOW(), NOW(), 0, 0),
(92105, 92001, 'MACHINE', '大型机械', '塔吊+挖掘机', '台班', '项', 1.0000, 4000000.0000, 4000000.00, NULL, 1, 1, NOW(), NOW(), 0, 0),
(92106, 92001, 'MACHINE', '小型机械', '振动棒等', '台班', '项', 1.0000, 2000000.0000, 2000000.00, NULL, 1, 1, NOW(), NOW(), 0, 0),
(92107, 92001, 'SUBCONTRACT', '装饰分包', '精装修分包', '综合', '项', 1.0000, 5000000.0000, 5000000.00, NULL, 1, 1, NOW(), NOW(), 0, 0),
(92108, 92001, 'SUBCONTRACT', '幕墙分包', '幕墙工程分包', '综合', '项', 1.0000, 2000000.0000, 2000000.00, NULL, 1, 1, NOW(), NOW(), 0, 0),
(92109, 92001, 'INDIRECT', '管理费', '项目管理费', '综合', '项', 1.0000, 1200000.0000, 1200000.00, NULL, 1, 1, NOW(), NOW(), 0, 0),
(92110, 92001, 'INDIRECT', '临设费', '临时设施费', '综合', '项', 1.0000, 800000.0000, 800000.00, NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 目标成本变更
INSERT IGNORE INTO biz_budget_change (id, tenant_id, project_id, budget_id, change_code, change_reason, total_adjust_amount, status, workflow_instance_id, created_by, created_at, updated_at, deleted, version) VALUES
(92201, 1, 90001, 92001, 'BG20260401001', '设计变更导致3#楼增加一层，需追加结构及装修预算', 1500000.00, 'APPROVED', NULL, 1, NOW(), NOW(), 0, 0);

-- 变更明细
INSERT IGNORE INTO biz_budget_change_detail (id, tenant_id, change_id, budget_detail_id, cost_category, cost_subcategory, item_name, original_amount, adjust_amount, adjusted_amount, remark, created_by, created_at, updated_at, deleted, version) VALUES
(92211, 1, 92201, 92101, 'MATERIAL', '主材', '钢材', 11200000.00, 800000.00, 12000000.00, '增加一层结构用钢', 1, NOW(), NOW(), 0, 0),
(92212, 1, 92201, 92103, 'LABOR', '主体劳务', '结构劳务', 8000000.00, 500000.00, 8500000.00, '增加一层劳务费用', 1, NOW(), NOW(), 0, 0),
(92213, 1, 92201, 92105, 'MACHINE', '大型机械', '塔吊+挖掘机', 4000000.00, 200000.00, 4200000.00, '延长机械使用时间', 1, NOW(), NOW(), 0, 0);

-- 费用子类
INSERT IGNORE INTO biz_cost_subcategory (id, cost_category, subcategory_name, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(92301, 'MATERIAL', '主材', 1, 1, 1, NOW(), NOW(), 0, 0),
(92302, 'MATERIAL', '辅材', 2, 1, 1, NOW(), NOW(), 0, 0),
(92303, 'MATERIAL', '周转材料', 3, 1, 1, NOW(), NOW(), 0, 0),
(92304, 'LABOR', '主体劳务', 1, 1, 1, NOW(), NOW(), 0, 0),
(92305, 'LABOR', '装饰劳务', 2, 1, 1, NOW(), NOW(), 0, 0),
(92306, 'MACHINE', '大型机械', 1, 1, 1, NOW(), NOW(), 0, 0),
(92307, 'MACHINE', '小型机械', 2, 1, 1, NOW(), NOW(), 0, 0),
(92308, 'SUBCONTRACT', '专业分包', 1, 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 5: 产值与结算 ============

-- 产值报告（项目A 三期）
INSERT IGNORE INTO biz_output_report (id, project_id, contract_id, report_period, current_output, cumulative_output, confirm_date, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(93001, 90001, 91001, '2026-03', 8000000.00, 8000000.00, '2026-04-05', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(93002, 90001, 91001, '2026-04', 10000000.00, 18000000.00, '2026-05-05', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(93003, 90001, 91001, '2026-05', 10000000.00, 28000000.00, '2026-06-05', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 工程量清单（产值关联）
INSERT IGNORE INTO biz_quantity_list (id, project_id, contract_id, item_name, specification, unit, quantity, unit_price, amount, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(93101, 90001, 91001, 'C30混凝土浇筑', '泵送', 'm³', 12000.0000, 550.0000, 6600000.00, 1, 1, NOW(), NOW(), 0, 0),
(93102, 90001, 91001, '钢筋制安', 'HRB400', '吨', 1800.0000, 4200.0000, 7560000.00, 1, 1, NOW(), NOW(), 0, 0),
(93103, 90001, 91001, '模板工程', '木模', 'm²', 45000.0000, 85.0000, 3825000.00, 1, 1, NOW(), NOW(), 0, 0),
(93104, 90001, 91001, '脚手架工程', '落地式', 'm²', 28000.0000, 35.0000, 980000.00, 1, 1, NOW(), NOW(), 0, 0);

-- 竣工结算（项目B）
INSERT IGNORE INTO biz_final_settlement (id, project_id, contract_id, settlement_amount, settlement_date, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(93201, 90002, 91002, 33500000.00, '2026-06-15', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 项目最终结算（项目B）
INSERT IGNORE INTO biz_project_settlement (id, tenant_id, project_id, settlement_code, construction_contract_amount, cumulative_output, cumulative_received, cumulative_invoiced, total_income, subcontract_settled, labor_settled, material_settled, machine_settled, other_expense, cumulative_paid, total_expenditure, profit, profit_rate, status, workflow_instance_id, created_by, created_at, updated_at, deleted, version) VALUES
(93301, 1, 90002, 'JS20260615001', 32000000.00, 33500000.00, 31000000.00, 32000000.00, 33500000.00, 5000000.00, 8000000.00, 12000000.00, 3500000.00, 1000000.00, 29500000.00, 29500000.00, 4000000.00, 11.94, 'APPROVED', NULL, 1, NOW(), NOW(), 0, 0);

-- 结算关联合同明细
INSERT IGNORE INTO biz_settlement_contract_detail (id, tenant_id, settlement_id, contract_type, contract_id, contract_code, contract_name, contract_amount, settled_amount, paid_amount, unsettled_amount, settlement_status, created_by, created_at, updated_at, deleted, version) VALUES
(93311, 1, 93301, 'MATERIAL', 91501, 'CG20260201001', '水泥砂石采购', 12000000.00, 12000000.00, 12000000.00, 0.00, 'SETTLED', 1, NOW(), NOW(), 0, 0),
(93312, 1, 93301, 'LABOR', 91601, 'LW20250601001', '道路施工劳务', 8000000.00, 8000000.00, 8000000.00, 0.00, 'SETTLED', 1, NOW(), NOW(), 0, 0),
(93313, 1, 93301, 'SUBCONTRACT', 91801, 'FB20250701001', '绿化分包', 5000000.00, 5000000.00, 5000000.00, 0.00, 'SETTLED', 1, NOW(), NOW(), 0, 0);

-- ============ Layer 6: 材料管理（项目A） ============

-- 材料入库单
INSERT IGNORE INTO biz_material_inbound (id, project_id, contract_id, inbound_code, inbound_date, total_amount, direct_outbound, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(94001, 90001, 91501, 'RK20260301001', '2026-03-01', 4500000.00, 0, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(94002, 90001, 91502, 'RK20260315001', '2026-03-15', 3500000.00, 0, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 入库明细
INSERT IGNORE INTO biz_material_inbound_detail (id, inbound_id, material_name, specification, unit, unit_price, quantity, total_price, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(94011, 94001, '硅酸盐水泥', 'P.O 42.5', '吨', 450.0000, 5000.0000, 2250000.00, 1, 1, NOW(), NOW(), 0, 0),
(94012, 94001, '中砂', '细度模数2.3-3.0', '方', 120.0000, 8000.0000, 960000.00, 1, 1, NOW(), NOW(), 0, 0),
(94013, 94001, '碎石', '5-25mm', '方', 110.0000, 5000.0000, 550000.00, 1, 1, NOW(), NOW(), 0, 0),
(94014, 94001, '水泥砖', 'MU10 240×115×53', '万块', 3700.0000, 200.0000, 740000.00, 1, 1, NOW(), NOW(), 0, 0),
(94015, 94002, '螺纹钢', 'HRB400 Φ25', '吨', 3800.0000, 600.0000, 2280000.00, 1, 1, NOW(), NOW(), 0, 0),
(94016, 94002, '盘螺', 'HRB400 Φ8', '吨', 3900.0000, 300.0000, 1170000.00, 1, 1, NOW(), NOW(), 0, 0);

-- 材料出库单
INSERT IGNORE INTO biz_material_outbound (id, project_id, outbound_type, outbound_date, operator_name, contract_id, return_type, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(94101, 90001, 'PICK', '2026-03-10', '李明', NULL, NULL, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 出库明细
INSERT IGNORE INTO biz_material_outbound_detail (id, outbound_id, material_name, specification, unit, quantity, unit_price, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(94111, 94101, '硅酸盐水泥', 'P.O 42.5', '吨', 2000.0000, 450.0000, 1, 1, NOW(), NOW(), 0, 0),
(94112, 94101, '螺纹钢', 'HRB400 Φ25', '吨', 400.0000, 3800.0000, 1, 1, NOW(), NOW(), 0, 0);

-- 项目材料库存
INSERT IGNORE INTO biz_project_material_stock (id, project_id, material_name, specification, unit, stock_quantity, avg_unit_price, total_inbound, total_outbound, total_return, total_transfer_in, total_transfer_out, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(94201, 90001, '硅酸盐水泥', 'P.O 42.5', '吨', 3000.0000, 450.0000, 5000.0000, 2000.0000, 0.0000, 0.0000, 0.0000, 1, 1, NOW(), NOW(), 0, 0),
(94202, 90001, '中砂', '细度模数2.3-3.0', '方', 6500.0000, 120.0000, 8000.0000, 1500.0000, 0.0000, 0.0000, 0.0000, 1, 1, NOW(), NOW(), 0, 0),
(94203, 90001, '螺纹钢', 'HRB400 Φ25', '吨', 200.0000, 3800.0000, 600.0000, 400.0000, 0.0000, 0.0000, 0.0000, 1, 1, NOW(), NOW(), 0, 0),
(94204, 90001, '盘螺', 'HRB400 Φ8', '吨', 300.0000, 3900.0000, 300.0000, 0.0000, 0.0000, 0.0000, 0.0000, 1, 1, NOW(), NOW(), 0, 0);

-- 材料盘点单
INSERT IGNORE INTO biz_material_inventory (id, project_id, inventory_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(94301, 90001, '2026-06-30', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 盘点明细
INSERT IGNORE INTO biz_material_inventory_detail (id, tenant_id, inventory_id, stock_id, material_name, specification, unit, book_quantity, actual_quantity, diff_quantity, created_by, created_at, updated_at, deleted, version) VALUES
(94311, 1, 94301, 94201, '硅酸盐水泥', 'P.O 42.5', '吨', 3000.0000, 2980.0000, -20.0000, 1, NOW(), NOW(), 0, 0),
(94312, 1, 94301, 94202, '中砂', '细度模数2.3-3.0', '方', 6500.0000, 6550.0000, 50.0000, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 7: 机械管理（项目A） ============

-- 机械台账
INSERT IGNORE INTO biz_machine_ledger (id, machine_name, machine_code, machine_type, model, purchase_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(95001, '履带式挖掘机', 'JX-001', '土方机械', 'CAT320D', '2024-06-15', 'IN_FIELD', 1, 1, NOW(), NOW(), 0, 0),
(95002, '塔式起重机', 'JX-002', '起重机械', 'QTZ63', '2023-03-20', 'IN_FIELD', 1, 1, NOW(), NOW(), 0, 0);

-- 机械进退场
INSERT IGNORE INTO biz_machine_entry (id, machine_id, project_id, entry_date, entry_type, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(95011, 95001, 90001, '2026-01-20', 'IN', 1, 1, NOW(), NOW(), 0, 0),
(95012, 95002, 90001, '2026-02-01', 'IN', 1, 1, NOW(), NOW(), 0, 0);

-- 机械工作日志
INSERT IGNORE INTO biz_machine_work_log (id, machine_id, project_id, work_date, shift_count, work_quantity, oil_consumption, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(95021, 95001, 90001, '2026-07-15', 1.00, 850.00, 180.00, 'DRAFT', 1, 1, NOW(), NOW(), 0, 0),
(95022, 95001, 90001, '2026-07-16', 1.00, 920.00, 195.00, 'DRAFT', 1, 1, NOW(), NOW(), 0, 0),
(95023, 95002, 90001, '2026-07-15', 2.00, 45.00, 0.00, 'SETTLED', 1, 1, NOW(), NOW(), 0, 0);

-- 机械加油记录
INSERT IGNORE INTO biz_machine_oil_record (id, machine_id, project_id, oil_date, oil_quantity, oil_amount, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(95031, 95001, 90001, '2026-07-15', 180.00, 1440.00, 1, 1, NOW(), NOW(), 0, 0),
(95032, 95001, 90001, '2026-07-16', 195.00, 1560.00, 1, 1, NOW(), NOW(), 0, 0);

-- 机械维修记录
INSERT IGNORE INTO biz_machine_repair (id, machine_id, project_id, fault_description, report_date, repair_person, repair_date, repair_cost, repair_status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(95041, 95001, 90001, '液压系统压力不足，疑似密封圈老化', '2026-07-10', '张师傅', '2026-07-11', 3500.00, 'COMPLETED', 1, 1, NOW(), NOW(), 0, 0);

-- 机械结算
INSERT IGNORE INTO biz_machine_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(95051, 90001, 91701, 3000000.00, 3000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 8: 劳务管理（项目A） ============

-- 班组
INSERT IGNORE INTO biz_team (id, team_name, project_id, leader_name, leader_phone, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(96001, '钢筋班组', 90001, '王大力', '15800001111', 1, 1, 1, NOW(), NOW(), 0, 0),
(96002, '混凝土班组', 90001, '李建国', '15800002222', 1, 1, 1, NOW(), NOW(), 0, 0);

-- 劳务花名册
INSERT IGNORE INTO biz_labor_roster (id, project_id, team_id, worker_name, id_card, phone, worker_type, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(96011, 90001, 96001, '王大力', '330102198501011234', '15800001111', 'FIXED', 1, 1, 1, NOW(), NOW(), 0, 0),
(96012, 90001, 96001, '张铁柱', '330102199002022345', '15800003333', 'FIXED', 1, 1, 1, NOW(), NOW(), 0, 0),
(96013, 90001, 96001, '刘小明', '330102199503033456', '15800004444', 'TEMPORARY', 1, 1, 1, NOW(), NOW(), 0, 0),
(96014, 90001, 96002, '李建国', '330102198801044567', '15800002222', 'FIXED', 1, 1, 1, NOW(), NOW(), 0, 0),
(96015, 90001, 96002, '赵国庆', '330102199201055678', '15800005555', 'FIXED', 1, 1, 1, NOW(), NOW(), 0, 0),
(96016, 90001, 96002, '陈志强', '330102199601066789', '15800006666', 'TEMPORARY', 1, 1, 1, NOW(), NOW(), 0, 0);

-- 派工单
INSERT IGNORE INTO biz_work_order (id, project_id, team_id, worker_id, worker_name, work_date, hours, hourly_rate, overtime, overtime_rate, total_amount, order_type, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(96021, 90001, 96001, 96011, '王大力', '2026-07-15', 8.00, 45.00, 2.00, 67.50, 495.00, 'FIXED', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(96022, 90001, 96001, 96012, '张铁柱', '2026-07-15', 8.00, 40.00, 0.00, 0.00, 320.00, 'FIXED', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(96023, 90001, 96002, 96014, '李建国', '2026-07-15', 8.00, 45.00, 1.50, 67.50, 461.25, 'FIXED', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(96024, 90001, 96002, 96016, '陈志强', '2026-07-15', 8.00, 35.00, 0.00, 0.00, 280.00, 'TEMPORARY', 'DRAFT', 1, 1, NOW(), NOW(), 0, 0);

-- 劳务工资单
INSERT IGNORE INTO biz_labor_payroll (id, project_id, team_id, period_start, period_end, total_settlement, total_paid, unpaid, order_type, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(96031, 90001, 96001, '2026-06-01', '2026-06-30', 85000.00, 70000.00, 15000.00, 'FIXED', 'SETTLED', 1, 1, NOW(), NOW(), 0, 0);

-- 劳务产值报告
INSERT IGNORE INTO biz_labor_output_report (id, project_id, contract_id, current_output, cumulative_output, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(96041, 90001, 91601, 2500000.00, 2500000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(96042, 90001, 91601, 1500000.00, 4000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 劳务结算
INSERT IGNORE INTO biz_labor_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(96051, 90001, 91601, 3500000.00, 3500000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 劳务奖罚
INSERT IGNORE INTO biz_labor_reward_punish (id, project_id, contract_id, rp_type, amount, reason, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(96061, 90001, 91601, 'REWARD', 5000.00, '6月份主体结构施工质量优秀，一次验收合格率100%', 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 9: 分包管理（项目A） ============

-- 分包产值报告
INSERT IGNORE INTO biz_subcontract_output_report (id, project_id, contract_id, current_output, cumulative_output, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(97001, 90001, 91801, 2000000.00, 2000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(97002, 90001, 91801, 1500000.00, 3500000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 分包结算
INSERT IGNORE INTO biz_subcontract_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(97011, 90001, 91801, 3000000.00, 3000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 分包奖罚
INSERT IGNORE INTO biz_subcontract_reward_punish (id, project_id, contract_id, rp_type, amount, reason, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(97021, 90001, 91801, 'PUNISH', 20000.00, '幕墙安装进度滞后5天，未按计划完成节点', 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 10: 现场管理（项目A） ============

-- 进度计划（树形：主体+子任务）
INSERT IGNORE INTO biz_schedule_plan (id, project_id, task_name, parent_id, plan_start_date, plan_end_date, actual_start_date, actual_end_date, progress, task_status, task_detail, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(98001, 90001, '主体结构施工', 0, '2026-01-15', '2026-12-31', '2026-01-15', NULL, 55.00, 'IN_PROGRESS', '6栋高层住宅主体结构施工', 1, 1, 1, NOW(), NOW(), 0, 0),
(98002, 90001, '1#楼主体结构', 98001, '2026-01-15', '2026-06-30', '2026-01-15', '2026-06-25', 100.00, 'COMPLETED', '1#楼18层主体结构封顶', 1, 1, 1, NOW(), NOW(), 0, 0),
(98003, 90001, '2#楼主体结构', 98001, '2026-02-01', '2026-07-31', '2026-02-01', NULL, 75.00, 'IN_PROGRESS', '2#楼18层主体结构施工', 2, 1, 1, NOW(), NOW(), 0, 0),
(98004, 90001, '地下车库', 0, '2026-01-15', '2026-09-30', '2026-01-15', NULL, 60.00, 'IN_PROGRESS', '地下车库结构及防水施工', 2, 1, 1, NOW(), NOW(), 0, 0),
(98005, 90001, '二次结构及砌体', 0, '2026-08-01', '2027-03-31', NULL, NULL, 0.00, 'NOT_STARTED', '填充墙砌筑及构造柱施工', 3, 1, 1, NOW(), NOW(), 0, 0);

-- 进度反馈
INSERT IGNORE INTO biz_schedule_feedback (id, project_id, plan_id, actual_start_date, actual_end_date, task_status, progress, remark, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(98011, 90001, 98002, '2026-01-15', '2026-06-25', 'COMPLETED', 100.00, '1#楼主体结构提前5天封顶', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(98012, 90001, 98003, '2026-02-01', NULL, 'IN_PROGRESS', 75.00, '2#楼14层顶板浇筑完成', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 施工日志（近3天）
INSERT IGNORE INTO biz_construction_log (id, project_id, log_date, weather, temperature, wind, worker_count, production_record, technical_record, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(98021, 90001, '2026-07-01', '晴', '28-35℃', '东南风3级', 186, '2#楼15层墙柱钢筋绑扎完成80%，地下车库B区顶板模板安装', '钢筋连接采用机械连接，接头错开35d', 1, 1, NOW(), NOW(), 0, 0),
(98022, 90001, '2026-07-02', '多云', '27-34℃', '东风2级', 192, '2#楼15层墙柱模板安装，地下车库B区顶板钢筋绑扎', '模板拼缝严密，支撑间距600mm', 1, 1, NOW(), NOW(), 0, 0),
(98023, 90001, '2026-07-03', '阵雨', '26-32℃', '南风4级', 145, '因降雨暂停室外作业，进行室内管线预埋及材料整理', '做好基坑排水及已浇筑混凝土养护', 1, 1, NOW(), NOW(), 0, 0);

-- 质量安全检查
INSERT IGNORE INTO biz_inspection (id, project_id, inspection_type, scheme_id, inspection_content, has_problem, problem_description, responsible_person_id, rectification_deadline, rectification_date, rectification_status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(98031, 90001, 'QUALITY', 90601, '2#楼15层主体结构质量检查', 1, '局部钢筋间距偏差超过15mm，保护层垫块缺失', 1, '2026-07-05', '2026-07-04', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(98032, 90001, 'SAFETY', 90602, '施工现场安全巡查', 0, NULL, NULL, NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 检查明细
INSERT IGNORE INTO biz_inspection_detail (id, tenant_id, inspection_id, item_name, check_standard, check_method, check_result, remark, sort_order, created_by, created_at, updated_at, deleted, version) VALUES
(98041, 1, 98031, '混凝土强度', 'C30及以上', '回弹法', 'PASS', '回弹值满足设计要求', 1, 1, NOW(), NOW(), 0, 0),
(98042, 1, 98031, '钢筋间距', '偏差±10mm', '尺量', 'FAIL', '局部偏差15mm，需调整', 2, 1, NOW(), NOW(), 0, 0),
(98043, 1, 98032, '临边防护', '设置1.2m高防护栏杆', '目测+尺量', 'PASS', NULL, 1, 1, NOW(), NOW(), 0, 0),
(98044, 1, 98032, '用电安全', '三级配电两级保护', '检查配电箱', 'PASS', NULL, 2, 1, NOW(), NOW(), 0, 0);

-- 整改记录
INSERT IGNORE INTO biz_rectification (id, inspection_id, project_id, rectification_content, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(98051, 98031, 90001, '已对偏差钢筋进行调直处理，补充保护层垫块，复检合格', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 竣工验收（项目B）
INSERT IGNORE INTO biz_completion_acceptance (id, project_id, acceptance_date, acceptance_report, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(98061, 90002, '2026-05-20', '城南市政道路改造工程竣工验收报告：路面平整度、压实度、雨污管道闭水试验均合格，绿化成活率95%以上', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 11: 财务管理 ============

-- 开票申请（项目A，累计2500万）
INSERT IGNORE INTO biz_invoice_apply (id, project_id, contract_id, invoice_type, invoice_amount, invoice_title, taxpayer_id, bank_account, bank_name, contract_amount_snapshot, settlement_amount_snapshot, historical_invoiced_snapshot, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99001, 90001, 91001, 'SPECIAL', 15000000.00, '杭州滨江房地产开发有限公司', '91330100MAAAAAAA1', '3301009001001001', '中国工商银行杭州分行', 50000000.00, NULL, 0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99002, 90001, 91001, 'SPECIAL', 10000000.00, '杭州滨江房地产开发有限公司', '91330100MAAAAAAA1', '3301009001001001', '中国工商银行杭州分行', 50000000.00, NULL, 15000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 收款登记（项目A累计2000万 + 项目B累计3100万）
INSERT IGNORE INTO biz_payment_received (id, project_id, contract_id, receive_date, receive_amount, receiver, receive_type, receive_bank_account, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99011, 90001, 91001, '2026-02-15', 10000000.00, '中正建设集团有限公司', '银行转账', '3301001001001001', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(99012, 90001, 91001, '2026-04-20', 6000000.00, '中正建设集团有限公司', '银行转账', '3301001001001001', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(99013, 90001, 91001, '2026-06-15', 4000000.00, '中正建设集团有限公司', '银行转账', '3301001001001001', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 收票登记（项目A收到供应商发票）
INSERT IGNORE INTO biz_invoice_received (id, project_id, contract_id, contract_category, supplier_id, supplier_name, invoice_amount, invoice_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99021, 90001, 91501, 'PURCHASE', 90401, '杭州鑫达建材有限公司', 5000000.00, '2026-03-10', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(99022, 90001, 91601, 'LABOR', 90403, '杭州顺安劳务有限公司', 3000000.00, '2026-05-15', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 付款申请（材料600万+劳务400万+分包300万=1300万）
INSERT IGNORE INTO biz_payment_apply (id, project_id, contract_id, contract_category, supplier_id, supplier_name, payment_amount, payment_date, cumulative_settlement_snapshot, unpaid_amount_snapshot, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99031, 90001, 91501, 'PURCHASE', 90401, '杭州鑫达建材有限公司', 6000000.00, '2026-04-01', 8000000.00, 2000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99032, 90001, 91601, 'LABOR', 90403, '杭州顺安劳务有限公司', 4000000.00, '2026-05-20', 5000000.00, 1000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99033, 90001, 91801, 'SUBCONTRACT', 90405, '杭州恒基装饰工程有限公司', 3000000.00, '2026-06-10', 3000000.00, 0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 其他支付
INSERT IGNORE INTO biz_other_payment (id, project_id, payer_name, payment_date, payment_amount, remark, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99041, 90001, '中正建设集团有限公司', '2026-03-15', 200000.00, '施工临时用电报装费及围挡搭建费', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 项目报销
INSERT IGNORE INTO biz_project_reimbursement (id, project_id, total_amount, reimbursement_date, offset_reserve, reserve_apply_id, offset_amount, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99051, 90001, 35600.00, '2026-06-20', 1, 99071, 20000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 报销明细
INSERT IGNORE INTO biz_reimbursement_detail (id, reimbursement_id, expense_type, amount, remark, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99061, 99051, '差旅费', 15600.00, '项目部人员出差杭州-上海往返交通及住宿', 1, 1, NOW(), NOW(), 0, 0),
(99062, 99051, '招待费', 20000.00, '甲方及监理工作协调会议用餐', 1, 1, NOW(), NOW(), 0, 0);

-- 备用金申请
INSERT IGNORE INTO biz_reserve_fund_apply (id, project_id, applicant, apply_date, apply_amount, returned_amount, offset_amount, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99071, 90001, '李明', '2026-05-01', 50000.00, 10000.00, 20000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 银行账户
INSERT IGNORE INTO biz_bank_account (id, account_name, bank_name, bank_account, account_type, project_id, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99081, '中正建设集团有限公司', '中国工商银行杭州分行', '3301001001001001', 'BASIC', NULL, 1, 1, 1, NOW(), NOW(), 0, 0),
(99082, '中正建设集团有限公司滨江项目部', '中国建设银行杭州滨江支行', '3301002002002002', 'SPECIAL', 90001, 1, 1, 1, NOW(), NOW(), 0, 0);

-- 质保金（项目B，5%=167.5万）
INSERT IGNORE INTO biz_retention_money (id, project_id, contract_id, retention_rate, retention_amount, retention_period, start_date, expire_date, returned_amount, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99091, 90002, 91002, 5.0000, 1675000.00, 24, '2026-05-20', '2028-05-20', 0.00, 'ACTIVE', 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 12: 询价比价 ============

-- 询价单
INSERT IGNORE INTO biz_inquiry (id, title, invite_mode, bid_mode, status, publish_time, deadline, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99101, '滨江花园一期钢材采购询价', 'DIRECTED', 'LOWEST', 'AWARDED', '2026-02-01 09:00:00', '2026-02-10 17:00:00', 1, 1, NOW(), NOW(), 0, 0);

-- 询价供应商（邀请3家）
INSERT IGNORE INTO biz_inquiry_supplier (id, inquiry_id, supplier_id, supplier_name, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99111, 99101, 90401, '杭州鑫达建材有限公司', 1, 1, NOW(), NOW(), 0, 0),
(99112, 99101, 90402, '浙江宏远钢铁贸易有限公司', 1, 1, NOW(), NOW(), 0, 0),
(99113, 99101, 90404, '浙江力源机械租赁有限公司', 1, 1, NOW(), NOW(), 0, 0);

-- 询价物料
INSERT IGNORE INTO biz_inquiry_item (id, inquiry_id, material_name, specification, unit, quantity, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99121, 99101, '螺纹钢', 'HRB400 Φ25', '吨', 500.0000, 1, 1, NOW(), NOW(), 0, 0),
(99122, 99101, '盘螺', 'HRB400 Φ8', '吨', 200.0000, 1, 1, NOW(), NOW(), 0, 0),
(99123, 99101, 'H型钢', 'Q235B 200×200', '吨', 100.0000, 1, 1, NOW(), NOW(), 0, 0);

-- 报价单（2家报价）
INSERT IGNORE INTO biz_quotation (id, inquiry_id, supplier_id, supplier_name, total_amount, submit_time, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99131, 99101, 90401, '杭州鑫达建材有限公司', 3250000.00, '2026-02-08 14:30:00', 'SUBMITTED', 1, 1, NOW(), NOW(), 0, 0),
(99132, 99101, 90402, '浙江宏远钢铁贸易有限公司', 3380000.00, '2026-02-09 10:15:00', 'SUBMITTED', 1, 1, NOW(), NOW(), 0, 0);

-- 报价明细
INSERT IGNORE INTO biz_quotation_detail (id, quotation_id, inquiry_item_id, unit_price, total_price, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99141, 99131, 99121, 4200.0000, 2100000.00, 1, 1, NOW(), NOW(), 0, 0),
(99142, 99131, 99122, 4100.0000, 820000.00, 1, 1, NOW(), NOW(), 0, 0),
(99143, 99132, 99121, 4350.0000, 2175000.00, 1, 1, NOW(), NOW(), 0, 0),
(99144, 99132, 99122, 4250.0000, 850000.00, 1, 1, NOW(), NOW(), 0, 0);

-- 定标结果
INSERT IGNORE INTO biz_bid_result (id, inquiry_id, supplier_id, supplier_name, ranking, total_amount, is_winner, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99151, 99101, 90401, '杭州鑫达建材有限公司', 1, 3250000.00, 1, 1, 1, NOW(), NOW(), 0, 0),
(99152, 99101, 90402, '浙江宏远钢铁贸易有限公司', 2, 3380000.00, 0, 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 13: 消息与流程 ============

-- 公告
INSERT IGNORE INTO msg_announcement (id, title, content, publish_scope, scope_ids, is_top, effective_start, effective_end, status, publish_time, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99201, '关于加强夏季施工安全管理的通知', '各项目部：近期气温持续升高，请各项目部做好防暑降温工作，调整作息时间，确保施工人员安全。', 'ALL', NULL, 1, '2026-07-01', '2026-09-30', 'PUBLISHED', '2026-07-01 08:00:00', 1, 1, NOW(), NOW(), 0, 0),
(99202, '2026年度安全生产月活动安排（草稿）', '待完善', 'ALL', NULL, 0, NULL, NULL, 'DRAFT', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 站内消息
INSERT IGNORE INTO msg_message (id, user_id, title, content, message_type, business_type, business_id, is_read, read_time, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99211, 1, '付款申请待审批', '滨江花园一期工程材料采购付款申请（600万元）待您审批', 'APPROVAL', 'PAYMENT_APPLY', 99031, 1, '2026-04-01 10:00:00', 1, 1, NOW(), NOW(), 0, 0),
(99212, 1, '预算预警通知', '滨江花园一期工程材料成本已达预算80%，请关注成本控制', 'WARNING', 'BUDGET_WARNING', 90001, 0, NULL, 1, 1, NOW(), NOW(), 0, 0),
(99213, 1, '系统维护通知', '系统将于本周六凌昨2:00-4:00进行例行维护，届时服务将短暂中断', 'SYSTEM', NULL, NULL, 1, '2026-06-28 09:00:00', 1, 1, NOW(), NOW(), 0, 0);

-- 业务类型（流程引擎）
INSERT IGNORE INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99221, '付款申请', 'PAYMENT_APPLY', 0, 1, 1, 1, NOW(), NOW(), 0, 0),
(99222, '开票申请', 'INVOICE_APPLY', 0, 2, 1, 1, NOW(), NOW(), 0, 0),
(99223, '劳务结算', 'LABOR_SETTLEMENT', 0, 3, 1, 1, NOW(), NOW(), 0, 0),
(99224, '分包结算', 'SUBCONTRACT_SETTLEMENT', 0, 4, 1, 1, NOW(), NOW(), 0, 0),
(99225, '项目报销', 'PROJECT_REIMBURSEMENT', 0, 5, 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 14: 供应商评价 ============

-- 供应商评价
INSERT IGNORE INTO biz_supplier_evaluation (id, supplier_id, supplier_name, evaluation_date, quality_score, timeliness_score, price_score, service_score, cooperation_score, total_score, remark, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99301, 90401, '杭州鑫达建材有限公司', '2026-06-30', 5, 4, 4, 5, 5, 4.60, '材料质量稳定，供货及时，配合度高', 1, 1, NOW(), NOW(), 0, 0),
(99302, 90403, '杭州顺安劳务有限公司', '2026-06-30', 4, 4, 3, 4, 4, 3.80, '劳务人员技能较好，价格略有上涨', 1, 1, NOW(), NOW(), 0, 0);

-- 供应商黑名单
INSERT IGNORE INTO biz_supplier_blacklist (id, supplier_id, supplier_name, reason, blacklist_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99311, 90406, '浙江中天幕墙工程有限公司', '分包工程进度严重滞后，多次警告未改善，影响整体工期', '2026-06-15', 1, 1, 1, NOW(), NOW(), 0, 0);

-- ============ Layer 15: 扩展业务模块（HR/行政/材料机械财务扩展/投标分包/档案门户） ============

-- —— 人力资源（HR）——

-- 人员证书
INSERT IGNORE INTO biz_person_certificate (id, person_name, certificate_type, certificate_no, issue_date, expire_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90911, '张伟', '一级建造师', 'JZS-330100-2022-0001', '2022-03-15', '2028-03-14', 1, 1, 1, NOW(), NOW(), 0, 0),
(90912, '李娜', '注册安全工程师', 'AQGCS-330100-2023-0021', '2023-06-20', '2026-09-30', 1, 1, 1, NOW(), NOW(), 0, 0);

-- 企业证书
INSERT IGNORE INTO biz_company_certificate (id, certificate_name, certificate_type, certificate_no, issue_date, expire_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90921, '建筑工程施工总承包一级', 'QUALIFICATION', 'D233012345', '2021-01-10', '2027-01-09', 1, 1, 1, NOW(), NOW(), 0, 0),
(90922, '安全生产许可证', 'SAFETY_LICENSE', '(浙)JZ安许证字[2024]001', '2024-02-01', '2027-01-31', 1, 1, 1, NOW(), NOW(), 0, 0),
(90923, '营业执照', 'BUSINESS_LICENSE', '91330100MA1234567X', '2015-05-06', '2035-05-05', 1, 1, 1, NOW(), NOW(), 0, 0);

-- 入职申请
INSERT IGNORE INTO biz_entry_apply (id, username, real_name, gender, birth_date, id_card, phone, entry_date, org_id, post_id, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90931, 'zhoujie',  '周杰', '男', '1995-08-12', '330102199508123456', '13811110011', '2026-07-01', 90026, 90045, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(90932, 'wuting',   '吴婷', '女', '1998-11-03', '330102199811034567', '13811110012', '2026-07-10', 90023, 90043, 'DRAFT',    NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 转正申请
INSERT IGNORE INTO biz_regular_apply (id, user_id, user_name, trial_end_date, regular_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90941, 90076, '赵磊', '2026-06-30', '2026-07-01', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 调动申请
INSERT IGNORE INTO biz_transfer_apply (id, user_id, user_name, transfer_date, from_org_id, from_post_id, to_org_id, to_post_id, remark, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90951, 90074, '刘敏', '2026-07-05', 90024, 90044, 90026, 90044, '因滨江花园项目需要，调派至项目部', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 离职申请
INSERT IGNORE INTO biz_resign_apply (id, user_id, user_name, resign_date, handover_person, is_handover, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90961, 90072, '李娜', '2026-08-31', '张伟', 0, 'DRAFT', 1, 1, NOW(), NOW(), 0, 0);

-- —— 行政后勤 ——

-- 用印申请
INSERT IGNORE INTO biz_seal_apply (id, applicant, seal_type, is_carry_out, use_time, reason, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90971, '王强', '公章',   0, '2026-06-15 10:00:00', '滨江花园一期工程施工合同用印', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(90972, '赵磊', '合同专用章', 1, '2026-06-20 14:30:00', '外出与业主签订补充协议，需外带用印', 'DRAFT', 1, 1, NOW(), NOW(), 0, 0);

-- 现场签到
INSERT IGNORE INTO biz_sign_record (id, user_id, project_id, sign_time, latitude, longitude, address, is_in_range, tenant_id, created_at) VALUES
(90981, 90071, 90001, '2026-07-15 08:02:11', 30.2084000, 120.2100000, '杭州市滨江区西兴街道月明路88号', 1, 1, NOW()),
(90982, 90072, 90001, '2026-07-15 08:05:43', 30.2085000, 120.2101000, '杭州市滨江区西兴街道月明路88号', 1, 1, NOW()),
(90983, 90074, 90001, '2026-07-16 08:11:20', 30.2200000, 120.2500000, '杭州市滨江区(打卡点外500米)', 0, 1, NOW());

-- 办公用品
INSERT IGNORE INTO biz_office_supply (id, category_name, supply_name, specification, unit, image_url, stock_quantity, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91911, '纸张耗材', 'A4打印纸', '70g 500张/包', '包', NULL, 120, 1, 1, 1, NOW(), NOW(), 0, 0),
(91912, '书写工具', '中性笔',   '0.5mm 黑色',   '支', NULL, 300, 1, 1, 1, NOW(), NOW(), 0, 0),
(91913, '文件管理', '档案盒',   'A4 加厚',      '个', NULL, 80,  1, 1, 1, NOW(), NOW(), 0, 0),
(91914, '劳保用品', '安全帽',   'ABS 红色',     '顶', NULL, 50,  1, 1, 1, NOW(), NOW(), 0, 0);

-- 办公用品出入库
INSERT IGNORE INTO biz_office_supply_in_out (id, supply_id, io_type, quantity, remark, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91921, 91911, 'IN',  200, '年中集中采购入库', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(91922, 91911, 'OUT', 80,  '项目部领用', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(91923, 91914, 'OUT', 30,  '新入场工人配发安全帽', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 车辆
INSERT IGNORE INTO biz_vehicle (id, plate_number, vehicle_type, vehicle_status, image_url, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91931, '浙A88888', '商务车', 'IDLE',   NULL, 1, 1, 1, NOW(), NOW(), 0, 0),
(91932, '浙A66666', '皮卡',   'IN_USE', NULL, 1, 1, 1, NOW(), NOW(), 0, 0);

-- 车辆申请
INSERT IGNORE INTO biz_vehicle_apply (id, vehicle_id, plate_number, use_time, purpose, expected_return_time, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91941, 91931, '浙A88888', '2026-07-18 08:00:00', '接送业主考察滨江花园工地', '2026-07-18 18:00:00', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(91942, 91932, '浙A66666', '2026-07-20 07:30:00', '运送小型材料至城南项目部', '2026-07-20 12:00:00', 'DRAFT', 1, 1, NOW(), NOW(), 0, 0);

-- 车辆维保
INSERT IGNORE INTO biz_vehicle_maintenance (id, vehicle_id, plate_number, maint_type, maint_date, maint_cost, content, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(91951, 91931, '浙A88888', 'REPAIR',    '2026-05-10', 1200.00, '常规保养：更换机油机滤、四轮定位', 1, 1, NOW(), NOW(), 0, 0),
(91952, 91932, '浙A66666', 'INSURANCE', '2026-03-01', 5800.00, '年度商业险及交强险续保', 1, 1, NOW(), NOW(), 0, 0);

-- —— 材料扩展（退料/调拨）——

-- 材料退款申请
INSERT IGNORE INTO biz_material_refund (id, tenant_id, project_id, outbound_id, contract_id, refund_code, refund_amount, refund_reason, status, workflow_instance_id, created_by, created_at, updated_at, version, deleted) VALUES
(94911, 1, 90001, 94101, 91501, 'TK20260401001', 90000.00, '部分水泥受潮结块，退回供应商', 'APPROVED', NULL, 1, NOW(), NOW(), 0, 0);

-- 材料退款明细
INSERT IGNORE INTO biz_material_refund_detail (id, tenant_id, refund_id, material_name, specification, unit, quantity, unit_price, amount, created_by, created_at, updated_at, version, deleted) VALUES
(94921, 1, 94911, '硅酸盐水泥', 'P.O 42.5', '吨', 200.0000, 450.0000, 90000.00, 1, NOW(), NOW(), 0, 0);

-- 材料调拨单（滨江花园 → 城南市政）
INSERT IGNORE INTO biz_material_transfer (id, from_project_id, to_project_id, transfer_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(94931, 90001, 90002, '2026-05-20', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 材料调拨明细
INSERT IGNORE INTO biz_material_transfer_detail (id, transfer_id, material_name, specification, unit, quantity, unit_price, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(94941, 94931, '中砂',   '细度模数2.3-3.0', '方', 500.0000, 120.0000, 1, 1, NOW(), NOW(), 0, 0),
(94942, 94931, '螺纹钢', 'HRB400 Φ25',      '吨', 20.0000,  3800.0000, 1, 1, NOW(), NOW(), 0, 0);

-- —— 机械扩展（使用记录/工程量结算）——

-- 机械使用记录
INSERT IGNORE INTO biz_machine_usage_record (id, project_id, contract_id, usage_quantity, oil_amount, record_date, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(95911, 90001, 91701, 850.00, 1440.00, '2026-07-15', 1, 1, NOW(), NOW(), 0, 0),
(95912, 90001, 91701, 920.00, 1560.00, '2026-07-16', 1, 1, NOW(), NOW(), 0, 0);

-- 机械工作量结算单
INSERT IGNORE INTO biz_machine_work_settlement (id, project_id, settlement_code, period_start, period_end, total_amount, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(95921, 90001, 'JXJS20260701001', '2026-06-01', '2026-06-30', 260000.00, 2, NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 机械工作量结算明细
INSERT IGNORE INTO biz_machine_work_settlement_detail (id, settlement_id, ledger_id, work_log_ids, shift_count, work_volume, unit_price, subtotal, pricing_type, tenant_id, created_at) VALUES
(95931, 95921, 95001, '[95021,95022]', 2.00, 1770.00, 800.00, 'SHIFT',  160000.00, 1, NOW()),
(95932, 95921, 95002, '[95023]',       2.00, 45.00,   50000.00, 'VOLUME', 100000.00, 1, NOW());

-- —— 财务/采购扩展 ——

-- 采购结算
INSERT IGNORE INTO biz_purchase_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99411, 90001, 91501, 7000000.00, 7000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99412, 90001, 91502, 5000000.00, 5000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 个人报销
INSERT IGNORE INTO biz_personal_reimbursement (id, total_amount, reimbursement_date, remark, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99421, 3200.00, '2026-06-18', '张伟：项目投标差旅报销', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99422, 1500.00, '2026-07-05', '王强：财务票据邮寄及办公采购', 'DRAFT', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 通用支出合同（档案展示 + 到期提醒）
INSERT IGNORE INTO biz_expense_contract (id, tenant_id, project_id, contract_code, contract_name, contract_category, party_a_id, party_a_name, party_b_id, party_b_name, signing_date, end_date, budget_id, contract_amount, tax_rate, amount_without_tax, tax_amount, payment_terms, cooperation_content, cumulative_settlement, cumulative_paid, cumulative_invoice_received, responsible_user_id, status, workflow_instance_id, created_by, created_at, updated_at, deleted, version) VALUES
(99431, 1, 90001, 'FYHT20260201001', '水泥砂石采购合同', 'MATERIAL', 90301, '中正建设集团有限公司', 90401, '杭州鑫达建材有限公司', '2026-02-01', '2026-12-31', 92001, 8000000.00, 13.00, 7079646.02, 920353.98, '月结，次月15日前支付', '滨江花园一期水泥、砂石供应', 7000000.00, 6000000.00, 7000000.00, 90074, 'EFFECTIVE', NULL, 1, NOW(), NOW(), 0, 0),
(99432, 1, 90001, 'FYHT20260301001', '装饰工程分包合同', 'SUBCONTRACT', 90301, '中正建设集团有限公司', 90405, '杭州恒基装饰工程有限公司', '2026-03-01', '2026-09-15', 92001, 5000000.00, 9.00, 4587155.96, 412844.04, '按进度支付，月结', '公共区域精装修分包', 3000000.00, 2000000.00, 3000000.00, 90071, 'EFFECTIVE', NULL, 1, NOW(), NOW(), 0, 0);

-- 保证金退还（投标项目C）
INSERT IGNORE INTO biz_deposit_return (id, deposit_apply_id, return_amount, return_date, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99441, 90831, 500000.00, '2026-04-10', 1, 1, NOW(), NOW(), 0, 0);

-- 备用金归还
INSERT IGNORE INTO biz_reserve_fund_return (id, reserve_apply_id, return_amount, return_date, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99451, 99071, 10000.00, '2026-06-20', 1, 1, NOW(), NOW(), 0, 0);

-- 质保金返还（项目B）
INSERT IGNORE INTO biz_retention_return (id, retention_id, return_amount, return_date, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99461, 99091, 837500.00, '2027-05-20', 'DRAFT', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 财务封账
INSERT IGNORE INTO biz_finance_lock (id, period, lock_type, status, project_id, lock_by, lock_time, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99471, '2026-05', 'MONTHLY', 'LOCKED', NULL,  90073, '2026-06-05 18:00:00', 1, 1, NOW(), NOW(), 0, 0),
(99472, '2026-06', 'MONTHLY', 'LOCKED', 90001, 90073, '2026-07-05 18:00:00', 1, 1, NOW(), NOW(), 0, 0);

-- —— 投标/分包/档案/门户 ——

-- 开标记录（项目C 中标）
INSERT IGNORE INTO biz_open_bid_record (id, register_id, project_id, is_won, win_info, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90891, 90801, 90003, 1, '中标金额2800万元，综合评估法排名第一', 'WON', 1, 1, NOW(), NOW(), 0, 0);

-- 分包结算明细（关联分包结算 97011）
INSERT IGNORE INTO biz_subcontract_settlement_detail (id, tenant_id, settlement_id, item_name, unit, quantity, unit_price, amount, remark, sort_order, created_by, created_at, updated_at, deleted, version) VALUES
(97911, 1, 97011, '公共区域地面铺装', 'm²', 8000.0000, 220.0000, 1760000.00, '大堂及走道石材铺贴', 1, 1, NOW(), NOW(), 0, 0),
(97912, 1, 97011, '公共区域墙面装饰', 'm²', 6200.0000, 200.0000, 1240000.00, '涂料及饰面板', 2, 1, NOW(), NOW(), 0, 0);

-- 文件信息（档案/附件）
INSERT IGNORE INTO file_info (id, original_name, file_name, file_path, file_size, file_type, storage_type, business_type, business_id, project_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99481, '滨江花园施工合同.pdf', '20260105_ht001.pdf', 'contract/2026/20260105_ht001.pdf', 2456123, 'application/pdf', 'MINIO', 'CONSTRUCTION_CONTRACT', 91001, 90001, 1, 1, NOW(), NOW(), 0, 0),
(99482, '主体结构验收报告.pdf', '20260625_ys001.pdf', 'inspection/2026/20260625_ys001.pdf', 1345678, 'application/pdf', 'MINIO', 'INSPECTION', 98031, 90001, 1, 1, NOW(), NOW(), 0, 0),
(99483, '城南道路竣工照片.jpg', '20260520_img001.jpg', 'acceptance/2026/20260520_img001.jpg', 856432, 'image/jpeg', 'MINIO', 'COMPLETION_ACCEPTANCE', 98061, 90002, 1, 1, NOW(), NOW(), 0, 0);

-- 供应商门户账户（供应商可登录报价）
INSERT IGNORE INTO sys_supplier_account (id, supplier_id, phone, status, created_at) VALUES
(99491, 90401, '13900001111', 1, NOW()),
(99492, 90402, '13900002222', 1, NOW());

-- ============ 种子数据插入完成 ============
-- 覆盖模块: 系统管理/基础数据/项目/投标/合同/预算/产值/材料/机械/劳务/分包/现场/财务/询价/消息/评价/人事/行政/档案/门户
-- 总计约 300 条记录，覆盖 95+ 张业务表（含 sys_* 系统管理表 + HR/行政/扩展单据）

-- ============ 编号规则保障（幂等） ============
-- 背景：SerialNumberService.generate(...) 仅涉及以下 5 种业务类型，
--       已由 23/29 脚本初始化。此处重复保障，使本种子脚本自包含，
--       避免创建项目/合同/采购合同/资金调拨/采购结算时因缺规则报 500。
INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900001, 'PROJECT', 'PRJ', 'yyyyMMdd', 4, 'MONTH', '项目编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'PROJECT' AND tenant_id = 1);

INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900002, 'CONTRACT', 'HT', 'yyyyMMdd', 4, 'MONTH', '施工合同编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'CONTRACT' AND tenant_id = 1);

INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900003, 'PURCHASE_CONTRACT', 'CG', 'yyyyMMdd', 4, 'MONTH', '采购合同编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'PURCHASE_CONTRACT' AND tenant_id = 1);

INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900004, 'FUND_TRANSFER', 'ZJ', 'yyyyMMdd', 4, 'MONTH', '资金调拨编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'FUND_TRANSFER' AND tenant_id = 1);

INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900005, 'PURCHASE_SETTLEMENT', 'CGJS', 'yyyyMMdd', 4, 'MONTH', '采购结算编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'PURCHASE_SETTLEMENT' AND tenant_id = 1);
