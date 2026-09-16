/**
 * Stylelint 配置 — Industrial Precision 治理门禁（卡增量）
 *
 * PX_GOVERNED_DIRECTORIES = [ ... ] 列出已完成 px 批量替换 token 的目录，
 * 对这些目录禁裸 px（margin/padding/gap/font-size/border-radius），
 * 存量文件通过迁移完成后再加进列表。
 *
 * COLOR_GOVERNED_DIRECTORIES：2026-09-15 Phase 1.4 Design Tokens 清债完成，
 * CSS 侧硬编码色已全量替换为 --zw-* token / color-mix 派生，全量禁新增
 * 裸 Hex 与 rgb()/rgba()/hsl()/hsla()（透明度变体统一 color-mix 派生）。
 * tokens/*.css 定义文件不受限（hex 合法源头）；SVG 属性色不受限（图形资产）。
 */
const PX_GOVERNED_DIRECTORIES = [
  'src/views/**/*.vue',
]

const COLOR_GOVERNED_DIRECTORIES = [
  'src/**/*.vue',
]

export default {
  rules: {}, // 顶层规则为空：仅通过 overrides 按治理目录收紧（stylelint 要求顶层必须存在 rules）
  overrides: [
    {
      files: ['**/*.vue'],
      customSyntax: 'postcss-html',
    },
    {
      files: PX_GOVERNED_DIRECTORIES,
      rules: {
        'declaration-property-value-disallowed-list': {
          '/^(margin|padding)(-(top|right|bottom|left))?$/': ['/\\d+px/'],
          '/^(-webkit-)?margin$|^gap$|^row-gap$|^column-gap$/': ['/\\d+px/'],
          'font-size': ['/\\d+px/'],
          '/^border(-top|-right|-bottom|-left)?-radius$/': ['/\\d+px/'],
        },
      },
    },
    {
      files: COLOR_GOVERNED_DIRECTORIES,
      rules: {
        'color-no-hex': true,
        'function-disallowed-list': ['rgb', 'rgba', 'hsl', 'hsla'],
      },
    },
  ],
}
