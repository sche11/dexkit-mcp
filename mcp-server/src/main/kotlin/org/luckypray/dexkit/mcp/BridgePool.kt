package org.luckypray.dexkit.mcp

import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Session 池：管理 DexKitBridge 生命周期。
 *
 * - 最多 [MAX_SESSIONS] 个并发 session
 * - 超过上限时淘汰最久未访问的（LRU）
 * - [SESSION_TIMEOUT_MIN] 分钟未访问自动 close
 */
class BridgePool {

    private data class Entry(
        val bridge: DexKitBridge,
        val createdAt: Long = System.currentTimeMillis(),
        @Volatile var lastAccessAt: Long = System.currentTimeMillis()
    ) {
        fun touch() { lastAccessAt = System.currentTimeMillis() }
    }

    private val sessions = ConcurrentHashMap<String, Entry>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "dexkit-mcp-reaper").apply { isDaemon = true }
    }

    init {
        // 每 5 分钟扫描一次超时 session
        scheduler.scheduleAtFixedRate(this::reapExpired, 5, 5, TimeUnit.MINUTES)
    }

    /**
     * 通过 APK 路径创建新 session。
     * @return sessionId
     */
    fun openApk(apkPath: String): String {
        val file = File(apkPath)
        require(file.exists() && file.isFile) {
            "APK file not found or not a file: $apkPath"
        }
        val bridge = DexKitBridge.create(file.absolutePath)
        return register(bridge)
    }

    /**
     * 通过 DEX 字节数组创建新 session。
     * @param dexBytesArray base64 解码后的字节数组列表
     */
    fun openDex(dexBytesArray: List<ByteArray>): String {
        require(dexBytesArray.isNotEmpty()) { "dexBytesArray must not be empty" }
        val bridge = DexKitBridge.create(dexBytesArray.toTypedArray())
        return register(bridge)
    }

    /**
     * 获取 bridge 并刷新访问时间。
     * @throws IllegalStateException session 不存在或已关闭
     */
    fun get(sessionId: String): DexKitBridge {
        val entry = sessions[sessionId] ?: error("Session not found or already closed: $sessionId")
        entry.touch()
        return entry.bridge
    }

    /**
     * 关闭指定 session。
     */
    fun close(sessionId: String): Boolean {
        val entry = sessions.remove(sessionId) ?: return false
        return try {
            entry.bridge.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 关闭所有 session（用于 shutdown hook）。
     */
    fun closeAll() {
        sessions.keys.toList().forEach { close(it) }
        scheduler.shutdownNow()
    }

    /**
     * 当前活跃 session 数。
     */
    fun size(): Int = sessions.size

    private fun register(bridge: DexKitBridge): String {
        // LRU 淘汰
        while (sessions.size >= MAX_SESSIONS) {
            evictOldest()
        }
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = Entry(bridge)
        return sessionId
    }

    private fun evictOldest() {
        val oldest = sessions.entries.minByOrNull { it.value.lastAccessAt } ?: return
        close(oldest.key)
    }

    private fun reapExpired() {
        val now = System.currentTimeMillis()
        val expired = sessions.entries
            .filter { now - it.value.lastAccessAt > SESSION_TIMEOUT_MS }
            .map { it.key }
        expired.forEach { close(it) }
    }

    companion object {
        private const val MAX_SESSIONS = 4
        private const val SESSION_TIMEOUT_MIN = 30L
        private const val SESSION_TIMEOUT_MS = SESSION_TIMEOUT_MIN * 60 * 1000L
    }
}
