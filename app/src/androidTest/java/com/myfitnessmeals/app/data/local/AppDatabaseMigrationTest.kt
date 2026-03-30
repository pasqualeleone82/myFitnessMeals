package com.myfitnessmeals.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "app-database-migration-test"

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration_1_2_dedupsDuplicateFoodAndKeepsLatestOverride() {
        seedVersion1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        db.openHelper.writableDatabase
        val migratedDb = db.openHelper.readableDatabase

        val survivorFoodId = migratedDb.queryLong(
            "SELECT id FROM food_item WHERE source = 'OFF' AND source_id = 'off-dup' LIMIT 1"
        )
        assertEquals(2L, survivorFoodId)

        val duplicateCount = migratedDb.queryLong(
            "SELECT COUNT(*) FROM food_item WHERE source = 'OFF' AND source_id = 'off-dup'"
        )
        assertEquals(1L, duplicateCount)

        val canonicalKeyCount = migratedDb.queryLong(
            "SELECT COUNT(*) FROM food_item WHERE canonical_external_key = 'off::sid:off-dup'"
        )
        assertEquals(1L, canonicalKeyCount)

        val overrideCount = migratedDb.queryLong(
            "SELECT COUNT(*) FROM nutrition_override WHERE food_id = $survivorFoodId"
        )
        assertEquals(1L, overrideCount)

        val keptOverrideUpdatedAt = migratedDb.queryLong(
            "SELECT updated_at FROM nutrition_override WHERE food_id = $survivorFoodId"
        )
        assertEquals(5_000L, keptOverrideUpdatedAt)

        val keptOverrideNote = migratedDb.queryString(
            "SELECT note FROM nutrition_override WHERE food_id = $survivorFoodId"
        )
        assertEquals("latest-override", keptOverrideNote)

        val mealEntriesPointingToSurvivor = migratedDb.queryLong(
            "SELECT COUNT(*) FROM meal_entry WHERE food_id = $survivorFoodId"
        )
        assertEquals(2L, mealEntriesPointingToSurvivor)

        val staleFoodReferenceCount = migratedDb.queryLong(
            "SELECT COUNT(*) FROM meal_entry WHERE food_id = 1"
        )
        assertEquals(0L, staleFoodReferenceCount)

        db.close()
    }

    private fun seedVersion1Database() {
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersion1Schema(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    }
                )
                .build()
        )

        val db = helper.writableDatabase
        db.execSQL("PRAGMA foreign_keys=ON")

        db.execSQL(
            """
            INSERT INTO food_item(
                id, source_id, source, name, brand, barcode,
                kcal_100, carb_100, fat_100, protein_100, last_synced_at
            ) VALUES
                (1, 'off-dup', 'OFF', 'Oats', 'Brand A', '1234567890123', 380.0, 65.0, 7.0, 13.0, 1000),
                (2, 'off-dup', 'OFF', 'Oats', 'Brand A', '1234567890123', 382.0, 66.0, 6.5, 12.5, 2000)
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO nutrition_override(
                food_id, kcal_100, carb_100, fat_100, protein_100, note, created_at, updated_at
            ) VALUES
                (1, 390.0, 64.0, 8.0, 14.0, 'latest-override', 1000, 5000),
                (2, 385.0, 63.0, 7.0, 13.0, 'older-override', 1000, 4000)
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO meal_entry(
                id, local_date, timezone_offset_min, meal_type, food_id,
                quantity_value, quantity_unit, resolved_source,
                kcal_total, carb_total, fat_total, protein_total,
                created_at, updated_at
            ) VALUES
                (100, '2026-03-28', 60, 'breakfast', 1, 50.0, 'g', 'OVERRIDE', 195.0, 32.0, 4.0, 7.0, 1000, 1000),
                (101, '2026-03-28', 60, 'snack', 2, 30.0, 'g', 'OVERRIDE', 115.5, 18.9, 2.1, 3.9, 1000, 1000)
            """.trimIndent()
        )

        db.close()
        helper.close()
    }

    private fun createVersion1Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_item (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                source_id TEXT,
                source TEXT NOT NULL,
                name TEXT NOT NULL,
                brand TEXT,
                barcode TEXT,
                kcal_100 REAL,
                carb_100 REAL,
                fat_100 REAL,
                protein_100 REAL,
                last_synced_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_food_item_barcode ON food_item(barcode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_food_item_name ON food_item(name)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nutrition_override (
                food_id INTEGER NOT NULL,
                kcal_100 REAL,
                carb_100 REAL,
                fat_100 REAL,
                protein_100 REAL,
                note TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(food_id),
                FOREIGN KEY(food_id) REFERENCES food_item(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_nutrition_override_food_id ON nutrition_override(food_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_entry (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                local_date TEXT NOT NULL,
                timezone_offset_min INTEGER NOT NULL,
                meal_type TEXT NOT NULL,
                food_id INTEGER NOT NULL,
                quantity_value REAL NOT NULL,
                quantity_unit TEXT NOT NULL,
                resolved_source TEXT NOT NULL,
                kcal_total REAL NOT NULL,
                carb_total REAL NOT NULL,
                fat_total REAL NOT NULL,
                protein_total REAL NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(food_id) REFERENCES food_item(id) ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meal_entry_local_date ON meal_entry(local_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meal_entry_food_id ON meal_entry(food_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fitness_daily (
                local_date TEXT NOT NULL,
                provider TEXT NOT NULL,
                steps INTEGER NOT NULL,
                active_kcal REAL NOT NULL,
                workout_minutes INTEGER NOT NULL,
                last_sync_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL,
                PRIMARY KEY(local_date, provider)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fitness_daily_local_date ON fitness_daily(local_date)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_summary (
                local_date TEXT NOT NULL,
                kcal_target REAL NOT NULL,
                kcal_intake REAL NOT NULL,
                kcal_burned REAL NOT NULL,
                kcal_remaining REAL NOT NULL,
                carb_total REAL NOT NULL,
                fat_total REAL NOT NULL,
                protein_total REAL NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(local_date)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS provider_connection (
                provider TEXT NOT NULL,
                connection_state TEXT NOT NULL,
                token_ref TEXT,
                scopes TEXT NOT NULL,
                last_sync_at INTEGER,
                last_error_code TEXT,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(provider)
            )
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.queryLong(sql: String): Long {
        query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "No rows for query: $sql" }
            return cursor.getLong(0)
        }
    }

    private fun SupportSQLiteDatabase.queryString(sql: String): String {
        query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "No rows for query: $sql" }
            return cursor.getString(0)
        }
    }
}
