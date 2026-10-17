// ============================================================
// app 模块构建脚本 —— B3 RSS 阅读器（主应用）
// 职责：订阅源管理、RSS 解析、离线缓存、已读/未读、定时刷新、收藏
// ============================================================
plugins {
    alias(libs.plugins.android.application) // Android 应用模块
    alias(libs.plugins.kotlin.android)      // Kotlin 支持
    alias(libs.plugins.ksp)                 // KSP：Room 的注解处理器（编译期生成实现代码）
}

android {
    // 命名空间：R 类、BuildConfig 所在的包名（与 applicationId 是两个概念）
    namespace = "com.b3.rssreader"
    compileSdk = 35 // 用 SDK 35 编译

    defaultConfig {
        // applicationId：设备上唯一标识应用的 ID（与 notification 模块不同，保证可同时安装）
        applicationId = "com.b3.rssreader"
        // minSdk 26 = Android 8.0：
        //   1. 支持通知渠道（NotificationChannel）——本项目通知功能的硬性要求
        //   2. 支持自适应图标（adaptive icon）
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // 课程设计阶段不开启混淆/裁剪，出问题容易排查
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Java 版本：AGP 8.x 强制要求 Java 17 及以上
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // Kotlin 编译产物对应的 JVM 字节码版本，与 Java 17 保持一致
        jvmTarget = "17"
    }

    buildFeatures {
        // 视图绑定：为每个 XML 布局自动生成 Binding 类（ActivityMainBinding 等），
        // 通过 binding.xxx 直接访问控件，类型安全，告别 findViewById
        viewBinding = true
    }
}

dependencies {
    // ---------- AndroidX 基础 ----------
    implementation(libs.androidx.core.ktx)          // Kotlin 扩展（lifecycleScope、Intent 等）
    implementation(libs.androidx.appcompat)         // AppCompatActivity
    implementation(libs.material)                   // Material 组件与主题
    implementation(libs.androidx.constraintlayout)  // 约束布局

    // ---------- MVVM 架构三件套 ----------
    implementation(libs.androidx.lifecycle.runtime.ktx)    // lifecycleScope 协程作用域
    implementation(libs.androidx.lifecycle.viewmodel.ktx)  // viewModelScope、ViewModelProvider
    implementation(libs.androidx.lifecycle.livedata.ktx)   // LiveData 协程构建器

    // ---------- Room 本地数据库 ----------
    // 承担：订阅源存储、文章离线缓存、已读/未读状态、收藏数据
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)        // suspend DAO / Flow 查询支持
    ksp(libs.androidx.room.compiler)              // KSP 编译期生成数据库实现

    // ---------- WorkManager ----------
    // 承担：后台定时拉取所有订阅源的最新文章（周期性任务 PeriodicWorkRequest）
    implementation(libs.androidx.work.runtime.ktx)

    // ---------- OkHttp ----------
    // 承担：HTTP 请求拉取 RSS XML 文本
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor) // 调试用日志拦截器

    // ---------- Navigation ----------
    // 承担：订阅源列表 / 文章列表 / 文章详情 / 收藏夹等页面之间的跳转
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // ---------- 协程 ----------
    implementation(libs.kotlinx.coroutines.android) // 主线程调度器 Dispatchers.Main
}
