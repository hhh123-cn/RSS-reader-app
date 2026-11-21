package com.b3.rssreader.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏夹实体 —— 对应数据库表 favorite_folders
 *
 * 自选功能"收藏夹管理"的数据模型：
 * 用户可以创建多个收藏夹（如"技术"、"生活"），
 * 收藏文章时选择归入某个收藏夹，实现收藏的分类管理。
 *
 * 文章与收藏夹的关系：
 * Article.favoriteFolderId → favorite_folders.id（可为 NULL = 未分组收藏）。
 * 删除收藏夹时文章不会丢失：外键 onDelete = SET_NULL，
 * 文章留在收藏中变为"未分组"。
 */
@Entity(tableName = "favorite_folders")
data class FavoriteFolder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 收藏夹名称（如"技术文章"） */
    @ColumnInfo(name = "name")
    val name: String,

    /** 创建时间（列表按创建顺序展示） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
