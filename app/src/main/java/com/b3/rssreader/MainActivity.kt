package com.b3.rssreader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.b3.rssreader.databinding.ActivityMainBinding

/**
 * B3 RSS 阅读器 —— 唯一的 Activity（单 Activity 架构）
 *
 * 架构说明：
 * 整个 App 只有这一个 Activity，所有页面（订阅源 / 文章列表 / 收藏 / 设置，
 * 以及后续阶段的文章详情、添加订阅等）都是 Fragment，
 * 由 Navigation 组件在一个 FragmentContainerView 里切换。
 *
 * 单 Activity 的好处：
 * 1. 页面切换由 Navigation 统一管理返回栈，不会出现 Activity 栈错乱
 * 2. 底部导航栏只放在这里一次，切换 tab 时不会闪烁重建
 * 3. 跨页面的数据共享（例如"从收藏页跳到文章详情"）用同一个 NavController 即可完成
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 从布局中取出 NavHostFragment，拿到全局唯一的 NavController
        //    （FragmentContainerView 里通过 android:name 声明了 NavHostFragment）
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 2. 底部导航与 NavController 联动：
        //    - 点击 tab → navigate 到对应 destination（自带返回栈处理）
        //    - destination 变化 → tab 选中态自动同步
        //    （BottomNavigationView 的 setupWithNavController 只有单参重载，
        //      它内部会按"菜单 id == destination id"判断哪些是顶级页面；
        //      AppBarConfiguration 是配合 AppBar 用的，这里不需要）
        binding.bottomNav.setupWithNavController(navController)
    }
}
