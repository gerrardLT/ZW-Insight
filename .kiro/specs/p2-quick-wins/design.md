# Design Document — P2 Quick Wins

## Overview

P2 优先级快速功能集合，包含 4 项独立的体验增强功能：数据脱敏（后端全新实现）、甘特图前端接入确认（微改造）、批量启用/停用用户（前端改造）、可视化编号规则管理（前端新建页面）。这些功能后端接口大多已就绪，主要工作集中在后端 Jackson 脱敏层和前端页面对接。

## Architecture

本功能集包含 4 项独立的体验增强功能，技术架构分为后端全新实现（数据脱敏）和前端改造/新建页面（甘特图集成确认、批量用户操作、编号规则管理）。

```
┌─────────────────────────────────────────────────────────────────┐
│                       PC Frontend (Vue 3)                        │
├──────────────┬──────────────┬───────────────┬───────────────────┤
│ schedule.vue │  user/       │ serial-number/│  (existing pages) │
│ + GanttChart │  index.vue   │ index.vue     │                   │
│ (已集成)      │ (批量操作改造) │ (新建页面)     │                   │
└──────┬───────┴──────┬───────┴───────┬───────┴───────────────────┘
       │              │               │
       ▼              ▼               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    REST API Layer (Spring Boot 3.2)              │
├──────────────────────────────────────────────────────────────────┤
│  Jackson Serialization Layer                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ @Desensitize + DesensitizeSerializer (ContextualSerializer)│    │
│  │ → 响应序列化阶段自动脱敏                                    │    │
│  └─────────────────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────────────────┤
│  SysUserController        │ SerialNumberController              │
│  PUT /v1/system/user/     │ /api/v1/file/serial (CRUD+generate) │
│  batch-status (已有)       │ (已有)                              │
└──────────────────────────────────────────────────────────────────┘
```

## Technology Stack

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | Spring Boot | 3.2.6 |
| 后端 ORM | MyBatis-Plus | 3.5.5 |
| 后端序列化 | Jackson (spring-boot-starter-web 内置) | — |
| 前端框架 | Vue 3 + TypeScript | 3.4.x |
| UI 库 | Element Plus | 2.6.x |
| 甘特图 | dhtmlx-gantt | 8.0.6 |
| 状态管理 | Pinia | 2.1.x |
| 构建工具 | Vite | 5.2.x |

---

## Components and Interfaces

### Component 1: 数据脱敏（后端）

#### 1.1 模块结构

新增代码位于 `zw-common` 模块（全局可用），包路径 `com.zwinsight.common.desensitize`：

```
zw-common/src/main/java/com/zwinsight/common/desensitize/
├── Desensitize.java              // 自定义注解
├── DesensitizeType.java          // 脱敏类型枚举
├── DesensitizeSerializer.java    // Jackson ContextualSerializer
└── DesensitizeUtil.java          // 脱敏工具类（纯函数）
```

#### 1.2 注解定义

```java
package com.zwinsight.common.desensitize;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = DesensitizeSerializer.class)
public @interface Desensitize {
    DesensitizeType type();
}
```

#### 1.3 脱敏类型枚举

```java
package com.zwinsight.common.desensitize;

public enum DesensitizeType {
    /** 手机号：保留前3后4，中间4个星号 */
    PHONE(3, 4, 7),
    /** 身份证号：保留前3后4，中间星号 */
    ID_CARD(3, 4, 7),
    /** 银行卡号：保留前4后4，中间星号 */
    BANK_CARD(4, 4, 8),
    /** 邮箱：保留首字母+@+域名 */
    EMAIL(0, 0, 3),
    /** 地址：保留前6字符，其余星号 */
    ADDRESS(6, 0, 6);

    /** 前缀保留长度 */
    private final int prefixLen;
    /** 后缀保留长度 */
    private final int suffixLen;
    /** 最低掩码要求长度 */
    private final int minLen;

    DesensitizeType(int prefixLen, int suffixLen, int minLen) {
        this.prefixLen = prefixLen;
        this.suffixLen = suffixLen;
        this.minLen = minLen;
    }

    // getters...
}
```

