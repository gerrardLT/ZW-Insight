<template>
  <!--
    ZwiEmptyState 四变体空态（Stage 0.3 → Batch 1.3，2026-09-17）
    - 契约 class：.empty（31 页面既有纯文字空态的升级位）
    - data: 无数据（空账本插图 + 可选 CTA——空态只教一个动作）
    - error: 加载失败（警示三角插图 + 重试 CTA）
    - offline: 离线缓存过期（断连云插图 + 提示联网）
    - permission: 无权限（挂锁插图 + 联系管理员提示）
    插图：4 态专属蓝图 SVG（形状区分状态，非颜色区分）；随 prefers-color-scheme 双层切换亮/暗。
  -->
  <view class="empty" :class="`empty-${type}`">
    <!-- 双插画随主题显示（与项目纯 CSS 暗色机制同构，零 JS 切换；.empty-light/.empty-dark 由媒体查询互斥） -->
    <image class="empty-illustration empty-light" :src="illustLight" mode="aspectFit" />
    <image class="empty-illustration empty-dark" :src="illustDark" mode="aspectFit" />
    <text class="empty-text">{{ text }}</text>
    <view v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'ZwiEmptyState' })

const props = withDefaults(
  defineProps<{
    /** 空态类型 */
    type?: 'data' | 'error' | 'offline' | 'permission'
    /** 描述文案（未传时按类型给方向性默认，"给方向不道歉"） */
    description?: string
  }>(),
  { type: 'data', description: '' }
)

const DEFAULT_TEXT: Record<string, string> = {
  data: '暂无数据',
  error: '加载失败，请稍后重试',
  offline: '离线状态下无缓存数据，联网后自动同步',
  permission: '暂无访问权限，请联系管理员',
}

const text = computed(() => props.description || DEFAULT_TEXT[props.type])

/* ===== 4 态专属蓝图插图（data-URI SVG）=====
 * 跨端约束（uni-app 官方）：小程序 <image> 仅支持「网络地址」SVG，data-URI SVG 不渲染；
 * 故 mp-weixin 经下方条件编译回落既有品牌 PNG（零回归），H5/App 走 data-URI SVG（4 态专属 + 主题化）。
 * 遗留：mp-weixin 4 态专属插图待生成 4×2 品牌 PNG 或真机核验后接入（见 design-system-migration 记录）。
 * 色值镜像 tokens.css（data-URI SVG 无法读 CSS 变量，只能烘焙字面色，改动须与 token 同步）：
 *   NEUTRAL_LIGHT = --zw-text-quaternary(亮) #6b727d；NEUTRAL_DARK = --zw-text-quaternary(暗) #5a5f66；
 *   ACCENT = --zw-brand #ff6b00（双主题恒定）。图标恒中性、单一橙点缀（DESIGN L667/L675）。 */
const NEUTRAL_LIGHT = '#6b727d'
const NEUTRAL_DARK = '#5a5f66'
const ACCENT = '#ff6b00'

function buildIllust(type: string, neutral: string): string {
  const frame = `<rect x="10" y="10" width="44" height="44" rx="2" fill="none" stroke="${neutral}" stroke-width="1.5" stroke-dasharray="4 3"/>`
  let glyph: string
  switch (type) {
    case 'error':
      glyph =
        `<path d="M32 21 L45 44 L19 44 Z" fill="none" stroke="${neutral}" stroke-width="1.5" stroke-linejoin="round"/>` +
        `<line x1="32" y1="30" x2="32" y2="37" stroke="${ACCENT}" stroke-width="2" stroke-linecap="round"/>` +
        `<circle cx="32" cy="41" r="1.4" fill="${ACCENT}"/>`
      break
    case 'offline':
      glyph =
        `<path d="M23 41 h17 a5.2 5.2 0 0 0 0.6 -10.4 a8.4 8.4 0 0 0 -16 -1.3 a5.6 5.6 0 0 0 -1.6 11.7 z" fill="none" stroke="${neutral}" stroke-width="1.5" stroke-linejoin="round"/>` +
        `<line x1="20" y1="20" x2="44" y2="44" stroke="${ACCENT}" stroke-width="2" stroke-linecap="round"/>`
      break
    case 'permission':
      glyph =
        `<rect x="24" y="30" width="16" height="14" rx="2" fill="none" stroke="${neutral}" stroke-width="1.5"/>` +
        `<path d="M27.5 30 v-4 a4.5 4.5 0 0 1 9 0 v4" fill="none" stroke="${neutral}" stroke-width="1.5"/>` +
        `<circle cx="32" cy="37" r="1.8" fill="${ACCENT}"/>`
      break
    default:
      glyph =
        `<line x1="19" y1="24" x2="45" y2="24" stroke="${neutral}" stroke-width="1.5" opacity="0.65"/>` +
        `<line x1="19" y1="33" x2="45" y2="33" stroke="${neutral}" stroke-width="1.5" opacity="0.4"/>` +
        `<line x1="19" y1="42" x2="37" y2="42" stroke="${neutral}" stroke-width="1.5" opacity="0.25"/>` +
        `<circle cx="44" cy="42" r="2.5" fill="${ACCENT}"/>`
  }
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none">${frame}${glyph}</svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

// 平台开关：默认 PNG（小程序/App 安全兜底）；H5 构建期条件编译置真走 data-URI SVG。
// 注：移动端 vitest 用 @vitejs/plugin-vue（无 uni 预处理），#ifdef 注释惰性→下方赋值恒执行→测试取 SVG 分支；
// 而 mp-weixin/App 构建会剔除 #ifdef H5 块→useSvgIllust 留 false→PNG（零回归）。
let useSvgIllust = false
// #ifdef H5
useSvgIllust = true
// #endif

const illustLight = computed(() =>
  useSvgIllust ? buildIllust(props.type, NEUTRAL_LIGHT) : '/static/brand/empty-blueprint.png'
)
const illustDark = computed(() =>
  useSvgIllust ? buildIllust(props.type, NEUTRAL_DARK) : '/static/brand/empty-blueprint-dark.png'
)
</script>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 40rpx;
}
.empty-illustration {
  width: 240rpx;
  height: 240rpx;
  opacity: 0.9;
}
.empty-dark {
  display: none;
}
@media (prefers-color-scheme: dark) {
  .empty-light {
    display: none;
  }
  .empty-dark {
    display: block;
  }
}
.empty-text {
  font-size: 26rpx;
  color: var(--zw-text-tertiary);
  text-align: center;
  line-height: 1.6;
  margin-top: 8rpx;
}
.empty-action {
  margin-top: 24rpx;
  min-height: 44px;
  display: flex;
  align-items: center;
}
</style>
