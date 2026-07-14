package org.luckypray.dexkit.mcp

import java.io.File
import java.util.Locale

/**
 * Native 库加载器：从 fat-jar 内嵌资源释放到临时目录后 System.load。
 *
 * 加载顺序：
 * 1. 优先尝试 System.loadLibrary("dexkit")（依赖 java.library.path）
 * 2. 失败则从 jar 资源 /native/<os>-<arch>/ 释放到临时目录并加载
 *
 * 资源路径约定（与 build.gradle.kts 的 shadowJar eachFile 配对）：
 * - Windows x64:    /native/windows-x86_64/libdexkit.dll
 * - Linux x64:      /native/linux-x86_64/libdexkit.so
 * - Linux arm64:    /native/linux-arm64/libdexkit.so
 * - macOS arm64:    /native/macos-arm64/libdexkit.dylib
 *
 * 兼容回退：若新路径未命中，会尝试旧版 /native/<os>/ 路径，
 * 以便在不区分架构的旧 fat-jar 上仍能加载。
 */
object NativeLoader {

    private const val VERSION = "0.1.0"
    private const val LIB_NAME = "dexkit"

    private val osName: String = System.getProperty("os.name").lowercase(Locale.getDefault())
    private val osArch: String = System.getProperty("os.arch").lowercase(Locale.getDefault())

    val isWindows: Boolean = osName.contains("windows")
    val isLinux: Boolean = osName.contains("linux")
    val isMacos: Boolean = osName.contains("mac") || osName.contains("darwin")

    /**
     * 标准化架构标识。统一为 x86_64 / arm64 两类。
     */
    private val archTag: String
        get() = when {
            osArch == "aarch64" || osArch.contains("arm64") -> "arm64"
            osArch == "amd64" || osArch == "x86_64" -> "x86_64"
            else -> osArch
        }

    /**
     * OS 标识（不含架构）。
     */
    private val osTag: String
        get() = when {
            isWindows -> "windows"
            isLinux -> "linux"
            isMacos -> "macos"
            else -> "linux"
        }

    /**
     * 平台完整标签：os-arch，例如 windows-x86_64、linux-arm64。
     */
    val platformTag: String
        get() = "$osTag-$archTag"

    private val libExt: String
        get() = when {
            isWindows -> "dll"
            isLinux -> "so"
            isMacos -> "dylib"
            else -> "so"
        }

    /**
     * 加载 DexKit native 库。
     * 先尝试 java.library.path，失败则按以下顺序从 jar 资源加载：
     * 1) /native/<os>-<arch>/libdexkit.<ext>  （新版多架构 fat-jar）
     * 2) /native/<os>-<arch>/dexkit.<ext>     （无 lib 前缀的备选）
     * 3) /native/<os>/libdexkit.<ext>         （旧版回退）
     * 4) /native/<os>/dexkit.<ext>
     */
    fun load() {
        // 1. 先尝试 java.library.path
        try {
            System.loadLibrary(LIB_NAME)
            return
        } catch (e: UnsatisfiedLinkError) {
            // 继续 fallback
        }

        val libFileName = if (isWindows) "lib$LIB_NAME.$libExt" else "$LIB_NAME.$libExt"
        val candidatePaths = buildList {
            add("/native/$platformTag/lib$LIB_NAME.$libExt")
            add("/native/$platformTag/$LIB_NAME.$libExt")
            add("/native/$platformTag/libdexkit-dev.$libExt")
            add("/native/$platformTag/dexkit-dev.$libExt")
            // 旧版无架构区分的路径回退
            add("/native/$osTag/lib$LIB_NAME.$libExt")
            add("/native/$osTag/$LIB_NAME.$libExt")
        }

        val tempDir = File(System.getProperty("java.io.tmpdir"), "dexkit-mcp-$VERSION").apply { mkdirs() }
        val tempFile = File(tempDir, libFileName)

        val resourceStream = candidatePaths
            .firstNotNullOfOrNull { path -> this::class.java.getResourceAsStream(path) }
            ?: error(
                "Native library not found in jar. Looked for: $candidatePaths. " +
                    "Please ensure the fat-jar contains /native/$platformTag/libdexkit.$libExt."
            )

        resourceStream.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }

        System.load(tempFile.absolutePath)
    }
}
