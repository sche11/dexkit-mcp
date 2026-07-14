# DexKit MCP Server

将 [DexKit](https://github.com/LuckyPray/DexKit) 暴露为 [Model Context Protocol](https://modelcontextprotocol.io/) 服务器，使 AI 助手能够直接分析 APK/DEX 文件、查找被混淆的类与方法、生成 Xposed/Hook 代码。

> **本仓库不维护 DexKit 源码**。每次构建都会拉取上游 [`LuckyPray/DexKit`](https://github.com/LuckyPray/DexKit) 最新代码，应用必要的 CI 补丁后产出 fat-jar。

## 特性

- 11 个 MCP 工具：查找类/方法/字段、批量字符串搜索、查看 xrefs、获取 smali 等
- 多平台 native 支持：Windows x86_64、Linux x86_64、Linux arm64
- 静态链接 C++ 运行时，零外部 DLL 依赖（Windows）
- 单 jar 部署，运行时按 OS+架构自动释放 native 库

## 下载

从 [Releases](../../releases) 页下载对应平台的 fat-jar：

| 平台 | 文件名 |
|---|---|
| Windows x64 | `dexkit-mcp-server-<version>-windows-x86_64.jar` |
| Linux x64 | `dexkit-mcp-server-<version>-linux-x86_64.jar` |
| Linux arm64 | `dexkit-mcp-server-<version>-linux-arm64.jar` |

需要 Java 22+。

## 配置 MCP 客户端

### Cursor / Claude Desktop / Trae

```json
{
  "mcpServers": {
    "dexkit": {
      "command": "java",
      "args": ["-jar", "/path/to/dexkit-mcp-server-0.1.0-<platform>.jar"],
      "env": {}
    }
  }
}
```

### 验证

启动客户端后，MCP 服务器会注册以下工具（部分）：

- `dexkit_open` — 打开 APK/DEX 文件并创建会话
- `dexkit_find_class` — 按字符串/注解/调用关系查找类
- `dexkit_find_method` — 按多种条件查找方法
- `dexkit_find_field` — 查找字段
- `dexkit_batch_find_class_using_strings` — 批量字符串→类
- `dexkit_batch_find_method_using_strings` — 批量字符串→方法
- `dexkit_get_class_data` / `dexkit_get_method_data` / `dexkit_get_field_data` — 获取详细信息
- `dexkit_export_dex` — 导出修改后的 dex
- `dexkit_close_session` — 关闭会话

完整文档见 [`mcp-server/README.md`](mcp-server/README.md)。

## 本地构建

### 前置条件

- JDK 22+
- CMake 3.20+
- Ninja
- Windows: [LLVM-MinGW](https://github.com/mstorsjo/llvm-mingw/releases) 20240619+（ucrt x86_64）
- Linux: gcc/g++ + zlib1g-dev

### 步骤

```bash
# 1. 克隆本仓库
git clone https://github.com/sche11/dexkit-mcp.git
cd dexkit-mcp

# 2. 克隆上游 DexKit 到相邻目录
git clone https://github.com/LuckyPray/DexKit.git ../DexKit

# 3. 应用 CI 补丁（移除 :demo 依赖、静态链接 patch）
bash scripts/prepare-build.sh ../DexKit

# 4. 复制 mcp-server 到 DexKit 内（复合构建需要）
cp -r mcp-server ../DexKit/mcp-server

# 5. 构建
cd ../DexKit/mcp-server
# Windows 需要先安装 LLVM-MinGW 并设置 CC=clang CXX=clang++
./gradlew shadowJar

# 6. 产物位于
ls build/libs/dexkit-mcp-server-0.1.0.jar
```

## CI 自动构建

GitHub Actions 工作流定义于 [`.github/workflows/build.yml`](.github/workflows/build.yml)。

**触发方式：**
- Push 到 `main` / `master` 分支
- 推送 `v*` 标签（如 `v0.1.0`）— 触发 Release 构建
- 每周一定时构建（跟进上游）
- 手动触发（可指定上游 ref）

**构建矩阵：**

| Runner | 平台标签 | 工具链 |
|---|---|---|
| `windows-latest` | `windows-x86_64` | LLVM-MinGW (clang 18) + 静态 zlib |
| `ubuntu-latest` | `linux-x86_64` | GCC + zlib1g-dev |
| `ubuntu-24.04-arm` | `linux-arm64` | GCC + zlib1g-dev |

**关键补丁：**

- [`patches/cmake-static-link.patch`](patches/cmake-static-link.patch) — 让 CMakeLists.txt 在 LLVM-MinGW (Clang) 下静态链接 `libc++`、`libc++abi`、`libunwind`、`libwinpthread`，避免运行时依赖外部 DLL。
- [`scripts/strip-demo-dep.py`](scripts/strip-demo-dep.py) — 移除 `dexkit-dev/build.gradle` 中对 `:demo` Android 项目的依赖（CI 无 Android SDK）。

## 许可证

Apache License 2.0，与上游 DexKit 一致。

## 致谢

- [DexKit](https://github.com/LuckyPray/DexKit) — 由 LuckyPray 维护的高性能 DEX 解析库
- [Model Context Protocol](https://modelcontextprotocol.io/) — Anthropic 提出的工具调用协议
- [LLVM-MinGW](https://github.com/mstorsjo/llvm-mingw) — Martin Storsjö 维护的 LLVM-based MinGW 工具链
