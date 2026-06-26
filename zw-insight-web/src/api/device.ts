import request from '@/utils/request'

/**
 * 登录设备管理 API
 * 后端：DeviceController（zw-security 模块），映射 /api/v1/user/devices
 * 响应统一为 R<T>，由 request 拦截器返回 R 对象，业务层取 res.data
 */

/**
 * 登录设备 VO
 * 对应后端 com.zwinsight.security.dto.LoginDeviceVO
 */
export interface LoginDevice {
  /** 设备记录ID（注销时使用） */
  id: number
  /** 设备名称 */
  deviceName: string
  /** 操作系统 */
  os: string
  /** 登录IP */
  ipAddress: string
  /** IP归属地（省份|城市） */
  location: string
  /** 登录时间 */
  loginTime: string
  /** 最后活跃时间 */
  lastActiveTime: string
  /** 状态：1=活跃 0=已注销 */
  status: number
  /** 是否为当前登录设备 */
  isCurrent: boolean
}

/**
 * 查询当前登录用户的活跃设备列表
 * GET /api/v1/user/devices/list
 * 响应：R<List<LoginDeviceVO>> → res.data
 */
export function getLoginDevices() {
  return request.get('/v1/user/devices/list')
}

/**
 * 远程注销指定设备（使其 Token 失效）
 * DELETE /api/v1/user/devices/{deviceId}
 * 注意：deviceId 为设备记录 ID（LoginDevice.id），禁止注销当前设备（后端校验）
 */
export function revokeLoginDevice(deviceId: number) {
  return request.delete(`/v1/user/devices/${deviceId}`)
}
