# ip2region 离线 IP 地址库

`LoginLocationService`（异地登录检测）依赖 ip2region 的二进制数据文件 `ip2region.xdb`
进行 IP 归属地的**本地离线**解析，不依赖任何外部 API。

## 运维须知：必须放置 xdb 数据文件

该二进制数据文件**不随源码提交到仓库**（体积较大且会定期更新），需由运维手动提供：

1. 从官方仓库下载最新数据文件：
   - 仓库：https://github.com/lionsoul2014/ip2region
   - 文件：`data/ip2region.xdb`
2. 放置到本目录：
   ```
   zw-security/src/main/resources/ip2region/ip2region.xdb
   ```
   或放置到任意外部路径，并通过配置项指向它。

## 配置项

`LoginLocationService` 通过以下配置读取数据文件路径（支持 `classpath:` 与 `file:` 前缀）：

```yaml
ip2region:
  # 默认从 classpath 加载；也可指向外部绝对路径
  xdb-path: classpath:ip2region/ip2region.xdb
  # 示例：使用外部文件（推荐生产环境，便于热更新数据）
  # xdb-path: file:/opt/zwinsight/ip2region/ip2region.xdb
```

## 容错行为（需求 9.4）

- 若 xdb 文件缺失或加载失败：应用**正常启动**，仅记录 WARN 日志；
- 此时 `resolveLocation(ip)` 返回 `null`，`detectAndNotify(...)` 不发送通知，
  **登录流程不受影响、不被阻断**。

## 数据格式

ip2region 查询返回格式为 `国家|区域|省份|城市|ISP`，
`LoginLocationService.resolveLocation` 会提取并拼接为 `省份|城市`
（字段为空或占位符 `0` 时以 `未知` 替代）。
