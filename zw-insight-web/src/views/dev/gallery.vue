<template>
  <div class="gallery-container">
    <header class="gallery-header">
      <h1>组件画廊（内部）</h1>
      <p>
        设计令牌、图标、通用组件与资产的可视化目录。改样式前先来这里对照——
        风格一致性靠「改前看一眼」。数据全部取自运行时真实源（CSS 变量 / 图标 registry / 组件自身）。
      </p>
    </header>

    <!-- 1. 颜色令牌 -->
    <section class="gallery-section">
      <h2>1. 颜色令牌</h2>
      <div class="token-grid">
        <div v-for="t in colorTokens" :key="t.name" class="token-card">
          <div class="token-swatch" :style="{ background: t.value }" />
          <code class="token-name">{{ t.name }}</code>
          <span class="token-value">{{ t.value }}</span>
        </div>
      </div>
    </section>

    <!-- 2. 字体与字号 -->
    <section class="gallery-section">
      <h2>2. 字体与字号</h2>
      <div class="type-list">
        <div v-for="s in typeSamples" :key="s.label" class="type-row">
          <span class="type-meta">{{ s.label }}<code>{{ s.token }}</code></span>
          <span :style="s.style">中维智营 ZwInsight 0123456789</span>
        </div>
      </div>
    </section>

    <!-- 3. 图标注册表 -->
    <section class="gallery-section">
      <h2>3. 图标注册表（EP 兼容层，{{ iconNames.length }} 枚）</h2>
      <div class="icon-grid">
        <div v-for="n in iconNames" :key="n" class="icon-cell" :title="n">
          <el-icon :size="20"><component :is="n" /></el-icon>
          <span>{{ n }}</span>
        </div>
      </div>
    </section>

    <!-- 4. 通用组件样例 -->
    <section class="gallery-section">
      <h2>4. 通用组件</h2>

      <h3>4.1 空状态 ZwEmptyState</h3>
      <div class="demo-panel">
        <ZwEmptyState description="暂无结算单，审批通过后自动生成" />
      </div>

      <h3>4.2 数字翻牌 ZwCountUp</h3>
      <div class="demo-panel demo-countup">
        <div class="demo-kpi">
          <span class="demo-kpi-label">合同总额(万)</span>
          <ZwCountUp class="demo-kpi-value" :value="countUpValue" :format="wan" />
        </div>
        <div class="demo-kpi">
          <span class="demo-kpi-label">已收款(万)</span>
          <ZwCountUp class="demo-kpi-value" :value="countUpValue * 0.66" :format="wan" />
        </div>
        <el-button @click="countUpValue = countUpValue >= 9000 ? 1234 : countUpValue + 2345">变化数值</el-button>
      </div>

      <h3>4.3 骨架屏 ZwSkeleton（四形态）</h3>
      <div class="demo-panel">
        <h4>cards</h4>
        <ZwSkeleton variant="cards" :count="4" />
        <h4>table</h4>
        <ZwSkeleton variant="table" :rows="4" :cols="6" />
        <h4>chart</h4>
        <ZwSkeleton variant="chart" />
        <h4>detail</h4>
        <ZwSkeleton variant="detail" :rows="6" />
      </div>

      <h3>4.4 按钮与反馈</h3>
      <div class="demo-panel demo-row">
        <el-button type="primary">主要操作</el-button>
        <el-button>常规</el-button>
        <el-button type="warning">警示</el-button>
        <el-button type="danger">危险</el-button>
        <el-tag>标签</el-tag>
        <el-tag type="success">通过</el-tag>
        <el-tag type="danger">拦截</el-tag>
      </div>
    </section>

    <!-- 5. 资产目录 -->
    <section class="gallery-section">
      <h2>5. 内置图片资产（public/ + assets）</h2>
      <div class="asset-grid">
        <figure v-for="a in assets" :key="a.src" class="asset-card">
          <img :src="a.src" :alt="a.name" loading="lazy" />
          <figcaption>{{ a.name }}</figcaption>
        </figure>
      </div>
      <p class="gallery-note">插画/品牌图缺口与补齐方案见 docs/前端资产方案-插画与图片.md</p>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 组件画廊（内部 /dev/gallery，hidden 不入菜单）。
 * 目的：令牌/图标/组件/资产一页可视化，杜绝样式漂移；数据取运行时真实值。
 */
import { ref, computed, onMounted } from 'vue'
import * as icons from '@/components/icons/registry'
import ZwEmptyState from '@/components/ZwEmptyState.vue'
import ZwCountUp from '@/components/ZwCountUp.vue'
import ZwSkeleton from '@/components/ZwSkeleton.vue'
import emptyBlueprint from '@/assets/empty-blueprint.png'
import err403 from '@/assets/err-403.png'
import err404 from '@/assets/err-404.png'
import logo from '@/assets/logo.png'

const css = getComputedStyle(document.documentElement)
const readVar = (name: string) => css.getPropertyValue(name).trim()

