<template>
  <!-- 蓝图线稿动态背景（纯 CSS/SVG + 轻量指针视差；色值 token + color-mix，过 stylelint 门禁） -->
  <div ref="root" class="blueprint-bg" aria-hidden="true">
    <!-- 活体辉光底（极缓慢漂移） -->
    <div class="plx plx-glow">
      <span class="orb orb-a"></span>
      <span class="orb orb-b"></span>
    </div>
    <!-- 精密网格（视差 + 漂移） -->
    <div class="plx plx-grid"><div class="grid"></div></div>
    <!-- 等高线（描边生长后持续漂移） -->
    <div class="plx plx-contour">
      <svg class="contour" viewBox="0 0 900 400" preserveAspectRatio="none">
        <path d="M0,360 C140,300 240,390 380,330 S620,250 760,310 S880,350 900,320"/>
        <path d="M0,320 C140,260 240,350 380,290 S620,210 760,270 S880,310 900,280"/>
        <path d="M0,280 C140,220 240,310 380,250 S620,170 760,230 S880,270 900,240"/>
        <path d="M0,240 C140,180 240,270 380,210 S620,130 760,190 S880,230 900,200"/>
        <path d="M0,200 C140,140 240,230 380,170 S620,90 760,150 S880,190 900,160"/>
      </svg>
    </div>
    <!-- 塔吊 + 在建楼体线框（呼吸 + 反向视差） -->
    <div class="plx plx-wire">
      <svg class="wire" viewBox="0 0 300 420" preserveAspectRatio="xMidYMax meet">
        <line x1="70" y1="420" x2="70" y2="60"/><line x1="70" y1="60" x2="230" y2="60"/>
        <line x1="150" y1="60" x2="150" y2="120"/><line x1="70" y1="120" x2="230" y2="120"/>
        <line class="hot" x1="150" y1="120" x2="150" y2="170"/><rect class="hot" x="140" y="170" width="20" height="16"/>
        <circle class="node" cx="150" cy="120" r="3"/><circle class="node n2" cx="70" cy="60" r="3"/><circle class="node n3" cx="230" cy="60" r="3"/>
        <rect x="180" y="200" width="110" height="220"/>
        <line x1="180" y1="240" x2="290" y2="240"/><line x1="180" y1="280" x2="290" y2="280"/>
        <line x1="180" y1="320" x2="290" y2="320"/><line x1="180" y1="360" x2="290" y2="360"/>
        <line x1="235" y1="200" x2="235" y2="420"/>
        <rect x="10" y="300" width="90" height="120"/><line x1="10" y1="340" x2="100" y2="340"/><line x1="10" y1="380" x2="100" y2="380"/>
      </svg>
    </div>
    <!-- 体积光 + 尘埃 + 扫描带 + 颗粒 + 暗角 -->
    <div class="beam"></div>
    <div class="dust"><i v-for="n in 26" :key="n" :style="dustStyle(n)"></i></div>
    <div class="scanline"></div>
    <div class="grain"></div>
    <div class="vign"></div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';
const root = ref<HTMLElement | null>(null);

function rand(i: number, mod: number) {
  const x = Math.sin(i * 999.13) * 43758.5453;
  return (x - Math.floor(x)) * mod;
}
function dustStyle(n: number) {
  return {
    left: `${rand(n, 100)}%`,
    bottom: `${-10 + rand(n + 50, 30)}%`,
    animationDuration: `${9 + rand(n + 10, 10)}s`,
    animationDelay: `${rand(n + 20, 10)}s`,
    opacity: `${0.15 + rand(n + 30, 0.4)}`,
    transform: `scale(${0.5 + rand(n + 40, 1.8)})`,
  };
}

