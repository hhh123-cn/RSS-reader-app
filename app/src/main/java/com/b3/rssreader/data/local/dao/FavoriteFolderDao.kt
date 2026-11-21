package com.b3.rssreader.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.b3.rssreader.data.local.entity.FavoriteFolder
import kotlinx.coroutines.flow.Flow

/**
 * 收藏夹数据访问对象
 *
 * 除基础 CRUD 外，提供"收藏夹 + 收藏文章数"的聚合查询，
 * 供收藏页筛选条（chips）展示每个收藏夹内文章数量。
 */
@Dao
interface FavoriteFolderDao {

    /** 新建收藏夹，返回新 id */
    @Insert
    suspend fun insert(folder: FavoriteFolder): Long

    /** 重命名收藏夹 */
    @Query("UPDATE favorite_folders SET name = :name WHERE id = :folderId")
    suspend fun rename(folderId: Long, name: String)

    /**
     * 删除收藏夹（按实体删除）。
     * 注意：articles.favorite_folder_id 外键为 SET_NULL，
     * 夹内文章会自动变为"未分组收藏"，不会丢失。
     */
    @Delete
    suspend fun delete(folder: FavoriteFolder)

    /** 观察全部收藏夹 + 夹内收藏文章数（LEFT JOIN 聚合，空夹计数为 0） */
    @Query(
        """
        SELECT
            favorite_folders.*,
            COUNT(articles.id) AS article_count
        FROM favorite_folders
        LEFT JOIN articles
            ON articles.favorite_folder_id = favorite_folders.id
            AND articles.is_favorite = 1
        GROUP BY favorite_folders.id
        ORDER BY favorite_folders.created_at ASC
        """
    )
    fun observeAllWithCount(): Flow<List<FavoriteFolderWithCount>>

    /** 一次性获取全部收藏夹（对话框选择列表用） */
    @Query("SELECT * FROM favorite_folders ORDER BY created_at ASC")
    suspend fun getAll(): List<FavoriteFolder>
}

/**
 * 收藏夹 + 夹内文章数 聚合结果。
 * COALESCE 不需要：COUNT 无匹配行时返回 0 而非 NULL。
 */
data class FavoriteFolderWithCount(
    @Embedded val folder: FavoriteFolder,
    @ColumnInfo(name = "article_count") val articleCount: Int
)
