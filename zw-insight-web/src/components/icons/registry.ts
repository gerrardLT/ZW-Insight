/**
 * EP→Tabler 图标映射注册表（存量迁移层）
 * 保留 Element Plus 原名作为全局组件名/导出名，使模板、路由 meta.icon、
 * 后端菜单种子字符串零改动切换到 Tabler 底层实现。
 * 新页面请直接使用 @tabler/icons-vue 的 IconXxx 命名。
 */
import {
  IconAlertTriangle,
  IconArrowDown,
  IconArrowLeft,
  IconBell,
  IconBellRinging,
  IconBox,
  IconBriefcase,
  IconBuilding,
  IconCash,
  IconChartBar,
  IconChartHistogram,
  IconChartLine,
  IconCheck,
  IconCircleCheck,
  IconCircleMinus,
  IconCirclePlus,
  IconClock,
  IconCloudUpload,
  IconCoin,
  IconCreditCard,
  IconDeviceDesktop,
  IconDeviceMobile,
  IconDownload,
  IconEdit,
  IconEye,
  IconFile,
  IconFileText,
  IconFiles,
  IconFolderCheck,
  IconFolderOpen,
  IconFolderPlus,
  IconGauge,
  IconHome,
  IconId,
  IconKey,
  IconLayoutGrid,
  IconLibrary,
  IconLink,
  IconList,
  IconLoader2,
  IconLock,
  IconMap,
  IconMapPin,
  IconMaximize,
  IconMedal,
  IconMenu,
  IconMessage,
  IconMessage2,
  IconMinimize,
  IconMinus,
  IconMoon,
  IconNotebook,
  IconPackage,
  IconPencil,
  IconPhoto,
  IconPlus,
  IconPower,
  IconPrinter,
  IconRefresh,
  IconRotate,
  IconRotateClockwise,
  IconRubberStamp,
  IconSchool,
  IconSearch,
  IconSettings,
  IconShare,
  IconShoppingCart,
  IconSquareCheck,
  IconStack,
  IconStar,
  IconSun,
  IconSwitch,
  IconTicket,
  IconTool,
  IconTrash,
  IconTrendingUp,
  IconTrophy,
  IconTruck,
  IconUpload,
  IconUser,
  IconUserCircle,
  IconWallet,
  IconX
} from '@tabler/icons-vue'
import type { Component } from 'vue'

export const EP_ICON_MAP: Record<string, Component> = {
  ArrowDown: IconArrowDown,
  Avatar: IconUserCircle,
  Back: IconArrowLeft,
  Bell: IconBell,
  Box: IconBox,
  Briefcase: IconBriefcase,
  ChatDotSquare: IconMessage2,
  Check: IconCheck,
  Checked: IconSquareCheck,
  CircleCheck: IconCircleCheck,
  CirclePlus: IconCirclePlus,
  Close: IconX,
  CloseBold: IconX,
  Coin: IconCoin,
  Collection: IconLibrary,
  Connection: IconLink,
  CreditCard: IconCreditCard,
  DataAnalysis: IconChartBar,
  DataLine: IconChartLine,
  Delete: IconTrash,
  Document: IconFile,
  Download: IconDownload,
  Edit: IconEdit,
  EditPen: IconPencil,
  Expand: IconMaximize,
  Files: IconFiles,
  Fold: IconMinimize,
  FolderAdd: IconFolderPlus,
  FolderChecked: IconFolderCheck,
  FolderOpened: IconFolderOpen,
  GoldMedal: IconMedal,
  Goods: IconPackage,
  Grid: IconLayoutGrid,
  Histogram: IconChartHistogram,
  HomeFilled: IconHome,
  Iphone: IconDeviceMobile,
  Key: IconKey,
  List: IconList,
  Loading: IconLoader2,
  Lock: IconLock,
  MapLocation: IconMap,
  Memo: IconFileText,
  Menu: IconMenu,
  Message: IconMessage,
  Money: IconCash,
  Monitor: IconDeviceDesktop,
  Moon: IconMoon,
  Notebook: IconNotebook,
  Notification: IconBellRinging,
  Odometer: IconGauge,
  OfficeBuilding: IconBuilding,
  Picture: IconPhoto,
  Place: IconMapPin,
  Platform: IconStack,
  Plus: IconPlus,
  Postcard: IconId,
  Printer: IconPrinter,
  Refresh: IconRefresh,
  RefreshLeft: IconRotate,
  RefreshRight: IconRotateClockwise,
  Remove: IconMinus,
  RemoveFilled: IconCircleMinus,
  School: IconSchool,
  Search: IconSearch,
  Setting: IconSettings,
  Share: IconShare,
  ShoppingCart: IconShoppingCart,
  Stamp: IconRubberStamp,
  Star: IconStar,
  Sunny: IconSun,
  Switch: IconSwitch,
  SwitchButton: IconPower,
  Ticket: IconTicket,
  Tickets: IconTicket,
  Timer: IconClock,
  Tools: IconTool,
  TrendCharts: IconTrendingUp,
  Trophy: IconTrophy,
  Upload: IconUpload,
  UploadFilled: IconCloudUpload,
  User: IconUser,
  UserFilled: IconUserCircle,
  Van: IconTruck,
  View: IconEye,
  Wallet: IconWallet,
  WalletFilled: IconWallet,
  WarnTriangleFilled: IconAlertTriangle,
  Warning: IconAlertTriangle,
}