// 指针视差：写入归一化变量 --px/--py（-1..1），各层乘不同幅度（仅 transform/opacity，GPU 友好）
let raf = 0;
function onMove(e: PointerEvent) {
  const el = root.value;
  if (!el) return;
  const r = el.getBoundingClientRect();
  const px = ((e.clientX - r.left) / r.width) * 2 - 1;
  const py = ((e.clientY - r.top) / r.height) * 2 - 1;
  if (raf) return;
  raf = requestAnimationFrame(() => {
    el.style.setProperty('--px', px.toFixed(3));
    el.style.setProperty('--py', py.toFixed(3));
    raf = 0;
  });
}
function onLeave() { root.value?.style.setProperty('--px', '0'); root.value?.style.setProperty('--py', '0'); }
const reduce = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
onMounted(() => { if (!reduce) root.value?.addEventListener('pointermove', onMove); root.value?.addEventListener('pointerleave', onLeave); });
onBeforeUnmount(() => { root.value?.removeEventListener('pointermove', onMove); root.value?.removeEventListener('pointerleave', onLeave); if (raf) cancelAnimationFrame(raf); });
</script>

<style scoped>
.blueprint-bg { position: absolute; inset: 0; overflow: hidden; z-index: 0; pointer-events: none; --px: 0; --py: 0; }

/* 视差容器：负延迟不冲突；transition 做指针平滑跟随 */
.plx { position: absolute; inset: 0; transition: transform .4s cubic-bezier(.16,1,.3,1); will-change: transform; }
.plx-glow    { transform: translate3d(calc(var(--px) * 6px),  calc(var(--py) * 6px), 0); }
.plx-grid    { transform: translate3d(calc(var(--px) * 10px), calc(var(--py) * 10px), 0); }
.plx-contour { transform: translate3d(calc(var(--px) * 16px), calc(var(--py) * 16px), 0); }
.plx-wire    { transform: translate3d(calc(var(--px) * -26px), calc(var(--py) * -18px), 0); }

/* 活体辉光：两团极缓慢漂移（transform/opacity，品牌暗调） */
.orb { position: absolute; border-radius: 50%; filter: blur(70px); opacity: .5; }
.orb-a { width: 60%; height: 55%; top: -12%; left: -8%; background: radial-gradient(circle, color-mix(in srgb, var(--zw-brand) 24%, transparent), transparent 66%); animation: bp-orbA 46s ease-in-out infinite; }
.orb-b { width: 55%; height: 50%; bottom: -6%; right: -10%; background: radial-gradient(circle, color-mix(in srgb, var(--zw-info) 20%, transparent), transparent 66%); animation: bp-orbB 58s ease-in-out infinite; }
@keyframes bp-orbA { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(28%, 18%) scale(1.15); } }
@keyframes bp-orbB { 0%,100% { transform: translate(0,0) scale(1.05); } 50% { transform: translate(-22%, -16%) scale(.92); } }

/* 精密网格缓慢视差漂移 */
.grid {
  position: absolute; inset: -40px;
  background-image:
    linear-gradient(color-mix(in srgb, var(--zw-text-inverse) 7%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--zw-text-inverse) 7%, transparent) 1px, transparent 1px);
  background-size: 30px 30px;
  -webkit-mask-image: radial-gradient(120% 90% at 40% 30%, black 25%, transparent 82%);
  mask-image: radial-gradient(120% 90% at 40% 30%, black 25%, transparent 82%);
  animation: bp-gridDrift 32s linear infinite;
}
@keyframes bp-gridDrift { from { transform: translate3d(0, 0, 0); } to { transform: translate3d(30px, 30px, 0); } }

/* 等高线：描边生长(6s) → 之后整幅持续缓慢上下漂移，永不僵死 */
.contour { position: absolute; left: -10%; bottom: 0; width: 130%; height: 64%; opacity: .5; animation: bp-contourDrift 24s ease-in-out infinite; }
.contour path { fill: none; stroke: color-mix(in srgb, var(--zw-brand) 55%, transparent); stroke-width: 1; stroke-dasharray: 1400; stroke-dashoffset: 1400; animation: bp-draw 5s ease forwards; }
.contour path:nth-child(2) { animation-delay: .4s; stroke: color-mix(in srgb, var(--zw-text-inverse) 14%, transparent); }
.contour path:nth-child(3) { animation-delay: .8s; }
.contour path:nth-child(4) { animation-delay: 1.2s; stroke: color-mix(in srgb, var(--zw-text-inverse) 10%, transparent); }
.contour path:nth-child(5) { animation-delay: 1.6s; }
@keyframes bp-draw { to { stroke-dashoffset: 0; } }
@keyframes bp-contourDrift { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-10px); } }

