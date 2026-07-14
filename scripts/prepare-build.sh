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
# 2. 移除 dexkit/ 与 dexkit-dev/ 的 build.gradle 对 :demo 的依赖
#    （两个模块都有 copyReleaseDemo task 和 evaluationDependsOn(":demo")）
# ─────────────────────────────────────────────────────────────────────────────
log "[2/3] 移除 dexkit/ 和 dexkit-dev/ 中的 :demo 依赖..."

python3 "$SCRIPT_DIR/strip-demo-dep.py" \
    "$DEXKIT_ROOT/dexkit/build.gradle" \
    "$DEXKIT_ROOT/dexkit-dev/build.gradle"

# ─────────────────────────────────────────────────────────────────────────────
# 3. 注入 CMakeLists.txt 静态链接选项（LLVM-MinGW Clang on Windows）
#    使用 Python 脚本替代脆弱的 .patch 文件，对上游行号变化免疫
# ─────────────────────────────────────────────────────────────────────────────
log "[3/3] 注入 CMakeLists.txt 静态链接选项..."

python3 "$SCRIPT_DIR/patch-cmake.py" \
    "$DEXKIT_ROOT/dexkit/src/main/cpp/CMakeLists.txt"

log "DONE: DexKit 源码已准备就绪"
