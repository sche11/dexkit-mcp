#!/usr/bin/env bash
# prepare-build.sh — 准备 DexKit 上游源码用于 CI 构建
#
# 主要工作：
# 1. 替换 settings.gradle 为最小版本（仅 :dexkit 和 :dexkit-dev）
# 2. 从 dexkit-dev/build.gradle 移除对 :demo 项目的依赖
# 3. 应用 CMakeLists.txt 静态链接补丁（仅对 Windows LLVM-MinGW 生效）
#
# 使用方式：
#   ./scripts/prepare-build.sh [<dexkit-source-root>]
#
# 若未提供 <dexkit-source-root>，使用当前工作目录。
# 脚本所在目录的上一级视为 dexkit-mcp 仓库根（用于定位 patches/ 和 scripts/）。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEXKIT_ROOT="${1:-$(pwd)}"

log() {
    echo "[prepare-build] $*" >&2
}

if [[ ! -f "$DEXKIT_ROOT/settings.gradle" ]]; then
    log "ERROR: $DEXKIT_ROOT/settings.gradle 不存在"
    log "Usage: $0 <dexkit-source-root>"
    exit 1
fi

DEXKIT_ROOT="$(cd "$DEXKIT_ROOT" && pwd)"
log "DexKit source root: $DEXKIT_ROOT"
log "dexkit-mcp repo root: $REPO_ROOT"

# ─────────────────────────────────────────────────────────────────────────────
# 1. 替换 settings.gradle 为最小版本（仅 :dexkit 和 :dexkit-dev）
# ─────────────────────────────────────────────────────────────────────────────
log "[1/3] 替换 settings.gradle 为最小版本..."

cat > "$DEXKIT_ROOT/settings.gradle" <<'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":dexkit")
include(":dexkit-dev")
EOF

log "  settings.gradle 已写入"

# ─────────────────────────────────────────────────────────────────────────────
# 2. 移除 dexkit-dev/build.gradle 对 :demo 的依赖
# ─────────────────────────────────────────────────────────────────────────────
log "[2/3] 移除 dexkit-dev/build.gradle 中的 :demo 依赖..."

python3 "$SCRIPT_DIR/strip-demo-dep.py" "$DEXKIT_ROOT/dexkit-dev/build.gradle"

# ─────────────────────────────────────────────────────────────────────────────
# 3. 应用 CMakeLists.txt 静态链接补丁
# ─────────────────────────────────────────────────────────────────────────────
log "[3/3] 应用 CMakeLists.txt 静态链接补丁..."

cd "$DEXKIT_ROOT"
PATCH_FILE="$REPO_ROOT/patches/cmake-static-link.patch"

if [[ ! -f "$PATCH_FILE" ]]; then
    log "ERROR: 补丁文件不存在: $PATCH_FILE"
    exit 1
fi

# 优先使用 git apply（保留 git 历史），失败则用 patch 命令
if git apply --check "$PATCH_FILE" 2>/dev/null; then
    git apply "$PATCH_FILE"
    log "  补丁已通过 git apply 应用"
elif patch -p1 --dry-run < "$PATCH_FILE" >/dev/null 2>&1; then
    patch -p1 < "$PATCH_FILE"
    log "  补丁已通过 patch 命令应用"
else
    log "WARN: 补丁可能已应用或上下文不匹配，跳过"
fi

log "DONE: DexKit 源码已准备就绪"
