#!/usr/bin/env python3
"""
从 DexKit 上游的 dexkit-dev/build.gradle 中移除对 :demo 项目的依赖。

CI 环境通常不安装 Android SDK，而 :demo 是 Android Application 模块，
其 `evaluationDependsOn(":demo")` 会导致 Gradle 配置阶段失败。

此脚本会：
1. 删除 `evaluationDependsOn(":demo")` 行
2. 删除整个 `tasks.register("copyReleaseDemo") { ... }` 块
   （使用花括号匹配，处理嵌套）
3. 删除随后的 `tasks.testClasses.dependsOn(tasks.copyReleaseDemo)` 行

使用方式：python3 strip-demo-dep.py <path-to-dexkit-dev-build.gradle>
"""

import re
import sys
from pathlib import Path


def remove_copy_release_demo_block(src: str) -> str:
    """移除 tasks.register("copyReleaseDemo") { ... } 块。"""
    pattern = re.compile(r'tasks\.register\("copyReleaseDemo"\)\s*\{')
    match = pattern.search(src)
    if not match:
        print("WARN: 未找到 copyReleaseDemo 块", file=sys.stderr)
        return src

    block_start = match.start()
    brace_start = match.end() - 1  # 指向 '{'
    depth = 1
    pos = brace_start + 1
    while pos < len(src) and depth > 0:
        ch = src[pos]
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
        pos += 1

    if depth != 0:
        raise RuntimeError("花括号不匹配，无法定位 copyReleaseDemo 块结束")

    # 跳过块尾后的换行符
    while pos < len(src) and src[pos] in '\r\n':
        pos += 1

    return src[:block_start] + src[pos:]


def remove_evaluation_depends_on_demo(src: str) -> str:
    """删除 evaluationDependsOn(":demo") 行。"""
    return re.sub(
        r'^evaluationDependsOn\(":demo"\)\s*\n',
        '',
        src,
        flags=re.MULTILINE,
    )


def remove_test_classes_depends(src: str) -> str:
    """删除对 tasks.copyReleaseDemo 的 dependsOn 引用（整行，含前导空白）。"""
    return re.sub(
        r'^[ \t]*tasks\.testClasses\.dependsOn\(tasks\.copyReleaseDemo\)[ \t]*\n',
        '',
        src,
        flags=re.MULTILINE,
    )


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <path-to-dexkit-dev/build.gradle>", file=sys.stderr)
        return 1

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"ERROR: 文件不存在: {path}", file=sys.stderr)
        return 1

    src = path.read_text(encoding='utf-8')
    src = remove_copy_release_demo_block(src)
    src = remove_test_classes_depends(src)
    src = remove_evaluation_depends_on_demo(src)
    path.write_text(src, encoding='utf-8')

    print(f"OK: 已移除 {path} 中的 :demo 依赖")
    return 0


if __name__ == '__main__':
    sys.exit(main())
