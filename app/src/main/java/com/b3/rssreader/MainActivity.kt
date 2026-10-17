package com.b3.rssreader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.b3.rssreader.databinding.ActivityMainBinding

/**
 * B3 RSS 阅读器 —— 主应用入口 Activity
 *
 * 阶段 1 仅作占位：展示模块骨架是否正常跑通。
 * 阶段 2 起将改为使用 Navigation 承载"订阅源列表 → 文章列表 → 文章详情"等页面。
 *
 * 关于 ViewBinding：
 * ActivityMainBinding 由布局文件 activity_main.xml 自动生成（buildFeatures.viewBinding = true），
 * binding.root 就是整个布局的根 View，setContentView 之后即可通过 binding.xxx 访问控件。
 */
class MainActivity : AppCompatActivity() {

    // lateinit：延迟初始化，在 onCreate 中赋值（ViewBinding 的常见写法）
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 第一步：inflate 布局并生成 Binding 对象
        binding = ActivityMainBinding.inflate(layoutInflater)
        // 第二步：把布局根 View 设置给 Activity
        setContentView(binding.root)

        // 占位提示：证明两个模块骨架搭建成功、可以正常编译运行
        binding.tvPlaceholder.text = getString(R.string.main_placeholder)
    }
}