#### 1.4 ContextualSerializer 实现

```java
package com.zwinsight.common.desensitize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class DesensitizeSerializer extends StdSerializer<String>
    implements ContextualSerializer {

    private DesensitizeType type;

    protected DesensitizeSerializer() { super(String.class); }

    protected DesensitizeSerializer(DesensitizeType type) {
        super(String.class);
        this.type = type;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
        throws JsonMappingException {
        if (property != null) {
            Desensitize ann = property.getAnnotation(Desensitize.class);
            if (ann == null) {
                ann = property.getContextAnnotation(Desensitize.class);
            }
            if (ann != null) {
                return new DesensitizeSerializer(ann.type());
            }
        }
        return prov.findValueSerializer(property.getType(), property);
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider)
        throws java.io.IOException {
        gen.writeString(DesensitizeUtil.desensitize(value, type));
    }
}
```

#### 1.5 脱敏工具类（纯函数，核心逻辑）

```java
package com.zwinsight.common.desensitize;

public final class DesensitizeUtil {

    private DesensitizeUtil() {}

    /**
     * 根据脱敏类型对字符串执行掩码处理
     * @param value 原始值
     * @param type 脱敏类型
     * @return 脱敏后的字符串
     */
    public static String desensitize(String value, DesensitizeType type) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        return switch (type) {
            case PHONE -> maskPhone(value);
            case ID_CARD -> maskIdCard(value);
            case BANK_CARD -> maskBankCard(value);
            case EMAIL -> maskEmail(value);
            case ADDRESS -> maskAddress(value);
        };
    }

    private static String maskPhone(String value) {
        if (value.length() < 7) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static String maskIdCard(String value) {
        if (value.length() < 7) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 3)
            + "*".repeat(value.length() - 7)
            + value.substring(value.length() - 4);
    }

    private static String maskBankCard(String value) {
        if (value.length() < 8) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 4)
            + "*".repeat(value.length() - 8)
            + value.substring(value.length() - 4);
    }

    private static String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex < 1) {
            return "*".repeat(value.length());
        }
        String username = value.substring(0, atIndex);
        String domain = value.substring(atIndex);
        if (username.length() <= 1) {
            return username + domain;
        }
        return username.charAt(0) + "*".repeat(username.length() - 1) + domain;
    }

    private static String maskAddress(String value) {
        if (value.length() <= 6) {
            return value;
        }
        return value.substring(0, 6) + "*".repeat(value.length() - 6);
    }
}
```

#### 1.6 使用示例

在需要脱敏的实体 VO/DTO 字段上标注注解即可全局生效：

```java
public class SysUserVO {
    private String username;
    private String realName;

    @Desensitize(type = DesensitizeType.PHONE)
    private String phone;

    @Desensitize(type = DesensitizeType.EMAIL)
    private String email;

    @Desensitize(type = DesensitizeType.ID_CARD)
    private String idCard;
}
```

---

### Component 2: 甘特图前端集成确认

#### 2.1 当前状态

经代码审查确认：

- `GanttChart.vue` 组件已完整实现（dhtmlx-gantt 封装、时间轴缩放、关键路径、拖拽保存）
- `schedule.vue` 页面已集成 `GanttChart` 组件，支持项目选择后渲染甘特图
- 路由 `/site/schedule` 已注册
- API `getSchedulePlanTree` 和 `updateSchedulePlanDates` 已就绪

#### 2.2 待确认/补完项

1. **ProjectSelector 集成**：当前 `ganttProjectId` 从列表第一条数据中提取，应改为使用 `ProjectSelector` 组件让用户主动选择项目
2. **空状态处理**：当前使用 `v-else` 显示"请先选择项目以查看甘特图"，需确保当选择项目后数据为空时显示"暂无进度计划数据"

#### 2.3 改造方案

