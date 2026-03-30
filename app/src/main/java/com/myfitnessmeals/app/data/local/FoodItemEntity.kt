package com.myfitnessmeals.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_item",
    indices = [
        Index(value = ["barcode"], unique = false),
        Index(value = ["name"], unique = false),
        Index(value = ["canonical_external_key"], unique = true),
    ],
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "source_id")
    val sourceId: String?,
    val source: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    @ColumnInfo(name = "kcal_100")
    val kcal100: Double?,
    @ColumnInfo(name = "carb_100")
    val carb100: Double?,
    @ColumnInfo(name = "fat_100")
    val fat100: Double?,
    @ColumnInfo(name = "protein_100")
    val protein100: Double?,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long,
    @ColumnInfo(name = "canonical_external_key")
    val canonicalExternalKey: String? = null,
)
