package com.b3.rssreader.data.repository

import android.content.Context
import com.b3.rssreader.data.local.AppDatabase
import com.b3.rssreader.data.local.dao.FavoriteFolderDao
import com.b3.rssreader.data.local.dao.FavoriteFolderWithCount
import com.b3.rssreader.data.local.entity.FavoriteFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 收藏夹仓库 —— 收藏夹（分组）的唯一数据入口
 *
 * 分层：UI → ViewModel → FavoriteFolderRepository → FavoriteFolderDao → Room
 * 协程约定与项目其它仓库一致：查询返回 Flow，写操作 suspend + Dispatchers.IO。
 */
class FavoriteFolderRepository private constructor(private val folderDao: FavoriteFolderDao) {

    /** 观察全部收藏夹 + 夹内文章数（收藏页筛选条展示） */
    fun observeAllWithCount(): Flow<List<FavoriteFolderWithCount>> = folderDao.observeAllWithCount()

    /** 一次性获取全部收藏夹（收藏/移动对话框的选择列表） */
    suspend fun getAll(): List<FavoriteFolder> = withContext(Dispatchers.IO) {
        folderDao.getAll()
    }

    /** 新建收藏夹，返回新 id */
    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        folderDao.insert(FavoriteFolder(name = name))
    }

    /** 重命名收藏夹 */
    suspend fun renameFolder(folderId: Long, newName: String) = withContext(Dispatchers.IO) {
        folderDao.rename(folderId, newName)
    }

    /**
     * 删除收藏夹。
     * 数据库外键 SET_NULL 会把夹内文章自动变为"未分组收藏"（不丢数据）。
     */
    suspend fun deleteFolder(folder: FavoriteFolder) = withContext(Dispatchers.IO) {
        folderDao.delete(folder)
    }

    companion object {
        @Volatile
        private var INSTANCE: FavoriteFolderRepository? = null

        fun getInstance(context: Context): FavoriteFolderRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoriteFolderRepository(
                    AppDatabase.getInstance(context).favoriteFolderDao()
                ).also { INSTANCE = it }
            }
    }
}
