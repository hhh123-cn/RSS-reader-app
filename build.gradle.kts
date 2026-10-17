// ============================================================
// 根项目构建脚本
// 职责：声明"整个项目所有模块都可能用到的插件"，但不在根项目启用
// apply false = 只把插件加入构建的 classpath，具体哪个模块要用，
// 由该模块自己的 build.gradle.kts 去 alias(...) 启用
// ============================================================
plugins {
    alias(libs.plugins.android.application) apply false // Android 应用模块插件（app、notification 均用）
    alias(libs.plugins.android.library) apply false     // Android 库模块插件（预留，暂无库模块）
    alias(libs.plugins.kotlin.android) apply false      // Kotlin Android 语言支持
    alias(libs.plugins.ksp) apply false                 // KSP 注解处理器（Room 编译期生成数据库实现代码）
}