/* 塔吊 + 楼体线框（极缓呼吸） */
.wire { position: absolute; right: 4%; bottom: 6%; width: 46%; height: 70%; opacity: .5; transform-origin: bottom center; animation: bp-breathe 16s ease-in-out infinite; }
.wire line, .wire rect { fill: none; stroke: color-mix(in srgb, var(--zw-info) 45%, transparent); stroke-width: 1.1; vector-effect: non-scaling-stroke; }
.wire .hot { stroke: color-mix(in srgb, var(--zw-brand) 55%, transparent); filter: drop-shadow(0 0 5px color-mix(in srgb, var(--zw-brand) 70%, transparent)); }
/* P3：塔吊节点发光微晕（脉动） */
.wire .node { fill: var(--zw-brand); stroke: none; filter: drop-shadow(0 0 6px var(--zw-brand)); animation: bp-node 3s ease-in-out infinite; }
.wire .node.n2 { animation-delay: 1s; }
.wire .node.n3 { animation-delay: 2s; }
@keyframes bp-node { 0%,100% { opacity: .5; } 50% { opacity: 1; } }
@keyframes bp-breathe { 0%, 100% { transform: scale(1) translateY(0); } 50% { transform: scale(1.018) translateY(-5px); } }

/* 安全橙体积光缓慢扫过（负延迟错峰，周期拉长） */
.beam { position: absolute; top: -30%; left: -20%; width: 60%; height: 160%; filter: blur(6px); mix-blend-mode: screen;
  background: linear-gradient(100deg, transparent 30%, color-mix(in srgb, var(--zw-brand) 9%, transparent) 48%, color-mix(in srgb, var(--zw-brand) 15%, transparent) 50%, transparent 68%);
  animation: bp-beam 26s ease-in-out infinite; animation-delay: -6s; }
@keyframes bp-beam { 0%, 100% { transform: translateX(-10%) rotate(6deg); } 50% { transform: translateX(130%) rotate(6deg); } }

/* 尘埃粒子上浮 */
.dust i { position: absolute; width: 3px; height: 3px; border-radius: 50%; background: var(--zw-brand); animation: bp-rise linear infinite; }
@keyframes bp-rise { 0% { transform: translateY(30px); opacity: 0; } 12% { opacity: .6; } 100% { transform: translateY(-70vh); opacity: 0; } }

/* 扫描光带（错峰） + 会动的胶片颗粒 + 暗角 */
.scanline { position: absolute; left: 0; right: 0; height: 150px; top: -150px; animation: bp-scan 13s ease-in-out infinite; animation-delay: -4s;
  background: linear-gradient(180deg, transparent, color-mix(in srgb, var(--zw-brand) 8%, transparent), transparent); }
@keyframes bp-scan { 0%, 100% { top: -150px; } 50% { top: 100%; } }
.grain { position: absolute; inset: -50px; opacity: .13; mix-blend-mode: overlay; animation: bp-grain .6s steps(6) infinite;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='3'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E"); }
@keyframes bp-grain { 0%{transform:translate(0,0)} 20%{transform:translate(-6%,4%)} 40%{transform:translate(5%,-5%)} 60%{transform:translate(-4%,-3%)} 80%{transform:translate(4%,5%)} 100%{transform:translate(0,0)} }
.vign { position: absolute; inset: 0; background: radial-gradient(120% 100% at 42% 34%, transparent 38%, color-mix(in srgb, var(--zw-steel-bg) 74%, transparent) 100%); }

/* 无障碍：减少动效时冻结为静态、关视差 */
@media (prefers-reduced-motion: reduce) {
  .blueprint-bg * { animation: none !important; transition: none !important; }
  .plx { transform: none !important; }
  .contour path { stroke-dashoffset: 0; }
}
</style>
