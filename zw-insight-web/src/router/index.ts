import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
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
        path: 'config',
        name: 'BudgetConfig',
        component: () => import('@/views/budget/config.vue'),
        meta: { title: '预算配置', icon: 'Setting' }
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
        component: () => import('@/views/site/inspection.vue'),
        meta: { title: '质量安全检查', icon: 'CircleCheck' }
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
      }
    ]
  },
  {
    path: '/404',
    component: () => import('@/views/error/404.vue'),
    meta: { hidden: true }
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

// 路由守卫
router.beforeEach((to, from, next) => {
  NProgress.start()
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') {
    next('/login')
  } else {
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
