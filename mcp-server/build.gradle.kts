plugins {
    kotlin("jvm") version "2.3.21"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.luckypray.dexkit"
// 版本号优先从环境变量读取（CI 注入上游 DexKit 版本），本地默认 0.1.0
version = System.getenv("JAR_VERSION") ?: "0.1.0"

application {
    mainClass.set("org.luckypray.dexkit.mcp.MainKt")
    // native 库加载策略：运行时从 jar 资源释放到临时目录，不依赖 java.library.path
    applicationDefaultJvmArgs = listOf("-Djava.library.path=${rootProject.projectDir}/../dexkit-dev/build/library")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // 通过 includeBuild 替换为本地 :dexkit-dev
    implementation("org.luckypray:dexkit-dev:1.0-SNAPSHOT")
    // MCP Kotlin SDK 0.14.0（stdio 传输）
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.14.0")
    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    // JSON 解析（matcher schema）
    implementation("com.google.code.gson:gson:2.11.0")
    // 日志
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

// 把 :dexkit-dev 构建出的 native 库打包到 jar 的 /native/<os>-<arch>/ 路径下
// CI 构建时各平台 native 库放置于 dexkit-dev/build/library/<os>-<arch>/ 子目录
sourceSets {
    main {
        resources {
            srcDir(layout.projectDirectory.dir("../dexkit-dev/build/library"))
        }
    }
}

// 显式声明任务依赖：srcDir 是软引用，Gradle 不会自动建立任务依赖
// 必须确保 :dexkit-dev:copyLibrary 在 processResources 之前运行
// processResources 是读取 srcDir 并复制到 build/resources/main/ 的任务
// 如果 copyLibrary 没在 processResources 之前完成，native 库目录为空，jar 内不会包含 native 库
val copyNativeLib = tasks.register("copyNativeLib") {
    dependsOn(gradle.includedBuilds.first().task(":dexkit-dev:copyLibrary"))
}

tasks {
    processResources {
        dependsOn(copyNativeLib)
    }
    jar {
        dependsOn(copyNativeLib)
        manifest {
            attributes["Main-Class"] = "org.luckypray.dexkit.mcp.MainKt"
        }
    }
    shadowJar {
        dependsOn(copyNativeLib)
        archiveBaseName.set("dexkit-mcp-server")
        archiveClassifier.set("")
        // shadow jar 版本号同步使用环境变量，使产物名包含上游版本（如 dexkit-mcp-server-2.2.0.jar）
        archiveVersion.set(System.getenv("JAR_VERSION") ?: "0.1.0")
        mergeServiceFiles()
        // 重定向 native 库到 jar 内 /native/<platform-tag>/ 路径
        // 只处理 native 库文件（.dll/.so/.dylib），跳过 kotlin_module 等其他资源
        // 三级优先级：
        //   1) CI 环境 NATIVE_PLATFORM_TAG 指定（如 windows-x86_64 / linux-arm64）
        //   2) 源文件位于 <os>-<arch>/ 子目录（多平台合并布局）
        //   3) 按扩展名推断 OS（旧版扁平布局回退）
        eachFile {
            if (!name.endsWith(".dll") && !name.endsWith(".so") && !name.endsWith(".dylib")) return@eachFile
            val envTag = System.getenv("NATIVE_PLATFORM_TAG")
            if (envTag != null && envTag.matches(Regex("^(windows|linux|macos)-(x86_64|arm64)$"))) {
                path = "native/$envTag/$name"
                return@eachFile
            }
            val parentName = file.parentFile?.name
            if (parentName != null && parentName.matches(Regex("^(windows|linux|macos)-(x86_64|arm64)$"))) {
                path = "native/$parentName/$name"
                return@eachFile
            }
            val os = when {
                name.endsWith(".dll") -> "windows"
                name.endsWith(".so") -> "linux"
                name.endsWith(".dylib") -> "macos"
                else -> return@eachFile
            }
            path = "native/$os/$name"
        }
    }
}

kotlin {
    jvmToolchain(22)
}
