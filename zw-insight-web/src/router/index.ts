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
        meta: { title: '首页', icon: 'HomeFilled' }
      },
      {
        path: 'project-dashboard',
        name: 'ProjectDashboard',
        component: () => import('@/views/dashboard/project-dashboard.vue'),
        meta: { title: '项目看板', icon: 'DataAnalysis' }
      }
    ]
  },
  // 系统管理
  {
    path: '/system',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/system/org',
    meta: { title: '系统管理', icon: 'Setting' },
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
    redirect: '/project/list',
    meta: { title: '项目管理', icon: 'Briefcase' },
    children: [
      {
        path: 'list',
        name: 'ProjectList',
        component: () => import('@/views/project/index.vue'),
        meta: { title: '项目报备', icon: 'Document' }
      },
      {
        path: 'create',
        name: 'ProjectCreate',
        component: () => import('@/views/project/form.vue'),
        meta: { title: '新增项目', hidden: true }
      },
      {
        path: 'edit/:id',
        name: 'ProjectEdit',
        component: () => import('@/views/project/form.vue'),
        meta: { title: '编辑项目', hidden: true }
      },
      {
        path: 'detail/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/detail.vue'),
        meta: { title: '项目详情', hidden: true }
      }
    ]
  },
  // 合同管理
  {
    path: '/contract',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/contract/list',
    meta: { title: '合同管理', icon: 'Notebook' },
    children: [
      {
        path: 'list',
        name: 'ContractList',
        component: () => import('@/views/contract/index.vue'),
        meta: { title: '施工合同', icon: 'Document' }
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
      }
    ]
  },
  // 财务管理
  {
    path: '/finance',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/finance/invoice-apply',
    meta: { title: '财务管理', icon: 'Money' },
    children: [
      {
        path: 'invoice-apply',
        name: 'InvoiceApply',
        component: () => import('@/views/finance/invoice-apply.vue'),
        meta: { title: '开票申请', icon: 'Ticket' }
      },
      {
        path: 'invoice-received',
        name: 'InvoiceReceived',
        component: () => import('@/views/finance/invoice-received.vue'),
        meta: { title: '收票登记', icon: 'Tickets' }
      },
      {
        path: 'payment-received',
        name: 'PaymentReceived',
        component: () => import('@/views/finance/payment-received.vue'),
        meta: { title: '回款登记', icon: 'WalletFilled' }
      },
      {
        path: 'payment-apply',
        name: 'PaymentApply',
        component: () => import('@/views/finance/payment-apply.vue'),
        meta: { title: '付款申请', icon: 'CreditCard' }
      },
      {
        path: 'settlement',
        name: 'SettlementList',
        component: () => import('@/views/finance/settlement/index.vue'),
        meta: { title: '项目最终结算', icon: 'Money' }
      },
      {
        path: 'finance-lock',
        name: 'FinanceLock',
        component: () => import('@/views/finance/finance-lock/index.vue'),
        meta: { title: '财务封账', icon: 'Lock' }
      },
      {
        path: 'tax-rate',
        name: 'TaxRate',
        component: () => import('@/views/finance/tax-rate/index.vue'),
        meta: { title: '税率管理', icon: 'Histogram' }
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
    redirect: '/budget/list',
    meta: { title: '预算管理', icon: 'Coin' },
    children: [
      {
        path: 'list',
        name: 'BudgetList',
        component: () => import('@/views/budget/index.vue'),
        meta: { title: '预算编制', icon: 'Document' }
      },
      {
        path: 'change',
        name: 'BudgetChangeList',
        component: () => import('@/views/budget/change/index.vue'),
        meta: { title: '目标成本变更', icon: 'Switch' }
      },
      {
        path: 'change/form',
        name: 'BudgetChangeForm',
        component: () => import('@/views/budget/change/form.vue'),
        meta: { title: '变更单表单', hidden: true }
      },
      {
        path: 'config',
        name: 'BudgetConfig',
        component: () => import('@/views/budget/config.vue'),
        meta: { title: '预算配置', icon: 'Setting' }
      },
      {
        path: 'control-config',
        name: 'BudgetControlConfig',
        component: () => import('@/views/budget/control-config/index.vue'),
        meta: { title: '预算控制配置', icon: 'Setting' }
      }
    ]
  },
  // 采购管理
  {
    path: '/purchase',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/purchase/contract',
    meta: { title: '采购管理', icon: 'ShoppingCart' },
    children: [
      {
        path: 'contract',
        name: 'PurchaseContract',
        component: () => import('@/views/purchase/contract.vue'),
        meta: { title: '采购合同', icon: 'Document' }
      },
      {
        path: 'settlement',
        name: 'PurchaseSettlement',
        component: () => import('@/views/purchase/settlement.vue'),
        meta: { title: '采购结算', icon: 'Tickets' }
      },
      {
        path: 'inquiry',
        name: 'PurchaseInquiry',
        component: () => import('@/views/purchase/inquiry.vue'),
        meta: { title: '询价比价', icon: 'DataAnalysis' }
      }
    ]
  },
  // 劳务管理
  {
    path: '/labor',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/labor/contract',
    meta: { title: '劳务管理', icon: 'Avatar' },
    children: [
      {
        path: 'contract',
        name: 'LaborContract',
        component: () => import('@/views/labor/contract.vue'),
        meta: { title: '劳务合同', icon: 'Document' }
      },
      {
        path: 'team',
        name: 'LaborTeam',
        component: () => import('@/views/labor/team.vue'),
        meta: { title: '班组管理', icon: 'UserFilled' }
      },
      {
        path: 'roster',
        name: 'LaborRoster',
        component: () => import('@/views/labor/roster.vue'),
        meta: { title: '劳务花名册', icon: 'List' }
      },
      {
        path: 'work-order',
        name: 'LaborWorkOrder',
        component: () => import('@/views/labor/work-order.vue'),
        meta: { title: '用工单', icon: 'Memo' }
      },
      {
        path: 'payroll',
        name: 'LaborPayroll',
        component: () => import('@/views/labor/payroll.vue'),
        meta: { title: '工资单', icon: 'Wallet' }
      },
      {
        path: 'salary-stats',
        name: 'LaborSalaryStats',
        component: () => import('@/views/labor/salary/stats.vue'),
        meta: { title: '薪资统计', icon: 'DataAnalysis' }
      }
    ]
  },
  // 材料库存
  {
    path: '/material',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/material/inbound',
    meta: { title: '材料库存', icon: 'Box' },
    children: [
      {
        path: 'inbound',
        name: 'MaterialInbound',
        component: () => import('@/views/material/inbound.vue'),
        meta: { title: '到货入库', icon: 'Download' }
      },
      {
        path: 'outbound',
        name: 'MaterialOutbound',
        component: () => import('@/views/material/outbound.vue'),
        meta: { title: '领料出库', icon: 'Upload' }
      },
      {
        path: 'transfer',
        name: 'MaterialTransfer',
        component: () => import('@/views/material/transfer.vue'),
        meta: { title: '材料调拨', icon: 'Switch' }
      },
      {
        path: 'stock',
        name: 'MaterialStock',
        component: () => import('@/views/material/stock.vue'),
        meta: { title: '库存查询', icon: 'Search' }
      }
    ]
  },
  // 机械管理
  {
    path: '/machine',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/machine/contract',
    meta: { title: '机械管理', icon: 'Van' },
    children: [
      {
        path: 'contract',
        name: 'MachineContract',
        component: () => import('@/views/machine/contract.vue'),
        meta: { title: '机械合同', icon: 'Document' }
      },
      {
        path: 'ledger',
        name: 'MachineLedger',
        component: () => import('@/views/machine/ledger.vue'),
        meta: { title: '机械台账', icon: 'Notebook' }
      },
      {
        path: 'entry',
        name: 'MachineEntry',
        component: () => import('@/views/machine/entry.vue'),
        meta: { title: '进出场登记', icon: 'MapLocation' }
      },
      {
        path: 'work-log',
        name: 'MachineWorkLog',
        component: () => import('@/views/machine/work-log.vue'),
        meta: { title: '台班/工作量', icon: 'Timer' }
      },
      {
        path: 'repair',
        name: 'MachineRepair',
        component: () => import('@/views/machine/repair.vue'),
        meta: { title: '故障维修', icon: 'WarnTriangleFilled' }
      },
      {
        path: 'settlement',
        name: 'MachineSettlement',
        component: () => import('@/views/machine/settlement/index.vue'),
        meta: { title: '机械结算', icon: 'Tickets' }
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
    redirect: '/subcontract/contract',
    meta: { title: '分包管理', icon: 'Connection' },
    children: [
      {
        path: 'contract',
        name: 'SubContract',
        component: () => import('@/views/subcontract/contract.vue'),
        meta: { title: '分包合同', icon: 'Document' }
      },
      {
        path: 'settlement',
        name: 'SubSettlement',
        component: () => import('@/views/subcontract/settlement.vue'),
        meta: { title: '分包结算', icon: 'Tickets' }
      }
    ]
  },
  // 现场管理
  {
    path: '/site',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/site/schedule',
    meta: { title: '现场管理', icon: 'Place' },
    children: [
      {
        path: 'schedule',
        name: 'SiteSchedule',
        component: () => import('@/views/site/schedule.vue'),
        meta: { title: '进度计划', icon: 'DataLine' }
      },
      {
        path: 'construction-log',
        name: 'ConstructionLog',
        component: () => import('@/views/site/construction-log.vue'),
        meta: { title: '施工日志', icon: 'Memo' }
      },
      {
        path: 'inspection',
        name: 'SiteInspection',
        component: () => import('@/views/site/inspection/index.vue'),
        meta: { title: '质量安全检查', icon: 'CircleCheck' }
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
    meta: { title: '投标管理', icon: 'Trophy' },
    children: [
      {
        path: 'register',
        name: 'TenderRegister',
        component: () => import('@/views/tender/register.vue'),
        meta: { title: '投标报名', icon: 'EditPen' }
      },
      {
        path: 'certificate',
        name: 'TenderCertificate',
        component: () => import('@/views/tender/certificate.vue'),
        meta: { title: '证件管理', icon: 'Postcard' }
      }
    ]
  },
  // 行政人事
  {
    path: '/hr',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/hr/entry',
    meta: { title: '行政人事', icon: 'School' },
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
      }
    ]
  },
  // 档案管理
  {
    path: '/archive',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/archive/index',
    meta: { title: '档案管理', icon: 'FolderOpened' },
    children: [
      {
        path: 'index',
        name: 'ArchiveIndex',
        component: () => import('@/views/archive/index.vue'),
        meta: { title: '档案查询', icon: 'Search' }
      }
    ]
  },
  // 工作流管理
  {
    path: '/workflow',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/workflow/designer',
    meta: { title: '工作流管理', icon: 'Share' },
    children: [
      {
        path: 'designer',
        name: 'WorkflowDesigner',
        component: () => import('@/views/workflow/designer/index.vue'),
        meta: { title: '流程设计器', icon: 'EditPen' }
      },
      {
        path: 'process',
        name: 'WorkflowProcess',
        component: () => import('@/views/workflow/process/index.vue'),
        meta: { title: '流程定义', icon: 'Document' }
      },
      {
        path: 'business-type',
        name: 'WorkflowBusinessType',
        component: () => import('@/views/workflow/business-type/index.vue'),
        meta: { title: '业务类型', icon: 'Collection' }
      },
      {
        path: 'approval',
        name: 'WorkflowApproval',
        component: () => import('@/views/workflow/approval/index.vue'),
        meta: { title: '审批管理', icon: 'Checked' }
      },
      {
        path: 'rollback',
        name: 'WorkflowRollback',
        component: () => import('@/views/workflow/rollback/index.vue'),
        meta: { title: '审批回滚', icon: 'RefreshLeft' }
      }
    ]
  },
  // 消息管理
  {
    path: '/message',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/message/notice',
    meta: { title: '消息管理', icon: 'Bell' },
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
    meta: { title: '基础数据', icon: 'Grid' },
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
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', hidden: true }
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
  const userStore = useUserStore()
  const token = userStore.token || localStorage.getItem('token')

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
  if (requiredPermission) {
    const userPermissions = userStore.permissions

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

export default router
