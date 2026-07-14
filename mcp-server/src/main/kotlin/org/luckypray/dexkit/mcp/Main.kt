package org.luckypray.dexkit.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * DexKit MCP Server 入口。
 *
 * 启动流程：
 * 1. 加载 native 库（从 jar 资源释放到临时目录）
 * 2. 创建 BridgePool（管理 DexKitBridge session 生命周期）
 * 3. 注册 11 个 MCP 工具
 * 4. 启动 stdio 传输，等待客户端连接
 *
 * 注册到 Trae CN 的命令：
 *   java -jar dexkit-mcp-server-0.1.0.jar
 */
fun main() = runBlocking {
    // 1. 加载 native 库
    NativeLoader.load()
    System.err.println("[dexkit-mcp] Native library loaded")

    // 2. 创建 BridgePool
    val pool = BridgePool()
    System.err.println("[dexkit-mcp] Bridge pool initialized (max sessions = 4, timeout = 30min)")

    // 3. 注册 shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        pool.closeAll()
        System.err.println("[dexkit-mcp] All sessions closed")
    })

    // 4. 创建 MCP Server
    val server = Server(
        serverInfo = Implementation(
            name = "dexkit-mcp-server",
            version = "0.1.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false)
            )
        )
    )
    registerDexKitTools(server, pool)
    System.err.println("[dexkit-mcp] Registered 11 tools")

    // 5. 启动 stdio 传输
    val transport = StdioServerTransport(
        input = System.`in`.asSource().buffered(),
        output = System.out.asSink().buffered()
    )
    val session = server.createSession(transport)
    System.err.println("[dexkit-mcp] Server started, waiting for stdio input")

    // 等待 session 关闭
    val done = Job()
    session.onClose {
        done.complete()
    }
    done.join()
}
