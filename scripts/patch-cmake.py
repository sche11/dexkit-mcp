#!/usr/bin/env python3
"""向上游 DexKit 的 CMakeLists.txt 注入 LLVM-MinGW 静态链接选项。

替代脆弱的 .patch 文件，使用字符串匹配直接修改，对行号变化免疫。
仅在 WIN32 + Clang 编译器时生效（LLVM-MinGW 场景）。
"""
import re
import sys
from pathlib import Path

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except (AttributeError, OSError):
        pass

# 注入的 Clang 静态链接块（含 elseif 分支和 endif）
CLANG_BLOCK = """    elseif(CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
        # LLVM-MinGW: statically link libc++, libc++abi, libunwind, libwinpthread
        target_link_options(${PROJECT_NAME} PRIVATE
                -Wl,-Bstatic -lc++ -lc++abi -lunwind -lwinpthread -Wl,-Bdynamic
        )
    endif()"""

# 匹配 GNU 静态链接块后的 endif()，捕获 GNU 块内容（不含 endif）
# 结构: target_link_options(... PRIVATE \n -static-libstdc++ \n ... \n ) \n <indent>endif()
GNU_BLOCK_RE = re.compile(
    r'(target_link_options\(\$\{PROJECT_NAME\}\s+PRIVATE\s*\n'
    r'\s*-static-libstdc\+\+\s*\n'
    r'\s*-static-libgcc\s*\n'
    r'\s*-Wl,-Bstatic,--whole-archive\s+-lwinpthread\s+-Wl,--no-whole-archive\s*\n'
    r'\s*\)\s*\n)'
    r'\s*endif\(\)',
    re.MULTILINE,
)

# 备用匹配：直接找 WIN32 块内的 endif() → elseif(APPLE) 模式
FALLBACK_RE = re.compile(
    r'(\s*)endif\(\)\s*\n(\s*)elseif\s*\(APPLE\)',
    re.MULTILINE,
)


def patch_cmake(path: Path) -> bool:
    """修改 CMakeLists.txt，在 GNU 静态链接块后添加 Clang 分支。"""
    src = path.read_text(encoding='utf-8')

    if 'CMAKE_CXX_COMPILER_ID STREQUAL "Clang"' in src:
        print(f"SKIP: {path} 已包含 Clang 静态链接块", file=sys.stderr)
        return True

    match = GNU_BLOCK_RE.search(src)
    if match:
        # 用 GNU 块 + Clang 块替换整个匹配（GNU 块 + endif）
        replacement = match.group(1) + CLANG_BLOCK
        src = src[:match.start()] + replacement + src[match.end():]
    else:
        print(f"WARN: {path} 未找到 GNU 静态链接块，尝试备用匹配", file=sys.stderr)
        fallback = FALLBACK_RE.search(src)
        if not fallback:
            print(f"ERROR: {path} 无法定位插入点", file=sys.stderr)
            return False
        indent = fallback.group(1)
        clang_block = (
            f'    elseif(CMAKE_CXX_COMPILER_ID STREQUAL "Clang")\n'
            f'        target_link_options(${{PROJECT_NAME}} PRIVATE\n'
            f'                -Wl,-Bstatic -lc++ -lc++abi -lunwind -lwinpthread -Wl,-Bdynamic\n'
            f'        )\n'
            f'{indent}endif()'
        )
        # 在 endif() 前插入 Clang 块
        endif_pos = src.rfind('endif()', fallback.start(), fallback.end())
        src = src[:endif_pos] + clang_block + src[endif_pos + len('endif()'):]

    path.write_text(src, encoding='utf-8')
    print(f"OK: 已注入 Clang 静态链接块到 {path}", file=sys.stderr)
    return True


def main() -> int:
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <CMakeLists.txt>", file=sys.stderr)
        return 1
    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"ERROR: 文件不存在: {path}", file=sys.stderr)
        return 1
    return 0 if patch_cmake(path) else 1


if __name__ == '__main__':
    sys.exit(main())
