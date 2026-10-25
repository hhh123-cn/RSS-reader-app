package com.b3.rssreader.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.b3.rssreader.databinding.FragmentSettingsBinding

/**
 * 设置页 —— 主 App 自身的设置项
 *
 * 当前为骨架占位；后续阶段实现：
 * - 刷新频率设置（配合 WorkManager 定时刷新）
 * - 跳转到伴生模块提醒设置页的入口（隐式 Intent 打开 P3 模块）
 * - 关于页面（课程设计信息）
 *
 * 注意区分：伴生模块（P3）有自己独立的"提醒设置页"，
 * 它运行在另一个 App 进程里，本页只是提供跳转入口。
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
