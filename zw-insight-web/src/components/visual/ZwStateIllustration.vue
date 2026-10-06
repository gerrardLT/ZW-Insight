<!--
  ZwStateIllustration — 品牌化状态插图（蓝图语言 v2，2026-10-05 插画级重绘）
  9 态专属 SVG（160×120 横版插画级，替代 v1 的 64px 图标级）：
    data(空账本) / search(无结果) / error(警示) / offline(断连) / permission(挂锁)
    settlement(暂无结算) / fund(无资金计划) / approval(无待办) / blueprint(空白图纸)
  纪律（DESIGN Iconography L667/L675）：线条恒中性 --zw-text-quaternary（strong/普通/低透明三层分级），
  单一 --zw-brand 橙点缀（一图一处）；形状区分状态（色盲安全）；
  内联 SVG 零硬编码 hex（颜色经 CSS 类注入，随主题/品牌主色换肤联动）。
  共用签名元素：四角制图 registration marks（.ill-mark，替代 v1 的整幅虚线底板——
  v2 各态已有主形体，整幅外框会与场景形体重复拥挤）。
-->
<template>
  <svg
    class="zw-illust"
    :class="`zw-illust--${type}`"
    :style="{ width: size }"
    viewBox="0 0 160 120"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    role="img"
    :aria-label="ariaLabel"
  >
    <!-- 制图角标（registration marks，蓝图语言签名元素，9 态共用） -->
    <g class="ill-mark">
      <path d="M8 4 v8 M4 8 h8" stroke-width="1" />
      <path d="M152 4 v8 M148 8 h8" stroke-width="1" />
      <path d="M8 108 v8 M4 112 h8" stroke-width="1" />
      <path d="M152 108 v8 M148 112 h8" stroke-width="1" />
    </g>

    <!-- data：空账本 —— 图纸 + 数据行 + 趋势虚影 + 橙游标 -->
    <g v-if="type === 'data'">
      <rect class="ill-line" x="38" y="18" width="84" height="86" rx="3" stroke-width="1.5" />
      <line class="ill-line-strong" x1="38" y1="34" x2="122" y2="34" stroke-width="1.5" />
      <line class="ill-line" x1="50" y1="48" x2="110" y2="48" stroke-width="2" opacity="0.55" />
      <line class="ill-line" x1="50" y1="60" x2="96" y2="60" stroke-width="2" opacity="0.35" />
      <line class="ill-line" x1="50" y1="72" x2="104" y2="72" stroke-width="2" opacity="0.22" />
      <path class="ill-line" d="M50 92 L68 80 L84 86 L106 72" stroke-width="1.5" stroke-dasharray="3 3" opacity="0.5" />
      <circle class="ill-accent-f" cx="106" cy="72" r="3" />
    </g>

    <!-- search：无结果 —— 放大镜 + 空白页 + 橙问号 -->
    <g v-else-if="type === 'search'">
      <rect class="ill-line" x="30" y="16" width="62" height="80" rx="3" stroke-width="1.5" />
      <line class="ill-line" x1="42" y1="32" x2="80" y2="32" stroke-width="1.5" opacity="0.4" />
      <line class="ill-line" x1="42" y1="44" x2="72" y2="44" stroke-width="1.5" opacity="0.25" />
      <circle class="ill-line-strong" cx="104" cy="66" r="20" stroke-width="2" />
      <line class="ill-line-strong" x1="118" y1="80" x2="132" y2="94" stroke-width="2.5" stroke-linecap="round" />
      <path class="ill-accent-s" d="M99 58 a6.5 6.5 0 1 1 6.5 6.5 v3" stroke-width="2" stroke-linecap="round" />
      <circle class="ill-accent-f" cx="105.5" cy="71.5" r="1.6" />
    </g>

    <!-- error：警示 —— 图纸 + 三角 + 橙感叹号 -->
    <g v-else-if="type === 'error'">
      <rect class="ill-line" x="42" y="14" width="76" height="92" rx="3" stroke-width="1.5" opacity="0.45" />
      <line class="ill-line" x1="54" y1="28" x2="106" y2="28" stroke-width="1.5" opacity="0.3" />
      <path class="ill-line-strong" d="M80 38 L106 86 L54 86 Z" stroke-width="2" stroke-linejoin="round" />
      <line class="ill-accent-s" x1="80" y1="56" x2="80" y2="70" stroke-width="2.5" stroke-linecap="round" />
      <circle class="ill-accent-f" cx="80" cy="77" r="1.8" />
    </g>

    <!-- offline：断连 —— 云 + 断开插头 + 橙闪电 -->
    <g v-else-if="type === 'offline'">
      <path
        class="ill-line-strong"
        d="M44 62 h40 a12 12 0 0 0 1.4 -24 a19 19 0 0 0 -36.4 -3 a12.8 12.8 0 0 0 -5 27 z"
        stroke-width="2" stroke-linejoin="round"
      />
      <path class="ill-line" d="M56 74 v14 M72 74 v14" stroke-width="2" stroke-linecap="round" opacity="0.6" />
      <path class="ill-line" d="M50 88 h12 M66 88 h12" stroke-width="2" stroke-linecap="round" opacity="0.6" />
      <path class="ill-accent-s" d="M108 52 l-8 12 h7 l-6 12 l14 -15 h-7 l6 -9 z" stroke-width="1.8" stroke-linejoin="round" />
    </g>

    <!-- permission：挂锁 —— 门框 + 橙锁孔 -->
    <g v-else-if="type === 'permission'">
      <path class="ill-line" d="M60 22 v76 M100 22 v76" stroke-width="1.5" opacity="0.35" />
      <line class="ill-line" x1="48" y1="22" x2="112" y2="22" stroke-width="1.5" opacity="0.35" />
      <rect class="ill-line-strong" x="66" y="52" width="28" height="24" rx="3" stroke-width="2" />
      <path class="ill-line-strong" d="M72 52 v-6 a8 8 0 0 1 16 0 v6" stroke-width="2" />
      <circle class="ill-accent-f" cx="80" cy="62" r="2.6" />
      <line class="ill-accent-s" x1="80" y1="64.5" x2="80" y2="69" stroke-width="2" stroke-linecap="round" />
    </g>

    <!-- settlement：暂无结算 —— 计算纸 + 橙对勾框 -->
    <g v-else-if="type === 'settlement'">
      <rect class="ill-line" x="30" y="16" width="70" height="88" rx="3" stroke-width="1.5" />
      <line class="ill-line" x1="30" y1="34" x2="100" y2="34" stroke-width="1.5" />
      <g class="ill-line" stroke-width="1.5" opacity="0.4">
        <line x1="40" y1="46" x2="90" y2="46" />
        <line x1="40" y1="58" x2="78" y2="58" />
        <line x1="40" y1="70" x2="86" y2="70" />
      </g>
      <path class="ill-line" d="M40 90 h44" stroke-width="1.5" stroke-dasharray="4 3" opacity="0.5" />
      <rect class="ill-line-strong" x="108" y="62" width="26" height="26" rx="4" stroke-width="2" />
      <path class="ill-accent-s" d="M115 75 l5 5 l10 -11" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
    </g>

    <!-- fund：无资金计划 —— 钱包 + 日历 + 橙硬币 -->
    <g v-else-if="type === 'fund'">
      <rect class="ill-line" x="34" y="40" width="60" height="44" rx="5" stroke-width="1.8" />
      <path class="ill-line-strong" d="M34 54 h60" stroke-width="1.8" />
      <circle class="ill-accent-f" cx="80" cy="68" r="4.5" />
      <rect class="ill-line" x="74" y="16" width="52" height="40" rx="3" stroke-width="1.5" opacity="0.55" />
      <line class="ill-line" x1="74" y1="27" x2="126" y2="27" stroke-width="1.5" opacity="0.55" />
      <g class="ill-line" opacity="0.35" stroke-width="1.5">
        <path d="M86 21 v-4 M114 21 v-4" />
        <line x1="82" y1="35" x2="90" y2="35" />
        <line x1="98" y1="35" x2="106" y2="35" />
        <line x1="82" y1="44" x2="90" y2="44" />
      </g>
    </g>

    <!-- approval：无待办 —— 清单 + 橙对勾 -->
    <g v-else-if="type === 'approval'">
      <rect class="ill-line" x="34" y="20" width="70" height="72" rx="4" stroke-width="1.5" />
      <line class="ill-line" x1="34" y1="36" x2="104" y2="36" stroke-width="1.5" />
      <g class="ill-line" stroke-width="1.5" opacity="0.45">
        <circle cx="46" cy="48" r="3" />
        <line x1="56" y1="48" x2="92" y2="48" />
        <circle cx="46" cy="62" r="3" />
        <line x1="56" y1="62" x2="84" y2="62" />
      </g>
      <circle class="ill-accent-s" cx="46" cy="78" r="7" stroke-width="1.8" />
      <path class="ill-accent-s" d="M42.8 78 l2.4 2.4 l4.6 -5" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      <path class="ill-line" d="M112 92 l0 12 M112 98 l8 0" stroke-width="1.5" opacity="0.4" />
    </g>

    <!-- blueprint：空白图纸 —— 制图板 + 绘笔 + 橙笔尖（替代旧 PNG bespoke） -->
    <g v-else>
      <rect class="ill-line-strong" x="30" y="24" width="88" height="72" rx="2" stroke-width="1.8" />
      <rect class="ill-line" x="38" y="32" width="72" height="56" stroke-width="1" stroke-dasharray="4 3" opacity="0.5" />
      <line class="ill-line" x1="38" y1="32" x2="110" y2="88" stroke-width="1" opacity="0.25" />
      <line class="ill-line" x1="110" y1="32" x2="38" y2="88" stroke-width="1" opacity="0.25" />
      <g class="ill-line-strong" stroke-width="1.8">
        <path d="M126 44 v34" stroke-linecap="round" />
        <path d="M126 78 l-5 8 h10 z" stroke-linejoin="round" />
      </g>
      <circle class="ill-accent-f" cx="126" cy="40" r="3" />
      <path class="ill-line" d="M22 52 h-8 M18 48 v8" stroke-width="1.2" opacity="0.4" />
    </g>
  </svg>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 状态/场景类型（决定插图形状）；前 4 态与 ZwEmptyState type 对齐，后 5 个为业务场景 */
    type?: 'data' | 'search' | 'error' | 'offline' | 'permission' | 'settlement' | 'fund' | 'approval' | 'blueprint'
    /** 渲染宽度（高度按 4:3 视图比例自适应） */
    size?: string
    /** 无障碍标签 */
    ariaLabel?: string
  }>(),
  { type: 'data', size: '160px', ariaLabel: '状态插图' }
)
</script>

<style scoped>
.zw-illust {
  display: block;
  height: auto;
}

/* 颜色经 CSS 类注入（presentation attribute 不支持 var()）：线条恒中性，单一橙点缀 */
.ill-mark {
  stroke: var(--zw-text-quaternary);
  opacity: 0.4;
}

.ill-line {
  stroke: var(--zw-text-quaternary);
}

.ill-line-strong {
  stroke: var(--zw-text-quaternary);
  opacity: 0.85;
}

.ill-accent-s {
  stroke: var(--zw-brand);
}

.ill-accent-f {
  fill: var(--zw-brand);
}
</style>
