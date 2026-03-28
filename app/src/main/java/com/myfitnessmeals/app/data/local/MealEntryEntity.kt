package com.myfitnessmeals.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meal_entry",
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["local_date"], unique = false),
        Index(value = ["food_id"], unique = false),
    ],
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "local_date")
    val localDate: String,
    @ColumnInfo(name = "timezone_offset_min")
    val timezoneOffsetMin: Int,
    @ColumnInfo(name = "meal_type")
    val mealType: String,
    @ColumnInfo(name = "food_id")
    val foodId: Long,
    @ColumnInfo(name = "quantity_value")
    val quantityValue: Double,
    @ColumnInfo(name = "quantity_unit")
    val quantityUnit: String,
    @ColumnInfo(name = "resolved_source")
    val resolvedSource: String,
    @ColumnInfo(name = "kcal_total")
    val kcalTotal: Double,
    @ColumnInfo(name = "carb_total")
    val carbTotal: Double,
    @ColumnInfo(name = "fat_total")
    val fatTotal: Double,
    @ColumnInfo(name = "protein_total")
    val proteinTotal: Double,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
