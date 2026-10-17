// ============================================================
// settings.gradle.kts —— Gradle 项目全局配置
// 职责：
//   1. 声明项目包含哪些模块
//   2. 声明插件 / 依赖从哪些仓库下载（全项目统一，模块内禁止再写）
// ============================================================

pluginManagement {
    // 插件下载仓库（Android Gradle 插件、Kotlin 插件、KSP 插件等）
    repositories {
        google {
            // 限定 google() 仓库只负责解析 Android/Google/AndroidX 相关插件，
            // 避免每个插件都去 google() 仓库查找一遍，加快同步速度
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS：
    // 禁止各模块在自己的 build.gradle.kts 里声明 repositories {}
    // 所有依赖仓库统一在这里声明，防止仓库配置分散、版本冲突
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()       // AndroidX、Google 官方库
        mavenCentral() // 第三方库：OkHttp、Kotlinx 协程等
    }
}

// 项目根名称（与磁盘文件夹名无关，可以带中文以外的规范名）
rootProject.name = "B3RssReader"

// ------------------------------------------------------------
// 声明两个模块：
//   :app          —— B3 RSS 阅读器主应用（application 模块）
//   :notification —— P3 通知提醒中心伴生应用（application 模块，可独立安装运行）
// ------------------------------------------------------------
include(":app")
include(":notification")