// 命名导出：供显式 import { Plus } 等用法替换引用源
export const ArrowDown = IconArrowDown
export const Avatar = IconUserCircle
export const Back = IconArrowLeft
export const Bell = IconBell
export const Box = IconBox
export const Briefcase = IconBriefcase
export const ChatDotSquare = IconMessage2
export const Check = IconCheck
export const Checked = IconSquareCheck
export const CircleCheck = IconCircleCheck
export const CirclePlus = IconCirclePlus
export const Close = IconX
export const CloseBold = IconX
export const Coin = IconCoin
export const Collection = IconLibrary
export const Connection = IconLink
export const CreditCard = IconCreditCard
export const DataAnalysis = IconChartBar
export const DataLine = IconChartLine
export const Delete = IconTrash
export const Document = IconFile
export const Download = IconDownload
export const Edit = IconEdit
export const EditPen = IconPencil
export const Expand = IconMaximize
export const Files = IconFiles
export const Fold = IconMinimize
export const FolderAdd = IconFolderPlus
export const FolderChecked = IconFolderCheck
export const FolderOpened = IconFolderOpen
export const GoldMedal = IconMedal
export const Goods = IconPackage
export const Grid = IconLayoutGrid
export const Histogram = IconChartHistogram
export const HomeFilled = IconHome
export const Iphone = IconDeviceMobile
export const Key = IconKey
export const List = IconList
export const Loading = IconLoader2
export const Lock = IconLock
export const MapLocation = IconMap
export const Memo = IconFileText
export const Menu = IconMenu
export const Message = IconMessage
export const Money = IconCash
export const Monitor = IconDeviceDesktop
export const Moon = IconMoon
export const Notebook = IconNotebook
export const Notification = IconBellRinging
export const Odometer = IconGauge
export const OfficeBuilding = IconBuilding
export const Picture = IconPhoto
export const Place = IconMapPin
export const Platform = IconStack
export const Plus = IconPlus
export const Postcard = IconId
export const Printer = IconPrinter
export const Refresh = IconRefresh
export const RefreshLeft = IconRotate
export const RefreshRight = IconRotateClockwise
export const Remove = IconMinus
export const RemoveFilled = IconCircleMinus
export const School = IconSchool
export const Search = IconSearch
export const Setting = IconSettings
export const Share = IconShare
export const ShoppingCart = IconShoppingCart
export const Stamp = IconRubberStamp
export const Star = IconStar
export const Sunny = IconSun
export const Switch = IconSwitch
export const SwitchButton = IconPower
export const Ticket = IconTicket
export const Tickets = IconTicket
export const Timer = IconClock
export const Tools = IconTool
export const TrendCharts = IconTrendingUp
export const Trophy = IconTrophy
export const Upload = IconUpload
export const UploadFilled = IconCloudUpload
export const User = IconUser
export const UserFilled = IconUserCircle
export const Van = IconTruck
export const View = IconEye
export const Wallet = IconWallet
export const WalletFilled = IconWallet
export const WarnTriangleFilled = IconAlertTriangle
export const Warning = IconAlertTriangle

/** 当图标名称无法解析时显示的占位符组件（显式警告配置错误，不静默隐藏） */
export const IconFallbackPlaceholder = IconAlertTriangle

/**
 * 将图标字符串解析为组件；未命中则返回占位符（后端菜单配置可能存在映射表外的图标名，显式占位而非静默处理）
 * @param name 图标名称（如 "ArrowDown" / "Warning"），可 undefined/null
 * @returns 有效图标或占位符
 */
export function resolveMenuIcon(name: string | undefined | null): Component {
  if (name && EP_ICON_MAP[name]) return EP_ICON_MAP[name]
  return IconFallbackPlaceholder
}

/** 菜单管理页的图标选择枚举选项（过滤输入防止无效图标名写入数据库） */
export const MENU_ICON_OPTIONS = Object.keys(EP_ICON_MAP).sort()
