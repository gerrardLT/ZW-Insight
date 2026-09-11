/**
 * Stylelint 配置 — Industrial Precision px 治理门禁（卡增量）
 *
 * governedDirectories = [ ... ] 列出已经批量替换 token 的目录，对这些目录
 * 禁裸 px（margin/padding/gap/font-size/border-radius），存量文件通过迁移完成后再加进列表。
 */
const GOVERNED_DIRECTORIES = [
  'src/views/**/*.vue',
]

export default {
  rules: {}, // 顶层规则为空：仅通过 overrides 按治理目录收紧（stylelint 要求顶层必须存在 rules）
  overrides: [
    {
      files: ['**/*.vue'],
      customSyntax: 'postcss-html',
    },
    {
      files: GOVERNED_DIRECTORIES,
      rules: {
        'declaration-property-value-disallowed-list': {
          '/^(margin|padding)(-(top|right|bottom|left))?$/': ['/\\d+px/'],
          '/^(-webkit-)?margin$|^gap$|^row-gap$|^column-gap$/': ['/\\d+px/'],
          'font-size': ['/\\d+px/'],
          '/^border(-top|-right|-bottom|-left)?-radius$/': ['/\\d+px/'],
        },
      },
    },
  ],
}
