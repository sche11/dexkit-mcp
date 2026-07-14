plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "dexkit-mcp-server"

// 复合构建：复用 DexKit 仓库的 :dexkit-dev 模块（桌面版 jar，含 native lib）
includeBuild("..") {
    dependencySubstitution {
        substitute(module("org.luckypray:dexkit-dev")).using(project(":dexkit-dev"))
        substitute(module("org.luckypray:dexkit-dev:1.0-SNAPSHOT")).using(project(":dexkit-dev"))
    }
}
