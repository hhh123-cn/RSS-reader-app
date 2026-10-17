// ============================================================
// notification 模块构建脚本 —— P3 通知提醒中心（伴生应用）
//
// 注意：本模块同样是 application 模块（不是 library），
// 拥有独立 applicationId 和桌面入口，可以脱离主 App 单独安装运行，
// 满足"伴生模块可独立运行"的要求。
// ============================================================
plugins {
    alias(libs.plugins.android.application) // Android 应用模块
    alias(libs.plugins.kotlin.android)      // Kotlin 支持
    // 注意：本模块不用 KSP——它不持有 Room 数据库（原因见下方依赖说明）
}

android {
    namespace = "com.b3.notification"
    compileSdk = 35

    defaultConfig {
        // 与主 App 不同的 applicationId，两个 App 可以同时安装在同一台设备上
        applicationId = "com.b3.notification"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ---------- AndroidX 基础 ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // ---------- MVVM ----------
    // 提醒设置页也遵循 MVVM：设置状态由 ViewModel + LiveData 管理
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // ---------- WorkManager ----------
    // 承担：后台周期性检查"是否有新文章"，有新文章则推送通知
    implementation(libs.androidx.work.runtime.ktx)

    // ---------- 协程 ----------
    implementation(libs.kotlinx.coroutines.android)

    // ============================================================
    // 依赖取舍说明（课程设计答辩可讲）：
    //
    // 1. 为什么没有 Room？
    //    Room 数据库由主 App 持有。Android 规定两个独立 APK 的文件互不可见，
    //    因此共享数据库的标准做法是：主 App 用 ContentProvider 把数据导出，
    //    伴生模块通过 ContentResolver 跨进程读取（阶段 2 实现）。
    //    若伴生模块自己也建 Room 库，那就是"两份数据"而非"共享数据库"。
    //
    // 2. 为什么没有 OkHttp？
    //    拉取 RSS 的职责在主 App（配合其 WorkManager 定时刷新）。
    //    伴生模块只负责"发现新文章 → 推送通知"，数据来自 ContentProvider，无需联网。
    //
    // 3. 为什么没有 Navigation？
    //    伴生模块只有一个独立的提醒设置页（单一 Activity），
    //    页面少、跳转简单，不需要引入导航框架。
    // ============================================================
}
