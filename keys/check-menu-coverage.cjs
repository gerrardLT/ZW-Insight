// 前端 router 可见路由 vs 后端 sys_menu 种子的差集检查。有缺口则 exit 1。
// 用法（项目根目录）：node keys/check-menu-coverage.cjs
const fs = require('fs'), path = require('path')

const lines = fs.readFileSync('zw-insight-web/src/router/index.ts', 'utf8').split(/\r?\n/)
const routes = []
let top = null, cur = null
for (const l of lines) {
  let m
  if ((m = l.match(/^ {4}path: '([^']*)'/))) { top = { path: m[1], hidden: false }; continue }
  if ((m = l.match(/^ {4}meta: \{(.*)\}/)) && top) { top.hidden = /hidden: true/.test(m[1]); continue }
  if ((m = l.match(/^ {8}path: '([^']*)'/)) && top) { cur = { top: top.path, path: m[1], topHidden: top.hidden }; routes.push(cur); continue }
  if ((m = l.match(/^ {8}meta: \{(.*)\}/)) && cur) {
    cur.title = (m[1].match(/title: '([^']*)'/) || [])[1]
    cur.hidden = /hidden: true/.test(m[1])
  }
}
const full = r => ((r.top === '/' ? '' : r.top) + '/' + r.path).replace(/\/+/g, '/').replace(/\/$/, '') || '/'
const visible = routes.filter(r => r.title && !r.hidden && !r.topHidden && r.path && !r.path.includes(':'))

const menus = new Map()
const re = /(?:\(|SELECT\s+)(\d+),\s*'([^']*)',\s*'(DIR|MENU)',\s*(\d+|NULL),\s*('[^']*'|NULL)/g
const walk = d => fs.readdirSync(d).forEach(f => {
  const p = path.join(d, f)
  if (fs.statSync(p).isDirectory()) return walk(p)
  if (!f.endsWith('.sql')) return
  const s = fs.readFileSync(p, 'utf8')
  if (!s.includes('sys_menu')) return
  for (let m; (m = re.exec(s));) menus.set(m[1], { parent: m[4], path: m[5].replace(/'/g, ''), type: m[3] })
})
walk('zw-insight-server/zw-app/src/main/resources')

const resolve = (m, seen = new Set()) => {
  if (!m.path || m.path === 'NULL') return null
  if (m.path.startsWith('/')) return m.path
  const p = menus.get(m.parent)
  if (!p || seen.has(m)) return null
  seen.add(m)
  const pp = resolve(p, seen)
  return pp && (pp + '/' + m.path).replace(/\/+/g, '/')
}
const have = new Set([...menus.values()].filter(m => m.type === 'MENU').map(m => resolve(m)).filter(Boolean))
const missing = visible.map(full).filter(p => !have.has(p))
missing.forEach(p => console.log('MISSING', p))
console.log(`visible routes ${visible.length}, menu paths ${have.size}, missing ${missing.length}`)
process.exit(missing.length ? 1 : 0)