```vue
<!-- schedule.vue 中甘特图区域改造 -->
<el-card shadow="never" style="margin-top: 16px">
  <template #header>
    <div style="display: flex; align-items: center; gap: 12px;">
      <span>甘特图视图</span>
      <ProjectSelector v-model="ganttProjectId" placeholder="选择项目" style="width: 240px" />
    </div>
  </template>
  <GanttChart
    v-if="ganttProjectId && ganttHasData"
    ref="ganttRef"
    :project-id="ganttProjectId"
    :editable="true"
    @task-updated="handleGanttTaskUpdated"
  />
  <el-empty v-else-if="ganttProjectId && !ganttHasData" description="暂无进度计划数据" />
  <el-empty v-else description="请先选择项目以查看甘特图" />
</el-card>
```

---

### Component 3: 批量启用/停用用户（前端）

#### 3.1 当前状态

经代码审查确认，`user/index.vue` 页面**已实现**批量启用/停用功能：

- ✅ 表格首列有 `type="selection"` 多选框
- ✅ 工具栏有"批量启用"/"批量停用"按钮，绑定 `:disabled="!selectedIds.length"`
- ✅ `handleSelectionChange` 收集选中 ID
- ✅ `handleBatchStatus(status)` 调用 `batchUpdateUserStatus(ids, status)`
- ✅ 操作前弹出 ElMessageBox.confirm 确认

#### 3.2 待补完项

1. **排除当前用户**：当前 `handleBatchStatus` 未排除当前登录用户自身
2. **确认对话框显示选中数量**：当前显示"确定要批量启用/停用选中用户吗？"，需改为"确认启用选中的 N 个用户？"
3. **API 路径对齐**：前端调用 `PUT /v1/system/user/batch-status`，需求文档指定为 `PUT /v1/system/user/status`，需确认后端实际路径

#### 3.3 改造方案

```typescript
// user/index.vue - handleBatchStatus 改造
import { useUserStore } from '@/stores/user'

async function handleBatchStatus(status: number) {
  const userStore = useUserStore()
  const currentUserId = userStore.userId

  // 排除当前登录用户
  const filteredIds = selectedIds.value.filter(id => id !== currentUserId)
  if (filteredIds.length === 0) {
    ElMessage.warning('不能对自己执行此操作')
    return
  }

  const action = status === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(
    `确认${action}选中的 ${filteredIds.length} 个用户？`,
    '提示',
    { type: 'warning' }
  )

  await batchUpdateUserStatus(filteredIds, status)
  ElMessage.success(`批量${action}成功`)
  loadData()
}
```

---

### Component 4: 可视化编号规则管理（前端新建页面）

#### 4.1 文件结构

```
zw-insight-web/src/
├── api/
│   └── file.ts                      // 新增：编号规则 API
├── views/system/
│   └── serial-number/
│       └── index.vue                // 新增：编号规则管理页面
└── router/
    └── index.ts                     // 修改：新增路由
```

#### 4.2 API 层接口定义

```typescript
// api/file.ts
import request from '@/utils/request'

export interface SerialNumberRule {
  id?: number
  businessType: string
  rulePrefix: string
  dateFormat: string
  seqLength: number
  resetPeriod: string
  description?: string
}

/** 获取编号规则列表 */
export function getSerialNumberList() {
  return request.get<any, any>('/api/v1/file/serial')
}

/** 新增编号规则 */
export function createSerialNumber(data: SerialNumberRule) {
  return request.post('/api/v1/file/serial', data)
}

/** 更新编号规则 */
export function updateSerialNumber(id: number, data: SerialNumberRule) {
  return request.put(`/api/v1/file/serial/${id}`, data)
}

/** 删除编号规则 */
export function deleteSerialNumber(id: number) {
  return request.delete(`/api/v1/file/serial/${id}`)
}

/** 预览生成编号 */
export function generateSerialNumber(businessType: string) {
  return request.post<any, any>(`/api/v1/file/serial/generate/${businessType}`)
}
```

#### 4.3 路由注册

在 `router/index.ts` 的 `/system` children 数组中新增：

```typescript
{
  path: 'serial-number',
  name: 'SerialNumberManage',
  component: () => import('@/views/system/serial-number/index.vue'),
  meta: { title: '编号规则管理', icon: 'Odometer' }
}
```

