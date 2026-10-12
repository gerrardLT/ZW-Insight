#!/bin/sh
# ==============================================================================
# 安装本地 Git 钩子（2026-10-12）
#
# 为什么需要安装脚本：`.git/hooks/` 不入版本库，钩子必须每人在本地装一次。
# 仓库内的钩子源在 `tests/hooks/`，本脚本负责复制到 `.git/hooks/` 并置可执行位。
#
# 用法：
#   bash tests/install-git-hooks.sh          # 安装
#   bash tests/install-git-hooks.sh --list   # 查看当前状态
#   bash tests/install-git-hooks.sh --force  # 覆盖已存在的同名钩子（先备份）
# ==============================================================================
set -e

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "错误：当前目录不是 Git 仓库" >&2
  exit 1
}
cd "$repo_root"

src_dir="tests/hooks"
dst_dir=".git/hooks"

if [ "$1" = "--list" ]; then
  echo "仓库内可安装的钩子（$src_dir）："
  ls -1 "$src_dir" 2>/dev/null | sed 's/^/  - /' || echo "  （无）"
  echo ""
  echo "已安装到 $dst_dir："
  for f in "$src_dir"/*; do
    [ -f "$f" ] || continue
    name="$(basename "$f")"
    if [ -f "$dst_dir/$name" ]; then
      if cmp -s "$f" "$dst_dir/$name"; then
        echo "  ✅ $name（已安装且与仓库版本一致）"
      else
        echo "  ⚠️  $name（已安装但与仓库版本不同，可用 --force 更新）"
      fi
    else
      echo "  ❌ $name（未安装）"
    fi
  done
  exit 0
fi

force=0
[ "$1" = "--force" ] && force=1

mkdir -p "$dst_dir"
installed=0
skipped=0

for f in "$src_dir"/*; do
  [ -f "$f" ] || continue
  name="$(basename "$f")"
  target="$dst_dir/$name"

  if [ -f "$target" ] && ! cmp -s "$f" "$target"; then
    if [ "$force" = "1" ]; then
      cp "$target" "$target.bak.$(date +%Y%m%d%H%M%S)"
      echo "  ↳ 已备份原 $name"
    else
      echo "  ⏭️  跳过 $name（已存在且内容不同，用 --force 覆盖）"
      skipped=$((skipped + 1))
      continue
    fi
  fi

  cp "$f" "$target"
  chmod +x "$target"
  echo "  ✅ 已安装 $name"
  installed=$((installed + 1))
done

echo ""
echo "完成：安装 $installed 个，跳过 $skipped 个。"
echo "验证：bash tests/install-git-hooks.sh --list"
echo "手动跑一次核对：node tests/check-test-sync.mjs"
