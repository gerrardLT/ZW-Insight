import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'

const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/login/forgot-password.vue'),
    meta: { title: '找回密码', hidden: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled', permission: 'dashboard:view' }
      },
      {
        // V2026_122 并入经营驾驶舱 Hub（保留直达，退出侧边栏）
        path: 'project-dashboard',
        name: 'ProjectDashboard',
        component: () => import('@/views/dashboard/project-dashboard.vue'),
        meta: { title: '项目看板', icon: 'DataAnalysis', permission: 'project-dashboard:view', hidden: true }
      },
      {
        // V2026_122 并入经营驾驶舱 Hub（保留直达，退出侧边栏）
        path: 'project-cost-control',
        name: 'ProjectCostControl',
        component: () => import('@/views/dashboard/project-cost-control.vue'),
        meta: { title: '成本主线看板', icon: 'TrendCharts', permission: 'dashboard:costcontrol:view', hidden: true }
      }
    ]
  },
  // 系统管理
  {
    path: '/system',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/system/org',
    meta: { title: '系统管理', icon: 'Setting', permission: 'system:view' },
    children: [
      {
        path: 'org',
        name: 'OrgManage',
        component: () => import('@/views/system/org/index.vue'),
        meta: { title: '机构管理', icon: 'OfficeBuilding' }
      },
      {
        path: 'user',
        name: 'UserManage',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '人员管理', icon: 'User' }
      },
      {
        path: 'role',
        name: 'RoleManage',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', icon: 'UserFilled' }
      },
      {
        path: 'menu',
        name: 'MenuManage',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理', icon: 'Menu' }
      },
      {
        path: 'dict',
        name: 'DictManage',
        component: () => import('@/views/system/dict/index.vue'),
        meta: { title: '数据字典', icon: 'Collection' }
      },
      {
        path: 'post',
        name: 'PostManage',
        component: () => import('@/views/system/post/index.vue'),
        meta: { title: '岗位管理', icon: 'Stamp' }
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config/index.vue'),
        meta: { title: '系统设置', icon: 'Tools' }
      },
      {
        path: 'template',
        name: 'TemplateManage',
        component: () => import('@/views/system/template/index.vue'),
        meta: { title: '模板管理', icon: 'Files' }
      },
      {
        path: 'print-template',
        name: 'PrintTemplateManage',
        component: () => import('@/views/system/print-template/index.vue'),
        meta: { title: '打印模板', icon: 'Printer' }
      },
      {
        path: 'log',
        name: 'LogManage',
        component: () => import('@/views/system/log/index.vue'),
        meta: { title: '日志管理', icon: 'Document' }
      },
      {
        path: 'amount-tier',
        name: 'AmountTierConfig',
        component: () => import('@/views/system/amount-tier/index.vue'),
        meta: { title: '审批分级配置', icon: 'Sort' }
      },
      {
        path: 'serial-number',
        name: 'SerialNumberManage',
        component: () => import('@/views/system/serial-number/index.vue'),
        meta: { title: '编号规则管理', icon: 'Odometer' }
      },
      {
        path: 'backup',
        name: 'BackupManage',
        component: () => import('@/views/system/backup/index.vue'),
        meta: { title: '数据备份', icon: 'FolderChecked' }
      },
      {
        path: 'version',
        name: 'VersionManage',
        component: () => import('@/views/system/version/index.vue'),
        meta: { title: '版本管理', icon: 'Tickets' }
      },
      {
        path: 'monitor',
        name: 'SystemMonitor',
        component: () => import('@/views/system/monitor/index.vue'),
        meta: { title: '系统监控', icon: 'Monitor' }
      }
    ]
  },
  // 个人中心
  {
    path: '/user',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/user/devices',
    meta: { title: '个人中心', icon: 'User', hidden: true },
    children: [
      {
        path: 'devices',
        name: 'UserDevices',
        component: () => import('@/views/user/devices.vue'),
        meta: { title: '登录设备', icon: 'Monitor' }
      }
    ]
  },
  // 项目管理
  {
    path: '/project',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/project/hub/ledger-group',
    meta: { title: '项目管理', icon: 'Briefcase', permission: 'project:view' },
    children: [
      // ===== V2026_121 二级菜单收敛：项目台账与 WBS 合 1 入口 =====
      {
        path: 'hub/ledger-group',
        name: 'ProjectHubLedger',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '项目台账与工程分解', icon: 'Document', group: '项目主档', permission: 'project:view',
          hubTabs: [
            { key: 'list', label: '项目台账', loader: () => import('@/views/project/index.vue') },
            { key: 'wbs', label: '工程分解与任务节点（WBS）', loader: () => import('@/views/project/wbs/index.vue'), permission: 'project:wbs:view' }
          ]
        }
      },
      {
        path: 'list',
        name: 'ProjectList',
        component: () => import('@/views/project/index.vue'),
        meta: { title: '项目台账', icon: 'Document', group: '项目主档', hidden: true }
      },
      {
        // V2026_124：项目经理发起付款的专用入口（复用财务付款页，细粒度权限码）
        path: 'payment-apply',
        name: 'ProjectPaymentApply',
        component: () => import('@/views/finance/payment-apply.vue'),
        meta: { title: '项目付款申请', icon: 'CreditCard', group: '项目主档', permission: 'finance:paymentapply:view' }
      },
      {
        path: 'create',
        name: 'ProjectCreate',
        component: () => import('@/views/project/form.vue'),
        meta: { title: '新增项目', hidden: true, permission: 'project:create' }
      },
      {
        path: 'edit/:id',
        name: 'ProjectEdit',
        component: () => import('@/views/project/form.vue'),
        meta: { title: '编辑项目', hidden: true, permission: 'project:edit' }
      },
      {
        path: 'detail/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/detail.vue'),
        meta: { title: '项目详情', hidden: true }
      },
      {
        path: 'wbs',
        name: 'ProjectWbs',
        component: () => import('@/views/project/wbs/index.vue'),
        meta: { title: '工程分解与任务节点（WBS）', icon: 'Share', permission: 'project:wbs:view', group: '项目主档', hidden: true }
      }
    ]
  },
  // 合同管理
  {
    path: '/contract',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/contract/list',
    meta: { title: '合同管理', icon: 'Notebook', permission: 'contract:view' },
    children: [
      // ===== V2026_121 二级菜单收敛：总包合同/产值/签证合 1 入口 =====
      {
        path: 'hub/contract-group',
        name: 'ContractHubMain',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '总包合同与产值', icon: 'Document', group: '总包合同', permission: 'contract:view',
          hubTabs: [
            { key: 'list', label: '施工总承包合同', loader: () => import('@/views/contract/index.vue') },
            { key: 'output-report', label: '工程产值申报', loader: () => import('@/views/contract/output-report.vue') },
            { key: 'change-event', label: '工程变更签证', loader: () => import('@/views/contract/change-event/index.vue'), permission: 'contract:changeevent:view' }
          ]
        }
      },
      {
        path: 'list',
        name: 'ContractList',
        component: () => import('@/views/contract/index.vue'),
        meta: { title: '施工总承包合同', icon: 'Document', group: '总包合同', hidden: true }
      },
      {
        path: 'create',
        name: 'ContractCreate',
        component: () => import('@/views/contract/form.vue'),
        meta: { title: '新增合同', hidden: true }
      },
      {
        path: 'edit/:id',
        name: 'ContractEdit',
        component: () => import('@/views/contract/form.vue'),
        meta: { title: '编辑合同', hidden: true }
      },
      {
        path: 'boq/:contractId',
        name: 'ContractBoqUpload',
        component: () => import('@/views/contract/boq-upload.vue'),
        meta: { title: 'BOQ 上传', hidden: true }
      },
      {
        path: 'output-report',
        name: 'OutputReport',
        component: () => import('@/views/contract/output-report.vue'),
        meta: { title: '工程产值申报', icon: 'TrendCharts', group: '总包合同', hidden: true }
      },
      {
        path: 'change-event',
        name: 'ChangeEventList',
        component: () => import('@/views/contract/change-event/index.vue'),
        meta: { title: '工程变更签证', icon: 'Switch', permission: 'contract:changeevent:view', group: '总包合同', hidden: true }
      }
    ]
  },
  // 经营驾驶舱（工程老板视角，V2026_59；与项目管理层 dashboard 分开，不改造旧首页）
  {
    path: '/cockpit',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/cockpit/hub/operation-group',
    meta: { title: '经营驾驶舱', icon: 'Odometer', permission: 'dashboard:view' },
    children: [
      // ===== V2026_121/122 二级菜单收敛：五看板合 1 入口，资金/风险中心保留 =====
      {
        path: 'hub/operation-group',
        name: 'CockpitHubOperation',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '经营驾驶舱', icon: 'DataBoard', permission: 'dashboard:view',
          hubTabs: [
            { key: 'overview', label: '经营总览', loader: () => import('@/views/cockpit/index.vue') },
            { key: 'project-operation', label: '项目经营', loader: () => import('@/views/cockpit/project-operation.vue') },
            { key: 'cost-center', label: '成本中心', loader: () => import('@/views/cockpit/cost-center.vue') },
            { key: 'project-dashboard', label: '项目看板', loader: () => import('@/views/dashboard/project-dashboard.vue'), permission: 'project-dashboard:view' },
            { key: 'project-cost-control', label: '成本主线看板', loader: () => import('@/views/dashboard/project-cost-control.vue'), permission: 'dashboard:costcontrol:view' }
          ]
        }
      },
      {
        path: 'overview',
        name: 'CockpitOverview',
        component: () => import('@/views/cockpit/index.vue'),
        meta: { title: '经营总览', icon: 'DataBoard', hidden: true }
      },
      {
        path: 'fund-center',
        name: 'CockpitFundCenter',
        component: () => import('@/views/cockpit/fund-center.vue'),
        meta: { title: '资金中心', icon: 'Money' }
      },
      {
        path: 'risk-center',
        name: 'CockpitRiskCenter',
        component: () => import('@/views/cockpit/risk-center.vue'),
        meta: { title: '风险中心', icon: 'WarnTriangleFilled' }
      },
      {
        // 项目经营（§12 列表 + §6 单项目详情：8 指标/四象限/项目风险）
        path: 'project-operation',
        name: 'CockpitProjectOperation',
        component: () => import('@/views/cockpit/project-operation.vue'),
        meta: { title: '项目经营', icon: 'OfficeBuilding', hidden: true }
      },
      {
        // 成本中心（§7.1 总览 + §7.2 七类结构 + §8 商务费用下钻 + §8.1 招待费）
        path: 'cost-center',
        name: 'CockpitCostCenter',
        component: () => import('@/views/cockpit/cost-center.vue'),
        meta: { title: '成本中心', icon: 'Coin', hidden: true }
      }
    ]
  },
  // 财务管理
  {
    path: '/finance',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/finance/hub/receivable-group',
    meta: { title: '财务管理', icon: 'Money', permission: 'finance:view' },
    children: [
      // ===== V2026_120 二级菜单收敛：29 项收敛为 8 个一等入口（Hub 内 Tab）=====
      // 原路由全部保留（meta.hidden 退出侧边栏，URL 直达与权限码不变），入口 Tab 懒挂载原组件。
      {
        path: 'hub/receivable-group',
        name: 'FinanceHubReceivable',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '工程应收与结算', icon: 'Money', group: '收入钱链', permission: 'finance:view',
          // S2：首 Tab 由竣工结算改为应收台账（日常对账入口），结算降为次级
          hubTabs: [
            { key: 'receivable', label: '应收工程账款台账', loader: () => import('@/views/finance/receivable/index.vue'), badge: 'financeOverdueReceivable' },
            { key: 'settlement', label: '工程竣工结算', loader: () => import('@/views/finance/settlement/index.vue') },
            { key: 'invoice-apply', label: '工程开票申请', loader: () => import('@/views/finance/invoice-apply.vue') },
            { key: 'payment-received', label: '工程回款登记', loader: () => import('@/views/finance/payment-received.vue') }
          ]
        }
      },
      {
        path: 'hub/payable-group',
        name: 'FinanceHubPayable',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '工程应付与支出', icon: 'CreditCard', group: '支出钱链', permission: 'finance:view',
          hubTabs: [
            { key: 'workbench', label: '财务待办', loader: () => import('@/views/finance/workbench/index.vue') },
            { key: 'payment-apply', label: '工程付款申请', loader: () => import('@/views/finance/payment-apply.vue'), badge: 'financePendingApproval' },
            { key: 'payable', label: '应付账款台账', loader: () => import('@/views/finance/payable/index.vue'), badge: 'financeUnpaid' },
            { key: 'other-payment', label: '其他支出费用', loader: () => import('@/views/finance/other-payment.vue') }
          ]
        }
      },
      {
        path: 'hub/reimbursement-group',
        name: 'FinanceHubReimbursement',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '日常报销与备用金', icon: 'WalletFilled', group: '支出钱链', permission: 'finance:view',
          hubTabs: [
            { key: 'project-reimbursement', label: '项目现场报销', loader: () => import('@/views/finance/project-reimbursement.vue') },
            { key: 'personal-reimbursement', label: '员工日常报销', loader: () => import('@/views/finance/personal-reimbursement.vue') },
            { key: 'reserve-fund', label: '备用金借支与核销', loader: () => import('@/views/finance/reserve-fund.vue') }
          ]
        }
      },
      {
        path: 'hub/invoice-group',
        name: 'FinanceHubInvoice',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '发票税务台账', icon: 'Tickets', group: '发票与税务', permission: 'finance:view',
          hubTabs: [
            { key: 'invoice-received', label: '进项发票台账', loader: () => import('@/views/finance/invoice-received.vue') },
            { key: 'invoice-issued', label: '销项发票台账', loader: () => import('@/views/finance/invoice-issued.vue') },
            { key: 'invoice-summary', label: '进销项税负汇总', loader: () => import('@/views/finance/invoice-summary.vue') }
          ]
        }
      },
      {
        path: 'hub/bank-group',
        name: 'FinanceHubBank',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '银行账户与流水', icon: 'Coin', group: '账户与资金', permission: 'finance:view',
          hubTabs: [
            { key: 'bank-account-group', label: '银行账户清单', loader: () => import('@/views/finance/bank-account-group/index.vue') },
            { key: 'bank-flow', label: '银行流水明细', loader: () => import('@/views/finance/bank-flow/index.vue') },
            { key: 'balance-reconciliation', label: '银行余额调节表', loader: () => import('@/views/finance/balance-reconciliation/index.vue') }
          ]
        }
      },
      {
        path: 'hub/special-fund-group',
        name: 'FinanceHubSpecialFund',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '专项资金与金融', icon: 'GoldMedal', group: '专项资金台账', permission: 'finance:view',
          hubTabs: [
            { key: 'security-bond', label: '投标与履约保证金', loader: () => import('@/views/finance/security-bond/index.vue') },
            { key: 'retention', label: '工程质量保证金', loader: () => import('@/views/finance/retention.vue') },
            { key: 'wage-account', label: '农民工工资专户', loader: () => import('@/views/finance/wage-account/index.vue') },
            { key: 'bill', label: '承兑汇票台账', loader: () => import('@/views/finance/bill/index.vue') },
            { key: 'financing', label: '项目融资借款', loader: () => import('@/views/finance/financing/index.vue') }
          ]
        }
      },
      {
        path: 'hub/plan-group',
        name: 'FinanceHubPlan',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '资金计划与经营分析', icon: 'Calendar', group: '经营分析', permission: 'finance:view',
          hubTabs: [
            { key: 'fund-plan', label: '月度资金收支计划', loader: () => import('@/views/finance/fund-plan/index.vue') },
            { key: 'daily-cash-report', label: '每日资金头寸日报', loader: () => import('@/views/finance/daily-cash-report/index.vue') },
            { key: 'fund-dashboard', label: '资金大屏看板', loader: () => import('@/views/finance/fund-dashboard/index.vue') },
            { key: 'monthly-analysis', label: '月度综合经营分析', loader: () => import('@/views/finance/monthly-analysis/index.vue') },
            { key: 'entertainment-analysis', label: '业务招待费专项', loader: () => import('@/views/finance/entertainment-analysis/index.vue') }
          ]
        }
      },
      {
        path: 'hub/settings-group',
        name: 'FinanceHubSettings',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '财务基础设置', icon: 'Setting', group: '财务设置', permission: 'finance:view',
          hubTabs: [
            { key: 'fund-category', label: '收支科目设置', loader: () => import('@/views/finance/fund-category/index.vue') },
            { key: 'tax-rate', label: '增值税率设置', loader: () => import('@/views/finance/tax-rate/index.vue') },
            { key: 'finance-lock', label: '财务关账封账', loader: () => import('@/views/finance/finance-lock/index.vue') }
          ]
        }
      },
      // —— 收入 ——（meta.group 由 utils/navigation.ts 读取，作侧边栏分组标题）
      {
        path: 'invoice-apply',
        name: 'InvoiceApply',
        component: () => import('@/views/finance/invoice-apply.vue'),
        meta: { title: '工程开票申请', icon: 'Ticket', group: '收入与应收', hidden: true }
      },
      {
        path: 'payment-received',
        name: 'PaymentReceived',
        component: () => import('@/views/finance/payment-received.vue'),
        meta: { title: '工程回款登记', icon: 'WalletFilled', group: '收入与应收', hidden: true }
      },
      {
        path: 'settlement',
        name: 'SettlementList',
        component: () => import('@/views/finance/settlement/index.vue'),
        meta: { title: '工程竣工结算', icon: 'Money', group: '收入与应收', hidden: true }
      },
      {
        path: 'receivable',
        name: 'ReceivableLedger',
        component: () => import('@/views/finance/receivable/index.vue'),
        meta: { title: '应收工程账款台账', icon: 'Money', group: '收入与应收', hidden: true }
      },
      // —— 支出 ——
      {
        path: 'payment-apply',
        name: 'PaymentApply',
        component: () => import('@/views/finance/payment-apply.vue'),
        meta: { title: '工程付款申请', icon: 'CreditCard', group: '支出与应付', hidden: true }
      },
      {
        path: 'payable',
        name: 'PayableLedger',
        component: () => import('@/views/finance/payable/index.vue'),
        meta: { title: '应付账款台账', icon: 'Money', group: '支出与应付', hidden: true }
      },
      {
        path: 'other-payment',
        name: 'OtherPayment',
        component: () => import('@/views/finance/other-payment.vue'),
        meta: { title: '其他支出', icon: 'Coin', group: '支出与应付', hidden: true }
      },
      {
        path: 'project-reimbursement',
        name: 'ProjectReimbursement',
        component: () => import('@/views/finance/project-reimbursement.vue'),
        meta: { title: '项目现场报销', icon: 'Document', group: '支出与应付', hidden: true }
      },
      {
        path: 'personal-reimbursement',
        name: 'PersonalReimbursement',
        component: () => import('@/views/finance/personal-reimbursement.vue'),
        meta: { title: '员工日常报销', icon: 'User', group: '支出与应付', hidden: true }
      },
      // —— 发票税务 ——
      {
        path: 'invoice-received',
        name: 'InvoiceReceived',
        component: () => import('@/views/finance/invoice-received.vue'),
        meta: { title: '进项发票台账', icon: 'Tickets', group: '发票与税务', hidden: true }
      },
      {
        path: 'invoice-issued',
        name: 'InvoiceIssued',
        component: () => import('@/views/finance/invoice-issued.vue'),
        meta: { title: '销项发票台账', icon: 'Ticket', group: '发票与税务', hidden: true }
      },
      {
        path: 'invoice-summary',
        name: 'InvoiceSummary',
        component: () => import('@/views/finance/invoice-summary.vue'),
        meta: { title: '发票税负统计', icon: 'DataAnalysis', group: '发票与税务', hidden: true }
      },
      {
        path: 'tax-rate',
        name: 'TaxRate',
        component: () => import('@/views/finance/tax-rate/index.vue'),
        meta: { title: '增值税率设置', icon: 'Histogram', group: '财务设置', hidden: true }
      },
      // —— 资金 ——
      {
        path: 'fund-category',
        name: 'FundCategory',
        component: () => import('@/views/finance/fund-category/index.vue'),
        meta: { title: '收支科目设置', icon: 'Collection', group: '财务设置', hidden: true }
      },
      {
        path: 'bank-account-group',
        name: 'BankAccountGroup',
        component: () => import('@/views/finance/bank-account-group/index.vue'),
        meta: { title: '银行账户管理', icon: 'Grid', group: '账户与资金', hidden: true }
      },
      {
        path: 'bank-flow',
        name: 'BankFlow',
        component: () => import('@/views/finance/bank-flow/index.vue'),
        meta: { title: '银行流水明细', icon: 'Sort', group: '账户与资金', hidden: true }
      },
      {
        path: 'fund-plan',
        name: 'FundPlan',
        component: () => import('@/views/finance/fund-plan/index.vue'),
        meta: { title: '月度资金收支计划', icon: 'Calendar', group: '账户与资金', hidden: true }
      },
      {
        path: 'daily-cash-report',
        name: 'DailyCashReport',
        component: () => import('@/views/finance/daily-cash-report/index.vue'),
        meta: { title: '每日资金头寸日报', icon: 'Calendar', group: '账户与资金', hidden: true }
      },
      {
        path: 'balance-reconciliation',
        name: 'BalanceReconciliation',
        component: () => import('@/views/finance/balance-reconciliation/index.vue'),
        meta: { title: '银行余额调节表', icon: 'Operation', group: '账户与资金', hidden: true }
      },
      {
        path: 'bill',
        name: 'BillLedger',
        component: () => import('@/views/finance/bill/index.vue'),
        meta: { title: '承兑汇票台账', icon: 'Tickets', group: '专项资金台账', hidden: true }
      },
      {
        path: 'financing',
        name: 'FinancingLedger',
        component: () => import('@/views/finance/financing/index.vue'),
        meta: { title: '项目融资借款', icon: 'CreditCard', group: '专项资金台账', hidden: true }
      },
      // —— 专项 ——
      {
        path: 'reserve-fund',
        name: 'ReserveFund',
        component: () => import('@/views/finance/reserve-fund.vue'),
        meta: { title: '备用金借支与核销', icon: 'Wallet', group: '专项资金台账', hidden: true }
      },
      {
        path: 'retention',
        name: 'Retention',
        component: () => import('@/views/finance/retention.vue'),
        meta: { title: '工程质量保证金', icon: 'GoldMedal', group: '专项资金台账', hidden: true }
      },
      {
        path: 'security-bond',
        name: 'SecurityBond',
        component: () => import('@/views/finance/security-bond/index.vue'),
        meta: { title: '投标与履约保证金', icon: 'Lock', group: '专项资金台账', hidden: true }
      },
      {
        path: 'wage-account',
        name: 'WageAccount',
        component: () => import('@/views/finance/wage-account/index.vue'),
        meta: { title: '农民工工资专户', icon: 'Wallet', group: '专项资金台账', hidden: true }
      },
      // —— 分析 ——
      {
        path: 'fund-dashboard',
        name: 'FundDashboard',
        component: () => import('@/views/finance/fund-dashboard/index.vue'),
        meta: { title: '资金大屏看板', icon: 'DataLine', group: '经营分析', hidden: true }
      },
      {
        // 招待费分析（资金流转 §6.3 分析维度 + §8.1 老板默认看异常）
        path: 'entertainment-analysis',
        name: 'EntertainmentAnalysis',
        component: () => import('@/views/finance/entertainment-analysis/index.vue'),
        meta: { title: '业务招待费专项', icon: 'DataAnalysis', group: '经营分析', hidden: true }
      },
      {
        // 月度经营分析表（资金流转 §9：10 类费用 × 6 列，项目管理层“最终只看一张表”）
        path: 'monthly-analysis',
        name: 'MonthlyOperationAnalysis',
        component: () => import('@/views/finance/monthly-analysis/index.vue'),
        meta: { title: '月度综合经营分析', icon: 'Tickets', group: '经营分析', hidden: true }
      },
      // —— 治理 ——
      {
        path: 'finance-lock',
        name: 'FinanceLock',
        component: () => import('@/views/finance/finance-lock/index.vue'),
        meta: { title: '财务关账封账', icon: 'Lock', group: '财务设置', hidden: true }
      },
      {
        path: 'settlement/:id',
        name: 'SettlementDetail',
        component: () => import('@/views/finance/settlement/detail.vue'),
        meta: { title: '结算单详情', hidden: true }
      }
    ]
  },
  // 预算管理
  {
    path: '/budget',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/budget/hub/cost-group',
    meta: { title: '预算管理', icon: 'Coin', permission: 'budget:view' },
    children: [
      // ===== V2026_121 二级菜单收敛：目标成本 4 项收敛为 1 入口 =====
      {
        path: 'hub/cost-group',
        name: 'BudgetHubMain',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '目标成本管理', icon: 'Coin', permission: 'budget:view',
          hubTabs: [
            { key: 'list', label: '项目目标成本预算', loader: () => import('@/views/budget/index.vue') },
            { key: 'change', label: '目标成本调整单', loader: () => import('@/views/budget/change/index.vue') },
            { key: 'cost-account', label: '目标成本科目（CBS）', loader: () => import('@/views/budget/cost-account/index.vue'), permission: 'budget:costaccount:view' },
            { key: 'control-config', label: '成本预算控制配置', loader: () => import('@/views/budget/control-config/index.vue') }
          ]
        }
      },
      {
        path: 'list',
        name: 'BudgetList',
        component: () => import('@/views/budget/index.vue'),
        meta: { title: '项目目标成本预算', icon: 'Document', hidden: true }
      },
      {
        path: 'change',
        name: 'BudgetChangeList',
        component: () => import('@/views/budget/change/index.vue'),
        meta: { title: '目标成本调整单', icon: 'Switch', hidden: true }
      },
      {
        path: 'change/form',
        name: 'BudgetChangeForm',
        component: () => import('@/views/budget/change/form.vue'),
        meta: { title: '变更单表单', hidden: true }
      },
      {
        path: 'control-config',
        name: 'BudgetControlConfig',
        component: () => import('@/views/budget/control-config/index.vue'),
        meta: { title: '成本预算控制配置', icon: 'Setting', hidden: true }
      },
      {
        path: 'cost-account',
        name: 'CostAccountList',
        component: () => import('@/views/budget/cost-account/index.vue'),
        meta: { title: '目标成本科目（CBS）', icon: 'Coin', permission: 'budget:costaccount:view', hidden: true }
      }
    ]
  },
  // 采购管理
  {
    path: '/purchase',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/purchase/contract',
    meta: { title: '采购管理', icon: 'ShoppingCart', permission: 'purchase:view' },
    children: [
      {
        path: 'contract',
        name: 'PurchaseContract',
        component: () => import('@/views/purchase/contract.vue'),
        meta: { title: '物资采购合同', icon: 'Document', hidden: true }
      },
      {
        path: 'settlement',
        name: 'PurchaseSettlement',
        component: () => import('@/views/purchase/settlement.vue'),
        meta: { title: '采购结算单', icon: 'Tickets', hidden: true }
      },
      {
        path: 'inquiry',
        name: 'PurchaseInquiry',
        component: () => import('@/views/purchase/inquiry.vue'),
        meta: { title: '采购询价与比价', icon: 'DataAnalysis', hidden: true }
      }
    ]
  },
  // 劳务管理
  {
    path: '/labor',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/labor/hub/labor-group',
    meta: { title: '劳务管理', icon: 'Avatar', permission: 'labor:view' },
    children: [
      // ===== V2026_121 二级菜单收敛：劳务 9 项收敛为 1 入口 =====
      {
        path: 'hub/labor-group',
        name: 'LaborHubMain',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '劳务班组管理', icon: 'Avatar', permission: 'labor:view',
          // S2：首屏按角色落到作业页（项目经理/普通员工=点工，其余=花名册），合同退到第 3 位
          hubTabs: [
            { key: 'work-order', label: '现场点工派工单', loader: () => import('@/views/labor/work-order.vue'), defaultForRoles: ['PROJECT_MANAGER', 'STAFF'], badge: 'laborWorkOrderDraft' },
            { key: 'roster', label: '现场劳务花名册', loader: () => import('@/views/labor/roster.vue'), defaultForRoles: ['SUPER_ADMIN', 'COMPANY_LEADER', 'FINANCE_STAFF', 'MATERIAL_STAFF', 'COMMERCE_STAFF', 'AUDITOR'] },
            { key: 'contract', label: '劳务分包合同', loader: () => import('@/views/labor/contract.vue'), group: '合同与班组' },
            { key: 'team', label: '施工班组管理', loader: () => import('@/views/labor/team.vue'), group: '合同与班组' },
            { key: 'payroll', label: '劳务班组工资单', loader: () => import('@/views/labor/payroll.vue'), group: '作业与工资' },
            { key: 'salary-stats', label: '薪资统计', loader: () => import('@/views/labor/salary/stats.vue'), group: '作业与工资' },
            { key: 'output', label: '劳务完成产值', loader: () => import('@/views/labor/output.vue'), group: '结算与奖惩' },
            { key: 'settlement', label: '劳务结算单', loader: () => import('@/views/labor/settlement.vue'), group: '结算与奖惩' },
            { key: 'reward-punish', label: '劳务奖惩', loader: () => import('@/views/labor/reward-punish.vue'), group: '结算与奖惩' }
          ]
        }
      },
      {
        path: 'contract',
        name: 'LaborContract',
        component: () => import('@/views/labor/contract.vue'),
        meta: { title: '劳务分包合同', icon: 'Document', hidden: true }
      },
      {
        path: 'team',
        name: 'LaborTeam',
        component: () => import('@/views/labor/team.vue'),
        meta: { title: '施工班组管理', icon: 'UserFilled', hidden: true }
      },
      {
        path: 'roster',
        name: 'LaborRoster',
        component: () => import('@/views/labor/roster.vue'),
        meta: { title: '现场劳务花名册', icon: 'List', hidden: true }
      },
      {
        path: 'work-order',
        name: 'LaborWorkOrder',
        component: () => import('@/views/labor/work-order.vue'),
        meta: { title: '现场点工派工单', icon: 'Memo', hidden: true }
      },
      {
        path: 'payroll',
        name: 'LaborPayroll',
        component: () => import('@/views/labor/payroll.vue'),
        meta: { title: '劳务班组工资单', icon: 'Wallet', hidden: true }
      },
      {
        path: 'salary-stats',
        name: 'LaborSalaryStats',
        component: () => import('@/views/labor/salary/stats.vue'),
        meta: { title: '薪资统计', icon: 'DataAnalysis', hidden: true }
      },
      {
        path: 'output',
        name: 'LaborOutput',
        component: () => import('@/views/labor/output.vue'),
        meta: { title: '劳务完成产值', icon: 'TrendCharts', hidden: true }
      },
      {
        path: 'settlement',
        name: 'LaborSettlement',
        component: () => import('@/views/labor/settlement.vue'),
        meta: { title: '劳务结算单', icon: 'Files', hidden: true }
      },
      {
        path: 'reward-punish',
        name: 'LaborRewardPunish',
        component: () => import('@/views/labor/reward-punish.vue'),
        meta: { title: '劳务奖惩', icon: 'Scale', hidden: true }
      }
    ]
  },
  // 材料库存
  {
    path: '/material',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/material/hub/material-purchase-group',
    meta: { title: '材料库存', icon: 'Box', permission: 'material:view' },
    children: [
      // ===== V2026_121 二级菜单收敛：材料 6 + 采购 3 合 1 入口 =====
      {
        path: 'hub/material-purchase-group',
        name: 'MaterialHubMain',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '材料与采购', icon: 'Box', permission: 'material:view',
          // S2：9 个页签保留前 6 个在主区，采购 3 项收进「更多」（按 group 分组）
          hubTabs: [
            { key: 'inbound', label: '材料入库单', loader: () => import('@/views/material/inbound.vue'), badge: 'materialInboundDraft' },
            { key: 'outbound', label: '现场领料单', loader: () => import('@/views/material/outbound.vue') },
            { key: 'transfer', label: '材料调拨单', loader: () => import('@/views/material/transfer.vue') },
            { key: 'stock', label: '材料库存台账', loader: () => import('@/views/material/stock.vue') },
            { key: 'inventory', label: '材料盘点', loader: () => import('@/views/material/inventory.vue') },
            { key: 'refund', label: '退货退款', loader: () => import('@/views/material/refund.vue') },
            { key: 'purchase-contract', label: '物资采购合同', loader: () => import('@/views/purchase/contract.vue'), group: '采购管理' },
            { key: 'purchase-settlement', label: '采购结算单', loader: () => import('@/views/purchase/settlement.vue'), group: '采购管理' },
            { key: 'purchase-inquiry', label: '采购询价与比价', loader: () => import('@/views/purchase/inquiry.vue'), group: '采购管理' }
          ]
        }
      },
      {
        path: 'inbound',
        name: 'MaterialInbound',
        component: () => import('@/views/material/inbound.vue'),
        meta: { title: '材料入库单', icon: 'Download', hidden: true }
      },
      {
        path: 'outbound',
        name: 'MaterialOutbound',
        component: () => import('@/views/material/outbound.vue'),
        meta: { title: '现场领料单', icon: 'Upload', hidden: true }
      },
      {
        path: 'transfer',
        name: 'MaterialTransfer',
        component: () => import('@/views/material/transfer.vue'),
        meta: { title: '材料调拨单', icon: 'Switch', hidden: true }
      },
      {
        path: 'stock',
        name: 'MaterialStock',
        component: () => import('@/views/material/stock.vue'),
        meta: { title: '材料库存台账', icon: 'Search', hidden: true }
      },
      {
        path: 'inventory',
        name: 'MaterialInventory',
        component: () => import('@/views/material/inventory.vue'),
        meta: { title: '材料盘点', icon: 'DocumentChecked', hidden: true }
      },
      {
        path: 'refund',
        name: 'MaterialRefund',
        component: () => import('@/views/material/refund.vue'),
        meta: { title: '退货退款', icon: 'RefreshLeft', hidden: true }
      }
    ]
  },
  // 机械管理
  {
    path: '/machine',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/machine/hub/machine-group',
    meta: { title: '机械管理', icon: 'Van', permission: 'machine:view' },
    children: [
      // ===== V2026_121 二级菜单收敛：机械 6 项收敛为 1 入口 =====
      {
        path: 'hub/machine-group',
        name: 'MachineHubMain',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '机械租赁管理', icon: 'Van', permission: 'machine:view',
          // S2：首屏按角色落到点检与台班（项目经理/普通员工）或设备台账（其余），合同退到第 3 位
          hubTabs: [
            { key: 'work-log', label: '机械现场点检与台班', loader: () => import('@/views/machine/work-log.vue'), defaultForRoles: ['PROJECT_MANAGER', 'STAFF'], badge: 'machineWorkLogDraft' },
            { key: 'ledger', label: '机械设备台账', loader: () => import('@/views/machine/ledger.vue'), defaultForRoles: ['SUPER_ADMIN', 'COMPANY_LEADER', 'FINANCE_STAFF', 'MATERIAL_STAFF', 'COMMERCE_STAFF', 'AUDITOR'] },
            { key: 'contract', label: '机械租赁合同', loader: () => import('@/views/machine/contract.vue') },
            { key: 'entry', label: '进出场登记', loader: () => import('@/views/machine/entry.vue') },
            { key: 'repair', label: '故障维修', loader: () => import('@/views/machine/repair.vue') },
            { key: 'settlement', label: '机械结算单', loader: () => import('@/views/machine/settlement/index.vue') }
          ]
        }
      },
      {
        path: 'contract',
        name: 'MachineContract',
        component: () => import('@/views/machine/contract.vue'),
        meta: { title: '机械租赁合同', icon: 'Document', hidden: true }
      },
      {
        path: 'ledger',
        name: 'MachineLedger',
        component: () => import('@/views/machine/ledger.vue'),
        meta: { title: '机械设备台账', icon: 'Notebook', hidden: true }
      },
      {
        path: 'entry',
        name: 'MachineEntry',
        component: () => import('@/views/machine/entry.vue'),
        meta: { title: '进出场登记', icon: 'MapLocation', hidden: true }
      },
      {
        path: 'work-log',
        name: 'MachineWorkLog',
        component: () => import('@/views/machine/work-log.vue'),
        meta: { title: '机械现场点检与台班', icon: 'Timer', hidden: true }
      },
      {
        path: 'repair',
        name: 'MachineRepair',
        component: () => import('@/views/machine/repair.vue'),
        meta: { title: '故障维修', icon: 'WarnTriangleFilled', hidden: true }
      },
      {
        path: 'settlement',
        name: 'MachineSettlement',
        component: () => import('@/views/machine/settlement/index.vue'),
        meta: { title: '机械结算单', icon: 'Tickets', hidden: true }
      },
      {
        path: 'settlement/create',
        name: 'MachineSettlementCreate',
        component: () => import('@/views/machine/settlement/create.vue'),
        meta: { title: '新建结算单', hidden: true }
      },
      {
        path: 'settlement/detail/:id',
        name: 'MachineSettlementDetail',
        component: () => import('@/views/machine/settlement/detail.vue'),
        meta: { title: '结算单详情', hidden: true }
      }
    ]
  },
  // 分包管理
  {
    path: '/subcontract',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/subcontract/hub/subcontract-group',
    meta: { title: '分包管理', icon: 'Connection', permission: 'subcontract:view' },
    children: [
      // ===== V2026_121 二级菜单收敛：分包 3 项收敛为 1 入口 =====
      {
        path: 'hub/subcontract-group',
        name: 'SubcontractHubMain',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '专业分包管理', icon: 'Connection', permission: 'subcontract:view',
          hubTabs: [
            { key: 'contract', label: '专业分包合同', loader: () => import('@/views/subcontract/contract.vue') },
            { key: 'settlement', label: '分包结算单', loader: () => import('@/views/subcontract/settlement.vue') },
            { key: 'output', label: '分包完成产值', loader: () => import('@/views/subcontract/output.vue') }
          ]
        }
      },
      {
        path: 'contract',
        name: 'SubContract',
        component: () => import('@/views/subcontract/contract.vue'),
        meta: { title: '专业分包合同', icon: 'Document', hidden: true }
      },
      {
        path: 'settlement',
        name: 'SubSettlement',
        component: () => import('@/views/subcontract/settlement.vue'),
        meta: { title: '分包结算单', icon: 'Tickets', hidden: true }
      },
      {
        path: 'output',
        name: 'SubOutput',
        component: () => import('@/views/subcontract/output.vue'),
        meta: { title: '分包完成产值', icon: 'TrendCharts', hidden: true }
      }
    ]
  },
  // 现场管理
  {
    path: '/site',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/site/schedule',
    meta: { title: '现场管理', icon: 'Place', permission: 'site:view' },
    children: [
      {
        path: 'schedule',
        name: 'SiteSchedule',
        component: () => import('@/views/site/schedule.vue'),
        meta: { title: '现场施工进度计划', icon: 'DataLine' }
      },
      {
        path: 'construction-log',
        name: 'ConstructionLog',
        component: () => import('@/views/site/construction-log.vue'),
        meta: { title: '工程施工日志', icon: 'Memo' }
      },
      {
        path: 'completion',
        name: 'SiteCompletion',
        component: () => import('@/views/site/completion.vue'),
        meta: { title: '工程竣工验收', icon: 'CircleCheck' }
      },
      {
        path: 'inspection',
        name: 'SiteInspection',
        component: () => import('@/views/site/inspection/index.vue'),
        meta: { title: '施工质量检查', icon: 'CircleCheck' }
      },
      {
        // 安全检查：与质量检查同组件，进入页默认定位“安全”页签（按路由名判定）
        path: 'safety-inspection',
        name: 'SiteSafetyInspection',
        component: () => import('@/views/site/inspection/index.vue'),
        meta: { title: '现场安全检查', icon: 'Warning' }
      },
      {
        path: 'inspection/form',
        name: 'InspectionCreate',
        component: () => import('@/views/site/inspection/form.vue'),
        meta: { title: '新增检查', hidden: true }
      },
      {
        path: 'inspection/form/:id',
        name: 'InspectionEdit',
        component: () => import('@/views/site/inspection/form.vue'),
        meta: { title: '编辑检查', hidden: true }
      },
      {
        path: 'inspection/detail/:id',
        name: 'InspectionDetail',
        component: () => import('@/views/site/inspection/detail.vue'),
        meta: { title: '检查详情', hidden: true }
      }
    ]
  },
  // 投标管理
  {
    path: '/tender',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/tender/register',
    meta: { title: '投标管理', icon: 'Trophy', permission: 'tender:view' },
    children: [
      // ===== V2026_121 二级菜单收敛：投标报名与证件合 1 入口 =====
      {
        path: 'hub/tender-group',
        name: 'TenderHubMain',
        component: () => import('@/components/HubPage.vue'),
        meta: {
          title: '投标管理', icon: 'EditPen', group: '投标揽件', permission: 'tender:view',
          hubTabs: [
            { key: 'register', label: '投标项目管理', loader: () => import('@/views/tender/register.vue') },
            { key: 'certificate', label: '投标证件管理', loader: () => import('@/views/tender/certificate.vue') }
          ]
        }
      },
      {
        path: 'register',
        name: 'TenderRegister',
        component: () => import('@/views/tender/register.vue'),
        meta: { title: '投标项目管理', icon: 'EditPen', group: '投标揽件', hidden: true }
      },
      {
        path: 'certificate',
        name: 'TenderCertificate',
        component: () => import('@/views/tender/certificate.vue'),
        meta: { title: '投标证件管理', icon: 'Postcard', group: '投标揽件', hidden: true }
      }
    ]
  },
  // 行政人事
  {
    path: '/hr',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/hr/entry',
    meta: { title: '行政人事', icon: 'School', permission: 'hr:view' },
    children: [
      {
        path: 'statistics',
        name: 'HrStatistics',
        component: () => import('@/views/hr/statistics.vue'),
        meta: { title: '人事统计', icon: 'DataAnalysis' }
      },
      {
        path: 'entry',
        name: 'HrEntry',
        component: () => import('@/views/hr/entry.vue'),
        meta: { title: '入职申请', icon: 'Plus' }
      },
      {
        path: 'office-supply',
        name: 'OfficeSupply',
        component: () => import('@/views/hr/office-supply.vue'),
        meta: { title: '办公用品', icon: 'Goods' }
      },
      {
        path: 'vehicle',
        name: 'Vehicle',
        component: () => import('@/views/hr/vehicle.vue'),
        meta: { title: '车辆管理', icon: 'Van' }
      },
      {
        path: 'resign-apply',
        name: 'ResignApply',
        component: () => import('@/views/hr/resign-apply.vue'),
        meta: { title: '离职申请', icon: 'RemoveFilled' }
      }
    ]
  },
  // 档案管理
  {
    path: '/archive',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/archive/index',
    meta: { title: '档案管理', icon: 'FolderOpened', permission: 'archive:view' },
    children: [
      {
        path: 'index',
        name: 'ArchiveIndex',
        component: () => import('@/views/archive/index.vue'),
        meta: { title: '档案查询', icon: 'Search' }
      },
      {
        path: 'other-income-contract',
        name: 'ArchiveOtherIncomeContract',
        component: () => import('@/views/archive/other-income-contract.vue'),
        meta: { title: '其它收入合同档案', icon: 'Document' }
      },
      {
        path: 'other-expense-contract',
        name: 'ArchiveOtherExpenseContract',
        component: () => import('@/views/archive/other-expense-contract.vue'),
        meta: { title: '其它支出合同档案', icon: 'Document' }
      },
      {
        path: 'office-supply',
        name: 'ArchiveOfficeSupply',
        component: () => import('@/views/archive/office-supply.vue'),
        meta: { title: '办公用品档案', icon: 'Box' }
      }
    ]
  },
  // 平台管理
  {
    path: '/platform',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/platform/tenant',
    meta: { title: '平台管理', icon: 'Platform', permission: 'system:view' },
    children: [
      {
        path: 'tenant',
        name: 'PlatformTenant',
        component: () => import('@/views/platform/tenant/index.vue'),
        meta: { title: '租户管理', icon: 'OfficeBuilding' }
      },
      {
        path: 'tenant-type',
        name: 'PlatformTenantType',
        component: () => import('@/views/platform/tenant-type/index.vue'),
        meta: { title: '用户类型', icon: 'Collection' }
      },
      {
        path: 'storage',
        name: 'PlatformStorage',
        component: () => import('@/views/platform/storage/index.vue'),
        meta: { title: '存储管理', icon: 'FolderOpened' }
      }
    ]
  },
  // 工作流管理
  {
    path: '/workflow',
    component: () => import('@/layouts/DefaultLayout.vue'),
    // 默认落点按权限分发：有流程管理码进设计器，仅审批码进审批中心
    redirect: () => {
      const permissions = useUserStore().permissions || []
      return permissions.includes('*:*:*') || permissions.includes('workflow:view')
        ? '/workflow/designer'
        : '/workflow/approval'
    },
    meta: { title: '工作流管理', icon: 'Share', permission: ['workflow:view', 'workflow:approval:view'] },
    children: [
      {
        path: 'designer',
        name: 'WorkflowDesigner',
        component: () => import('@/views/workflow/designer/index.vue'),
        meta: { title: '流程设计器', icon: 'EditPen', permission: 'workflow:view' }
      },
      {
        path: 'process',
        name: 'WorkflowProcess',
        component: () => import('@/views/workflow/process/index.vue'),
        meta: { title: '流程定义', icon: 'Document', permission: 'workflow:view' }
      },
      {
        path: 'business-type',
        name: 'WorkflowBusinessType',
        component: () => import('@/views/workflow/business-type/index.vue'),
        meta: { title: '业务类型', icon: 'Collection', permission: 'workflow:view' }
      },
      {
        path: 'approval',
        name: 'WorkflowApproval',
        component: () => import('@/views/workflow/approval/index.vue'),
        // 业务角色凭细粒度审批码进入；既有 workflow:view 角色保持兼容
        meta: { title: '审批管理', icon: 'Checked', permission: ['workflow:approval:view', 'workflow:view'] }
      },
      {
        path: 'rollback',
        name: 'WorkflowRollback',
        component: () => import('@/views/workflow/rollback/index.vue'),
        meta: { title: '审批回滚', icon: 'RefreshLeft', permission: 'workflow:view' }
      }
    ]
  },
  // 消息管理
  {
    path: '/message',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/message/notice',
    meta: { title: '消息管理', icon: 'Bell', permission: 'message:view' },
    children: [
      {
        path: 'notice',
        name: 'MessageNotice',
        component: () => import('@/views/message/notice/index.vue'),
        meta: { title: '通知管理', icon: 'Notification' }
      },
      {
        path: 'announcement',
        name: 'MessageAnnouncement',
        component: () => import('@/views/message/announcement/index.vue'),
        meta: { title: '公告管理', icon: 'ChatDotSquare' }
      },
      {
        path: 'push-config',
        name: 'PushConfig',
        component: () => import('@/views/message/push-config/index.vue'),
        meta: { title: '推送渠道配置', icon: 'Connection' }
      },
      {
        path: 'center',
        name: 'MessageCenter',
        component: () => import('@/views/message/center/index.vue'),
        meta: { title: '消息中心', icon: 'Message' }
      }
    ]
  },
  // 基础数据
  {
    path: '/basedata',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/basedata/material',
    meta: { title: '基础数据', icon: 'Grid', permission: 'basedata:view' },
    children: [
      {
        path: 'material',
        name: 'BaseMaterial',
        component: () => import('@/views/basedata/material.vue'),
        meta: { title: '材料字典', icon: 'Document' }
      },
      {
        path: 'supplier',
        name: 'BaseSupplier',
        component: () => import('@/views/basedata/supplier.vue'),
        meta: { title: '供应商', icon: 'OfficeBuilding' }
      },
      {
        path: 'owner',
        name: 'BaseOwner',
        component: () => import('@/views/basedata/owner.vue'),
        meta: { title: '甲方单位', icon: 'Star' }
      },
      {
        path: 'company',
        name: 'BaseCompany',
        component: () => import('@/views/basedata/company.vue'),
        meta: { title: '自持公司', icon: 'HomeFilled' }
      },
      {
        path: 'inspection-scheme',
        name: 'BaseInspectionScheme',
        component: () => import('@/views/basedata/inspection-scheme.vue'),
        meta: { title: '检查方案', icon: 'Checked' }
      },
      {
        path: 'supplier-evaluation',
        name: 'BaseSupplierEvaluation',
        component: () => import('@/views/basedata/supplier-evaluation.vue'),
        meta: { title: '供应商评价', icon: 'Star' }
      },
      {
        path: 'supplier-blacklist',
        name: 'BaseSupplierBlacklist',
        component: () => import('@/views/basedata/supplier-blacklist.vue'),
        meta: { title: '供应商黑名单', icon: 'CloseBold' }
      }
    ]
  },
  // 帮助中心（Phase 1.3：登录即可访问，不进侧边菜单，由顶栏 ? 图标与命令面板进入）
  {
    path: '/help',
    component: () => import('@/layouts/DefaultLayout.vue'),
    meta: { title: '帮助中心', hidden: true },
    children: [
      {
        path: '',
        name: 'HelpCenter',
        component: () => import('@/views/help/index.vue'),
        meta: { title: '帮助中心', hidden: true }
      },
      {
        path: 'guide',
        name: 'HelpGuide',
        component: () => import('@/views/help/guide.vue'),
        meta: { title: '系统使用文档', hidden: true }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', hidden: true }
  },
  // 组件画廊（纯内部页：令牌/图标/组件/资产可视化目录，hidden 不入菜单，URL 直达）
  {
    path: '/dev/gallery',
    name: 'DevGallery',
    component: () => import('@/views/dev/gallery.vue'),
    meta: { title: '组件画廊', hidden: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', hidden: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    meta: { hidden: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes
})

/** 不需要登录即可访问的白名单路径 */
const whiteList = ['/login', '/forgot-password', '/403', '/404']

// 路由守卫
router.beforeEach((to, _from, next) => {
  NProgress.start()
  let token = localStorage.getItem('token')
  let userStore: any = null
  try {
    userStore = useUserStore()
    if (userStore?.token) token = userStore.token
  } catch {}

  // 未登录：非白名单页面跳转登录页
  if (!token) {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next('/login')
    }
    return
  }

  // 已登录但访问登录页：重定向首页
  if (to.path === '/login') {
    next('/')
    return
  }

  // 路由 meta.permission 权限检查
  // 如果路由定义了 meta.permission，校验用户是否拥有该权限
  const requiredPermission = to.meta?.permission as string | string[] | undefined
  if (requiredPermission && userStore) {
    const userPermissions = userStore.permissions || []

    // 超级管理员拥有全部权限
    if (userPermissions.includes('*:*:*')) {
      next()
      return
    }

    const requiredList: string[] = Array.isArray(requiredPermission)
      ? requiredPermission
      : [requiredPermission]

    const hasPermission = requiredList.some((perm) =>
      userPermissions.includes(perm)
    )

    if (!hasPermission) {
      next('/403')
      return
    }
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

// 捕获新版本发布后旧客户端拉取过期 Chunk 失败异常（ChunkLoadError / Failed to fetch dynamically imported module）
router.onError((error, to) => {
  NProgress.done()
  const errorMsg = error?.message || String(error)
  const pattern = /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i
  if (pattern.test(errorMsg)) {
    const targetPath = to?.fullPath || window.location.pathname
    const key = `reload_chunk_${targetPath}`
    const lastReload = Number(sessionStorage.getItem(key) || '0')
    const now = Date.now()
    // 防抖：10 秒内只自动重载一次，避免由于真实网络不可达引发死循环
    if (now - lastReload > 10000) {
      sessionStorage.setItem(key, String(now))
      console.warn('[Router] 检测到系统已发布新版本，自动重载以加载最新静态资源:', targetPath)
      window.location.href = targetPath
      return
    }
  }
})

export default router