#### 4.4 页面组件设计

```
┌────────────────────────────────────────────────────────────┐
│ 编号规则管理                                                │
├────────────────────────────────────────────────────────────┤
│ [+ 新增]                                                   │
├────────────────────────────────────────────────────────────┤
│ 业务类型 │ 规则前缀 │ 日期格式 │ 序号长度 │ 重置周期 │ 描述 │ 操作     │
│ CONTRACT │ HT      │ yyyyMMdd │ 4       │ MONTH   │ ... │ 预览|编辑|删除│
│ INVOICE  │ FP      │ yyyyMM   │ 5       │ YEAR    │ ... │ 预览|编辑|删除│
├────────────────────────────────────────────────────────────┤
│                                                            │
└────────────────────────────────────────────────────────────┘

┌─── 新增/编辑对话框 ─────────────────────────────────────────┐
│ 业务类型*: [__________]  (字母/数字/下划线, ≤50字符)         │
│ 规则前缀*: [__________]  (≤20字符)                          │
│ 日期格式*: [yyyyMMdd ▼]  (yyyyMMdd / yyyyMM / yyyy)        │
│ 序号长度*: [___4___]     (1-10 整数)                        │
│ 重置周期*: [MONTH   ▼]  (MONTH / YEAR)                     │
│ 描述:      [__________]                                     │
│                              [取消] [确定]                   │
└─────────────────────────────────────────────────────────────┘
```

#### 4.5 前端表单校验规则

```typescript
const formRules = {
  businessType: [
    { required: true, message: '请输入业务类型', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{1,50}$/,
      message: '仅允许字母、数字和下划线，长度不超过50',
      trigger: 'blur'
    }
  ],
  rulePrefix: [
    { required: true, message: '请输入规则前缀', trigger: 'blur' },
    { max: 20, message: '长度不超过20字符', trigger: 'blur' }
  ],
  dateFormat: [
    { required: true, message: '请选择日期格式', trigger: 'change' }
  ],
  seqLength: [
    { required: true, message: '请输入序号长度', trigger: 'blur' },
    { type: 'number', min: 1, max: 10, message: '序号长度为1-10的整数', trigger: 'blur' }
  ],
  resetPeriod: [
    { required: true, message: '请选择重置周期', trigger: 'change' }
  ]
}
```

---

## Data Models

### 数据脱敏 — 无新增数据库表

仅在已有实体的 VO/DTO 字段上添加 `@Desensitize` 注解，不改变数据库结构。

### 编号规则 — 已有表 `serial_number_rule`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（雪花算法） |
| tenant_id | BIGINT | 租户ID |
| business_type | VARCHAR(50) | 业务类型标识 |
| rule_prefix | VARCHAR(20) | 编号前缀 |
| date_format | VARCHAR(20) | 日期格式 |
| seq_length | INT | 序号长度 |
| reset_period | VARCHAR(10) | 重置周期(MONTH/YEAR) |
| description | VARCHAR(200) | 描述 |
| created_by | BIGINT | 创建人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| deleted | TINYINT | 逻辑删除标识 |
| version | INT | 乐观锁版本号 |

---

## Error Handling

| 场景 | 处理方式 |
|------|---------|
| 脱敏字段为 null/空 | 原样返回，不抛异常 |
| 脱敏字段长度不足 | 全部替换为等长星号，不抛异常 |
| 甘特图数据加载失败 | ElMessage.error 提示，甘特图区域保持空状态 |
| 甘特图拖拽保存失败 | ElMessage.error 提示，重新加载数据恢复原状 |
| 批量操作 API 返回失败 | ElMessage.error 显示错误信息，列表不刷新 |
| 批量操作选中包含当前用户 | 过滤排除当前用户后提交，若过滤后为空则 warning 提示 |
| 编号规则 CRUD 接口失败 | ElMessage.error 提示，对话框保持打开 |
| 编号预览生成失败 | ElMessage.error 提示 |
| 前端表单校验不通过 | 阻止提交，字段下方显示红色校验提示 |

---

## Testing Strategy

