package com.myfitnessmeals.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodItemEntity::class,
        NutritionOverrideEntity::class,
        MealEntryEntity::class,
        FitnessDailyEntity::class,
        DailySummaryEntity::class,
        ProviderConnectionEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun nutritionOverrideDao(): NutritionOverrideDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun fitnessDailyDao(): FitnessDailyDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun providerConnectionDao(): ProviderConnectionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_item ADD COLUMN canonical_external_key TEXT")

                db.execSQL(
                    """
                    UPDATE food_item
                    SET canonical_external_key = CASE
                        WHEN length(trim(COALESCE(source_id, ''))) > 0
                            THEN lower(trim(source)) || '::sid:' || lower(trim(source_id))
                        WHEN length(trim(COALESCE(barcode, ''))) > 0
                            THEN 'bar:' || lower(trim(barcode))
                        ELSE NULL
                    END
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TEMP TABLE _food_key_best_sync AS
                    SELECT
                        canonical_external_key,
                        MAX(last_synced_at) AS best_last_synced_at
                    FROM food_item
                    WHERE canonical_external_key IS NOT NULL
                    GROUP BY canonical_external_key
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TEMP TABLE _food_key_survivor AS
                    SELECT
                        f.canonical_external_key AS canonical_external_key,
                        MAX(f.id) AS survivor_id
                    FROM food_item f
                    JOIN _food_key_best_sync b
                        ON b.canonical_external_key = f.canonical_external_key
                       AND b.best_last_synced_at = f.last_synced_at
                    GROUP BY f.canonical_external_key
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TEMP TABLE _food_item_survivor AS
                    SELECT
                        f.id AS old_id,
                        s.survivor_id
                    FROM food_item f
                    JOIN _food_key_survivor s
                        ON s.canonical_external_key = f.canonical_external_key
                    WHERE f.canonical_external_key IS NOT NULL
                    """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS temp.idx_food_item_survivor_old_id ON _food_item_survivor(old_id)")

                db.execSQL(
                    """
                    CREATE TEMP TABLE _override_remap AS
                    SELECT
                        COALESCE(s.survivor_id, n.food_id) AS target_food_id,
                        n.food_id AS source_food_id,
                        n.kcal_100,
                        n.carb_100,
                        n.fat_100,
                        n.protein_100,
                        n.note,
                        n.created_at,
                        n.updated_at
                    FROM nutrition_override n
                    LEFT JOIN _food_item_survivor s
                        ON s.old_id = n.food_id
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TEMP TABLE _override_best_updated AS
                    SELECT
                        target_food_id AS target_food_id,
                        MAX(updated_at) AS best_updated_at
                    FROM _override_remap
                    GROUP BY target_food_id
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TEMP TABLE _override_best_created AS
                    SELECT
                        r.target_food_id AS target_food_id,
                        MAX(r.created_at) AS best_created_at
                    FROM _override_remap r
                    JOIN _override_best_updated u
                        ON u.target_food_id = r.target_food_id
                       AND u.best_updated_at = r.updated_at
                    GROUP BY r.target_food_id
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TEMP TABLE _override_latest_source AS
                    SELECT
                        r.target_food_id AS target_food_id,
                        MAX(r.source_food_id) AS source_food_id
                    FROM _override_remap r
                    JOIN _override_best_updated u
                        ON u.target_food_id = r.target_food_id
                       AND u.best_updated_at = r.updated_at
                    JOIN _override_best_created c
                        ON c.target_food_id = r.target_food_id
                       AND c.best_created_at = r.created_at
                    GROUP BY r.target_food_id
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TEMP TABLE _override_latest AS
                    SELECT
                        r.target_food_id AS food_id,
                        r.kcal_100,
                        r.carb_100,
                        r.fat_100,
                        r.protein_100,
                        r.note,
                        r.created_at,
                        r.updated_at
                    FROM _override_remap r
                    JOIN _override_latest_source s
                        ON s.target_food_id = r.target_food_id
                       AND s.source_food_id = r.source_food_id
                    """.trimIndent()
                )

                db.execSQL("DELETE FROM nutrition_override")

                db.execSQL(
                    """
                    INSERT INTO nutrition_override(
                        food_id,
                        kcal_100,
                        carb_100,
                        fat_100,
                        protein_100,
                        note,
                        created_at,
                        updated_at
                    )
                    SELECT
                        food_id,
                        kcal_100,
                        carb_100,
                        fat_100,
                        protein_100,
                        note,
                        created_at,
                        updated_at
                    FROM _override_latest
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    UPDATE meal_entry
                    SET food_id = (
                        SELECT s.survivor_id
                        FROM _food_item_survivor s
                        WHERE s.old_id = meal_entry.food_id
                    )
                    WHERE food_id IN (
                        SELECT s.old_id
                        FROM _food_item_survivor s
                        WHERE s.old_id != s.survivor_id
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    DELETE FROM food_item
                    WHERE id IN (
                        SELECT s.old_id
                        FROM _food_item_survivor s
                        WHERE s.old_id != s.survivor_id
                    )
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE _food_item_survivor")
                db.execSQL("DROP TABLE _food_key_survivor")
                db.execSQL("DROP TABLE _food_key_best_sync")
                db.execSQL("DROP TABLE _override_remap")
                db.execSQL("DROP TABLE _override_latest")
                db.execSQL("DROP TABLE _override_latest_source")
                db.execSQL("DROP TABLE _override_best_created")
                db.execSQL("DROP TABLE _override_best_updated")

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_food_item_canonical_external_key ON food_item(canonical_external_key)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_item ADD COLUMN saturated_fat_100 REAL")
                db.execSQL("ALTER TABLE food_item ADD COLUMN sugar_100 REAL")
                db.execSQL("ALTER TABLE food_item ADD COLUMN iron_100 REAL")
                db.execSQL("ALTER TABLE food_item ADD COLUMN calcium_100 REAL")
                db.execSQL("ALTER TABLE food_item ADD COLUMN magnesium_100 REAL")
                db.execSQL("ALTER TABLE food_item ADD COLUMN zinc_100 REAL")
                db.execSQL("ALTER TABLE food_item ADD COLUMN vitamin_c_100 REAL")
                db.execSQL("ALTER TABLE food_item ADD COLUMN vitamin_d_100 REAL")
                db.execSQL("ALTER TABLE food_item ADD COLUMN vitamin_b12_100 REAL")

                db.execSQL("ALTER TABLE meal_entry ADD COLUMN saturated_fat_total REAL")
                db.execSQL("ALTER TABLE meal_entry ADD COLUMN sugar_total REAL")
                db.execSQL("ALTER TABLE meal_entry ADD COLUMN iron_total REAL")
                db.execSQL("ALTER TABLE meal_entry ADD COLUMN calcium_total REAL")
                db.execSQL("ALTER TABLE meal_entry ADD COLUMN magnesium_total REAL")
                db.execSQL("ALTER TABLE meal_entry ADD COLUMN zinc_total REAL")
                db.execSQL("ALTER TABLE meal_entry ADD COLUMN vitamin_c_total REAL")
                db.execSQL("ALTER TABLE meal_entry ADD COLUMN vitamin_d_total REAL")
                db.execSQL("ALTER TABLE meal_entry ADD COLUMN vitamin_b12_total REAL")

                db.execSQL("ALTER TABLE nutrition_override ADD COLUMN saturated_fat_100 REAL")
                db.execSQL("ALTER TABLE nutrition_override ADD COLUMN sugar_100 REAL")
                db.execSQL("ALTER TABLE nutrition_override ADD COLUMN iron_100 REAL")
                db.execSQL("ALTER TABLE nutrition_override ADD COLUMN calcium_100 REAL")
                db.execSQL("ALTER TABLE nutrition_override ADD COLUMN magnesium_100 REAL")
                db.execSQL("ALTER TABLE nutrition_override ADD COLUMN zinc_100 REAL")
                db.execSQL("ALTER TABLE nutrition_override ADD COLUMN vitamin_c_100 REAL")
                db.execSQL("ALTER TABLE nutrition_override ADD COLUMN vitamin_d_100 REAL")
                db.execSQL("ALTER TABLE nutrition_override ADD COLUMN vitamin_b12_100 REAL")

                db.execSQL("ALTER TABLE daily_summary ADD COLUMN saturated_fat_total REAL")
                db.execSQL("ALTER TABLE daily_summary ADD COLUMN sugar_total REAL")
                db.execSQL("ALTER TABLE daily_summary ADD COLUMN iron_total REAL")
                db.execSQL("ALTER TABLE daily_summary ADD COLUMN calcium_total REAL")
                db.execSQL("ALTER TABLE daily_summary ADD COLUMN magnesium_total REAL")
                db.execSQL("ALTER TABLE daily_summary ADD COLUMN zinc_total REAL")
                db.execSQL("ALTER TABLE daily_summary ADD COLUMN vitamin_c_total REAL")
                db.execSQL("ALTER TABLE daily_summary ADD COLUMN vitamin_d_total REAL")
                db.execSQL("ALTER TABLE daily_summary ADD COLUMN vitamin_b12_total REAL")
            }
        }
    }
}
