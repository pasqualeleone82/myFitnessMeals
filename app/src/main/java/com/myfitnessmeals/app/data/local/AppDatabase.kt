package com.myfitnessmeals.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodItemEntity::class,
        NutritionOverrideEntity::class,
        MealEntryEntity::class,
        FitnessDailyEntity::class,
        DailySummaryEntity::class,
        ProviderConnectionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun nutritionOverrideDao(): NutritionOverrideDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun fitnessDailyDao(): FitnessDailyDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun providerConnectionDao(): ProviderConnectionDao
}