const COLOR_TOKENS = [
  '--zw-brand', '--zw-brand-hover', '--zw-brand-active', '--zw-brand-light', '--zw-brand-lighter',
  '--zw-success', '--zw-warning', '--zw-danger', '--zw-info',
  '--zw-text-primary', '--zw-text-secondary', '--zw-text-quaternary',
  '--zw-border-light', '--zw-bg-page', '--zw-bg-card, --zw-bg-page',
]

const colorTokens = computed(() =>
  COLOR_TOKENS.map((name) => ({ name: name.split(',')[0], value: readVar(name.split(',')[0]) || '(未定义)' }))
)

const typeSamples = [
  { label: 'Display', token: '--zw-font-display', style: { fontFamily: readVar('--zw-font-display') || 'inherit', fontSize: '22px', fontWeight: 600 } },
  { label: 'lg', token: '--zw-font-size-lg', style: { fontSize: readVar('--zw-font-size-lg') || '18px' } },
  { label: 'md', token: '--zw-font-size-md', style: { fontSize: readVar('--zw-font-size-md') || '16px' } },
  { label: 'base', token: '--zw-font-size-base', style: { fontSize: readVar('--zw-font-size-base') || '14px' } },
  { label: 'sm', token: '--zw-font-size-sm', style: { fontSize: readVar('--zw-font-size-sm') || '13px' } },
  { label: 'mono', token: '--zw-font-mono', style: { fontFamily: readVar('--zw-font-mono') || 'monospace', fontSize: '14px' } },
]

const iconNames = Object.keys(icons).filter((k) => /^[A-Z]/.test(k)).sort()

const countUpValue = ref(4520.5)
const wan = (n: number) => (n / 1).toFixed(1)

const assets = [
  { src: emptyBlueprint, name: 'empty-blueprint（AI 空白图纸空态）' },
  { src: err403, name: 'err-403' },
  { src: err404, name: 'err-404' },
  { src: logo, name: 'logo' },
]

onMounted(() => {
  void ref(0)
})
</script>

<style scoped>
.gallery-container {
  padding: var(--zw-space-lg);
  max-width: 1200px;
  margin: 0 auto;
}

.gallery-header h1 {
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-lg);
  color: var(--zw-text-primary);
}

.gallery-header p {
  color: var(--zw-text-secondary);
  font-size: var(--zw-font-size-sm);
  max-width: 720px;
}

.gallery-section {
  margin-top: var(--zw-space-2xl);
}

.gallery-section h2 {
  font-size: var(--zw-font-size-lg);
  color: var(--zw-text-primary);
  border-bottom: 1px solid var(--zw-border-light);
  padding-bottom: var(--zw-space-xs);
}

.gallery-section h3 {
  margin: var(--zw-space-lg) 0 var(--zw-space-sm);
  font-size: var(--zw-font-size-md);
  color: var(--zw-text-primary);
}

.gallery-section h4 {
  margin: var(--zw-space-md) 0 var(--zw-space-xs);
  color: var(--zw-text-secondary);
  font-size: var(--zw-font-size-sm);
}

.token-grid,
.icon-grid,
.asset-grid {
  display: grid;
  gap: var(--zw-space-md);
}

.token-grid {
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
}

.token-card {
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  overflow: hidden;
  background: var(--zw-bg-page);
}

.token-swatch {
  height: 44px;
}

.token-name {
  display: block;
  padding: var(--zw-space-sm) var(--zw-space-sm) 0;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-primary);
  word-break: break-all;
}

.token-value {
  display: block;
  padding: 0 var(--zw-space-sm) var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
}

.type-row {
  display: flex;
  align-items: baseline;
  gap: var(--zw-space-lg);
  padding: var(--zw-space-sm) 0;
  border-bottom: 1px dashed var(--zw-border-light);
}

.type-meta {
  width: 200px;
  flex-shrink: 0;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
}

.type-meta code {
  margin-left: var(--zw-space-xs);
}

.icon-grid {
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
}

.icon-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--zw-space-xs);
  padding: var(--zw-space-sm);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-xs);
  color: var(--zw-text-secondary);
}

.icon-cell span {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.demo-panel {
  border: 1px dashed var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  padding: var(--zw-space-lg);
  background: var(--zw-bg-page);
}

.demo-row {
  display: flex;
  align-items: center;
  gap: var(--zw-space-md);
  flex-wrap: wrap;
}

.demo-countup {
  display: flex;
  gap: var(--zw-space-2xl);
  align-items: center;
  flex-wrap: wrap;
}

.demo-kpi-label {
  display: block;
  color: var(--zw-text-quaternary);
  font-size: var(--zw-font-size-xs);
}

.demo-kpi-value {
  font-size: var(--zw-font-size-xl);
  font-weight: 600;
  color: var(--zw-text-primary);
  font-variant-numeric: tabular-nums;
}

.asset-grid {
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
}

.asset-card {
  margin: 0;
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  overflow: hidden;
  background: var(--zw-bg-page);
}

.asset-card img {
  width: 100%;
  height: 110px;
  object-fit: contain;
  background: var(--zw-bg-page);
}

.asset-card figcaption {
  padding: var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-secondary);
}

.gallery-note {
  margin-top: var(--zw-space-sm);
  color: var(--zw-text-quaternary);
  font-size: var(--zw-font-size-xs);
}
</style>
