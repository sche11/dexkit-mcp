# DexKit MCP Server

把 DexKit 封装为 MCP Server，AI 可直接调用工具分析 APK，定位被混淆的类/方法/字段。

## 工具列表（11 个）

| 工具 | 用途 |
|---|---|
| `dexkit_open` | 创建 session（APK 路径或 DEX 字节数组） |
| `dexkit_close_session` | 关闭 session 释放资源 |
| `dexkit_find_class` | 多条件查找类 |
| `dexkit_find_method` | 多条件查找方法 |
| `dexkit_find_field` | 多条件查找字段 |
| `dexkit_batch_find_class_using_strings` | 批量字符串搜索定位类 |
| `dexkit_batch_find_method_using_strings` | 批量字符串搜索定位方法 |
| `dexkit_get_class_data` | 通过 identifier 获取类详情 |
| `dexkit_get_method_data` | 通过 descriptor 获取方法详情 |
| `dexkit_get_field_data` | 通过 descriptor 获取字段详情 |
| `dexkit_export_dex` | 导出所有 dex 到目录 |

完整 schema 见 `c:\Users\schedule\.trae-cn\skills\dexkit\dexkit-mcp-usage.md`。

## 前置依赖

- JDK 17+
- Android NDK（用于构建 DexKit native 库）
- CMake + Ninja

## 构建步骤

### 1. 构建 DexKit 桌面 jar（一次性，需 NDK）

```powershell
cd d:\save\WorkSpace\DexKit
.\gradlew :dexkit-dev:jar --no-daemon -x :demo:assembleRelease -x test
```

产物：
- `dexkit-dev\build\libs\dexkit-dev-1.0-SNAPSHOT.jar`
- `dexkit-dev\build\library\libdexkit-dev.dll`（Windows）

### 2. 构建 MCP Server fat-jar

```powershell
cd d:\save\WorkSpace\DexKit\mcp-server
.\gradlew shadowJar --no-daemon
```

产物：`build\libs\dexkit-mcp-server-0.1.0.jar`

## 运行

### 命令行测试

```powershell
java -Djava.library.path=d:\save\WorkSpace\DexKit\dexkit-dev\build\library ^
     -jar d:\save\WorkSpace\DexKit\mcp-server\build\libs\dexkit-mcp-server-0.1.0.jar
```

进程启动后等待 stdio 输入 JSON-RPC 消息。

### 注册到 Trae CN

在 `C:\Users\schedule\AppData\Roaming\Trae CN\User\mcp.json` 的 `mcpServers` 中追加：

```json
"dexkit": {
  "command": "java",
  "args": [
    "-Djava.library.path=d:\\save\\WorkSpace\\DexKit\\dexkit-dev\\build\\library",
    "-jar",
    "d:\\save\\WorkSpace\\DexKit\\mcp-server\\build\\libs\\dexkit-mcp-server-0.1.0.jar"
  ],
  "env": {}
}
```

重启 Trae CN 后生效。

## 设计要点

- **Session 池**：最多 4 个并发，30 分钟空闲自动 close
- **Native 加载**：从 fat-jar 内嵌资源释放到临时目录，避免依赖 `java.library.path`
- **反编译目录**：不支持，返回错误并指引（用 apktool 重打包）
- **复合构建**：`includeBuild("..")` 复用 DexKit 仓库的 `:dexkit-dev` 模块