| 功能模块 | 测试类型 | 说明 |
|---------|---------|------|
| DesensitizeUtil | Property-based test (JUnit 5 + jqwik) | 对每种脱敏类型生成随机输入验证掩码规则 |
| DesensitizeSerializer | Unit test | 验证 Jackson 序列化集成正确拦截注解字段 |
| 甘特图集成 | Example-based test | 验证页面组件引用和数据流 |
| 批量启用/停用 | Unit test + Property test | 验证排除当前用户逻辑 |
| 编号规则页面 | Unit test + Property test | 验证前端表单校验规则 |
| 全链路脱敏 | Integration test | 请求带脱敏字段的接口验证响应已掩码 |

**Property-based test 配置**：最少 100 次迭代，使用 jqwik（后端已引入，见 `.jqwik-database` 文件）。

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: 脱敏掩码格式正确性

*For any* desensitize type and any input string whose length meets or exceeds the minimum masking requirement for that type, the `DesensitizeUtil.desensitize(value, type)` result SHALL satisfy:
- PHONE: `result == value[0:3] + "****" + value[-4:]`
- ID_CARD: `result == value[0:3] + "*".repeat(len-7) + value[-4:]`
- BANK_CARD: `result == value[0:4] + "*".repeat(len-8) + value[-4:]`
- EMAIL: `result == value[0] + "*".repeat(usernameLen-1) + "@" + domain`
- ADDRESS: `result == value[0:6] + "*".repeat(len-6)`

**Validates: Requirements 1.4, 1.5, 1.6, 1.7, 1.8**

### Property 2: 短输入全星号替代

*For any* desensitize type and any non-empty input string whose length is strictly less than the minimum masking requirement for that type, `DesensitizeUtil.desensitize(value, type)` SHALL return a string of asterisks with length equal to `value.length()`.

**Validates: Requirements 1.10**

### Property 3: 空值脱敏恒等性

*For any* desensitize type, `DesensitizeUtil.desensitize(null, type)` SHALL return null, and `DesensitizeUtil.desensitize("", type)` SHALL return `""`.

**Validates: Requirements 1.9**

### Property 4: 批量操作排除当前用户

*For any* user selection list that contains the current logged-in user's ID, the IDs submitted to the batch status update API SHALL NOT include the current user's ID, and the submitted list length SHALL be `selectedIds.length - 1`.

**Validates: Requirements 3.9**

### Property 5: 编号规则表单校验 — businessType 合法性

*For any* string that contains characters other than `[a-zA-Z0-9_]` or has length exceeding 50, the form validation for `businessType` field SHALL reject the input and prevent form submission.

**Validates: Requirements 4.11**

### Property 6: 编号规则表单校验 — seqLength 范围

*For any* numeric value outside the range [1, 10] (including non-integer values), the form validation for `seqLength` field SHALL reject the input and prevent form submission.

**Validates: Requirements 4.11**

---

## 菜单数据初始化

需在数据库菜单表中插入编号规则管理的菜单记录，或通过 `data-menu.sql` 添加：

```sql
-- 编号规则管理菜单（归属系统管理）
INSERT INTO sys_menu (id, parent_id, menu_name, path, component, menu_type, icon, sort_order, visible, permission)
VALUES (/*新ID*/, /*系统管理父ID*/, '编号规则管理', 'serial-number', 'system/serial-number/index', 'C', 'Odometer', 10, 1, 'system:serial:list');
```

---

## 实施依赖关系

```
数据脱敏（后端独立）
    └── 无前置依赖，可独立开发

甘特图前端确认（前端微改造）
    └── 依赖：GanttChart.vue（已完成）、schedule.vue（已集成）
    └── 改造点：ProjectSelector + 空状态判断

批量启用/停用（前端改造）
    └── 依赖：batchUpdateUserStatus API（已有）
    └── 改造点：排除当前用户 + 确认文案优化

编号规则管理（前端新建）
    └── 依赖：SerialNumberController（已有）
    └── 新建：API 层 + 路由 + 页面组件
```

四项功能无互相依赖，可并行开发。
