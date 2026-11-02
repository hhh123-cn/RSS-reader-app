package com.b3.rssreader

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.b3.rssreader.databinding.ActivityMainBinding

/**
 * B3 RSS 阅读器 —— 唯一的 Activity（单 Activity 架构）
 *
 * 架构说明：
 * 整个 App 只有这一个 Activity，所有页面（订阅源 / 文章列表 / 收藏 / 设置，
 * 文章详情等）都是 Fragment，由 Navigation 组件在一个 FragmentContainerView 里切换。
 *
 * 深链接支持：
 * 伴生模块通知点击后通过 rssreader://article/{id} 拉起本页：
 * - App 未运行 → onCreate 收到 Intent，NavHostFragment 自动路由
 * - App 已在运行 → 因 launchMode="singleTop" 复用实例，
 *   系统回调 onNewIntent，这里手动 handleDeepLink 完成路由
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 全局唯一 NavController（onNewIntent 处理深链接也要用，提升为成员变量） */
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 从布局中取出 NavHostFragment，拿到全局唯一的 NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 2. 底部导航与 NavController 联动：
        //    点击 tab → navigate 到对应 destination；destination 变化 → tab 选中态自动同步
        binding.bottomNav.setupWithNavController(navController)
    }

    /**
     * 深链接回调：App 已在运行（singleTop 复用实例）时，
     * 伴生模块的通知点击会走到这里，需要手动把深链接交给 Navigation 路由。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navController.handleDeepLink(intent)
    }
}
