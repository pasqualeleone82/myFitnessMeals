package com.myfitnessmeals.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
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
        assertEquals(5000L, keptOverrideUpdatedAt)

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

    @Test
    fun migration_1_2_largeDataset_preservesDedupAndLatestOverridePolicy() {
        val groupCount = 120
        val duplicatesPerGroup = 6
        seedLargeVersion1Database(groupCount = groupCount, duplicatesPerGroup = duplicatesPerGroup)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        db.openHelper.writableDatabase
        val migratedDb = db.openHelper.readableDatabase

        val dedupedFoodCount = migratedDb.queryLong(
            "SELECT COUNT(*) FROM food_item WHERE source_id LIKE 'off-bulk-%'"
        )
        assertEquals(groupCount.toLong(), dedupedFoodCount)

        val duplicateCanonicalKeys = migratedDb.queryLong(
            """
            SELECT COUNT(*)
            FROM (
                SELECT canonical_external_key
                FROM food_item
                WHERE canonical_external_key LIKE 'off::sid:off-bulk-%'
                GROUP BY canonical_external_key
                HAVING COUNT(*) > 1
            )
            """.trimIndent()
        )
        assertEquals(0L, duplicateCanonicalKeys)

        val dedupedOverrideCount = migratedDb.queryLong(
            """
            SELECT COUNT(*)
            FROM nutrition_override n
            JOIN food_item f ON f.id = n.food_id
            WHERE f.source_id LIKE 'off-bulk-%'
            """.trimIndent()
        )
        assertEquals(groupCount.toLong(), dedupedOverrideCount)

        val sampleGroup = 42
        val expectedSurvivorId =
            1_000L + ((sampleGroup - 1) * duplicatesPerGroup) + (duplicatesPerGroup - 1)

        val actualSurvivorId = migratedDb.queryLong(
            "SELECT id FROM food_item WHERE source_id = 'off-bulk-$sampleGroup'"
        )
        assertEquals(expectedSurvivorId, actualSurvivorId)

        val keptOverrideNote = migratedDb.queryString(
            "SELECT note FROM nutrition_override WHERE food_id = $actualSurvivorId"
        )
        assertEquals("bulk-override-g$sampleGroup-d$duplicatesPerGroup", keptOverrideNote)

        val mealReferences = migratedDb.queryLong(
            "SELECT COUNT(*) FROM meal_entry WHERE local_date = '2026-03-29'"
        )
        assertEquals((groupCount * duplicatesPerGroup).toLong(), mealReferences)

        val danglingMealReferences = migratedDb.queryLong(
            """
            SELECT COUNT(*)
            FROM meal_entry m
            LEFT JOIN food_item f ON f.id = m.food_id
            WHERE m.local_date = '2026-03-29' AND f.id IS NULL
            """.trimIndent()
        )
        assertEquals(0L, danglingMealReferences)

        val distinctBulkMealFoodIds = migratedDb.queryLong(
            "SELECT COUNT(DISTINCT food_id) FROM meal_entry WHERE local_date = '2026-03-29'"
        )
        assertEquals(groupCount.toLong(), distinctBulkMealFoodIds)

        val latestOverrideTimestamp = migratedDb.queryLong(
            "SELECT updated_at FROM nutrition_override WHERE food_id = $actualSurvivorId"
        )
        assertTrue(latestOverrideTimestamp >= 20_000L)

        db.close()
    }

    @Test
    fun migration_1_2_largeDataset_performanceEvidence_reportsPercentiles() {
        val iterations = 15
        val groupCount = 240
        val duplicatesPerGroup = 8
        val durationsMs = mutableListOf<Long>()

        repeat(iterations) {
            seedLargeVersion1Database(groupCount = groupCount, duplicatesPerGroup = duplicatesPerGroup)

            val startedAtNs = System.nanoTime()
            val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
            db.openHelper.writableDatabase
            val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000
            durationsMs += elapsedMs

            db.close()
        }

        val sortedDurations = durationsMs.sorted()
        val p50Ms = percentile(sortedDurations, 50)
        val p95Ms = percentile(sortedDurations, 95)
        val worstCaseMs = sortedDurations.last()

        println(
            "MIGRATION_PERF dataset=groups:$groupCount duplicates:$duplicatesPerGroup iterations:$iterations p50_ms:$p50Ms p95_ms:$p95Ms worst_ms:$worstCaseMs samples_ms:${sortedDurations.joinToString(",")}" 
        )

        assertEquals(iterations, durationsMs.size)
        assertTrue(p50Ms > 0)
        assertTrue(p95Ms >= p50Ms)
        assertTrue(worstCaseMs >= p95Ms)
    }

    @Test
    fun migration_2_3_addsEnrichedNutrientColumns_andKeepsExistingRows() {
        seedVersion2Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        db.openHelper.writableDatabase
        val migratedDb = db.openHelper.readableDatabase

        assertTableColumnExists(migratedDb, table = "food_item", column = "saturated_fat_100")
        assertTableColumnExists(migratedDb, table = "food_item", column = "sugar_100")
        assertTableColumnExists(migratedDb, table = "food_item", column = "iron_100")
        assertTableColumnExists(migratedDb, table = "food_item", column = "calcium_100")
        assertTableColumnExists(migratedDb, table = "food_item", column = "magnesium_100")
        assertTableColumnExists(migratedDb, table = "food_item", column = "zinc_100")
        assertTableColumnExists(migratedDb, table = "food_item", column = "vitamin_c_100")
        assertTableColumnExists(migratedDb, table = "food_item", column = "vitamin_d_100")
        assertTableColumnExists(migratedDb, table = "food_item", column = "vitamin_b12_100")

        assertTableColumnExists(migratedDb, table = "meal_entry", column = "saturated_fat_total")
        assertTableColumnExists(migratedDb, table = "meal_entry", column = "sugar_total")
        assertTableColumnExists(migratedDb, table = "meal_entry", column = "iron_total")
        assertTableColumnExists(migratedDb, table = "meal_entry", column = "calcium_total")
        assertTableColumnExists(migratedDb, table = "meal_entry", column = "magnesium_total")
        assertTableColumnExists(migratedDb, table = "meal_entry", column = "zinc_total")
        assertTableColumnExists(migratedDb, table = "meal_entry", column = "vitamin_c_total")
        assertTableColumnExists(migratedDb, table = "meal_entry", column = "vitamin_d_total")
        assertTableColumnExists(migratedDb, table = "meal_entry", column = "vitamin_b12_total")

        assertTableColumnExists(migratedDb, table = "nutrition_override", column = "saturated_fat_100")
        assertTableColumnExists(migratedDb, table = "nutrition_override", column = "sugar_100")
        assertTableColumnExists(migratedDb, table = "nutrition_override", column = "iron_100")
        assertTableColumnExists(migratedDb, table = "nutrition_override", column = "calcium_100")
        assertTableColumnExists(migratedDb, table = "nutrition_override", column = "magnesium_100")
        assertTableColumnExists(migratedDb, table = "nutrition_override", column = "zinc_100")
        assertTableColumnExists(migratedDb, table = "nutrition_override", column = "vitamin_c_100")
        assertTableColumnExists(migratedDb, table = "nutrition_override", column = "vitamin_d_100")
        assertTableColumnExists(migratedDb, table = "nutrition_override", column = "vitamin_b12_100")

        assertTableColumnExists(migratedDb, table = "daily_summary", column = "saturated_fat_total")
        assertTableColumnExists(migratedDb, table = "daily_summary", column = "sugar_total")
        assertTableColumnExists(migratedDb, table = "daily_summary", column = "iron_total")
        assertTableColumnExists(migratedDb, table = "daily_summary", column = "calcium_total")
        assertTableColumnExists(migratedDb, table = "daily_summary", column = "magnesium_total")
        assertTableColumnExists(migratedDb, table = "daily_summary", column = "zinc_total")
        assertTableColumnExists(migratedDb, table = "daily_summary", column = "vitamin_c_total")
        assertTableColumnExists(migratedDb, table = "daily_summary", column = "vitamin_d_total")
        assertTableColumnExists(migratedDb, table = "daily_summary", column = "vitamin_b12_total")

        assertEquals(1L, migratedDb.queryLong("SELECT COUNT(*) FROM food_item WHERE id = 7"))
        assertEquals(1L, migratedDb.queryLong("SELECT COUNT(*) FROM meal_entry WHERE id = 700"))
        assertEquals(1L, migratedDb.queryLong("SELECT COUNT(*) FROM nutrition_override WHERE food_id = 7"))
        assertEquals(1L, migratedDb.queryLong("SELECT COUNT(*) FROM daily_summary WHERE local_date = '2026-04-01'"))

        assertEquals(1L, migratedDb.queryLong("SELECT COUNT(*) FROM meal_entry WHERE saturated_fat_total IS NULL"))
        assertEquals(1L, migratedDb.queryLong("SELECT COUNT(*) FROM food_item WHERE vitamin_b12_100 IS NULL"))

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

    private fun seedLargeVersion1Database(groupCount: Int, duplicatesPerGroup: Int) {
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

        var foodId = 1_000L
        var mealId = 10_000L
        for (group in 1..groupCount) {
            for (dup in 1..duplicatesPerGroup) {
                val lastSyncedAt = if (dup >= duplicatesPerGroup - 1) {
                    5_000L + group
                } else {
                    1_000L + dup
                }
                val overrideUpdatedAt = if (dup == duplicatesPerGroup) {
                    20_000L + group
                } else {
                    10_000L + dup
                }
                val overrideCreatedAt = 1_000L + dup

                db.execSQL(
                    """
                    INSERT INTO food_item(
                        id, source_id, source, name, brand, barcode,
                        kcal_100, carb_100, fat_100, protein_100, last_synced_at
                    ) VALUES (?, ?, 'OFF', ?, 'Bulk Brand', ?, 120.0, 20.0, 2.0, 6.0, ?)
                    """.trimIndent(),
                    arrayOf(
                        foodId,
                        "off-bulk-$group",
                        "Bulk Food $group",
                        "8999999${group.toString().padStart(3, '0')}",
                        lastSyncedAt,
                    )
                )

                db.execSQL(
                    """
                    INSERT INTO nutrition_override(
                        food_id, kcal_100, carb_100, fat_100, protein_100, note, created_at, updated_at
                    ) VALUES (?, 121.0, 19.0, 2.5, 6.2, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        foodId,
                        "bulk-override-g$group-d$dup",
                        overrideCreatedAt,
                        overrideUpdatedAt,
                    )
                )

                db.execSQL(
                    """
                    INSERT INTO meal_entry(
                        id, local_date, timezone_offset_min, meal_type, food_id,
                        quantity_value, quantity_unit, resolved_source,
                        kcal_total, carb_total, fat_total, protein_total,
                        created_at, updated_at
                    ) VALUES (?, '2026-03-29', 60, 'lunch', ?, 100.0, 'g', 'OVERRIDE', 121.0, 19.0, 2.5, 6.2, 1000, 1000)
                    """.trimIndent(),
                    arrayOf(mealId, foodId)
                )

                foodId += 1
                mealId += 1
            }
        }

        db.close()
        helper.close()
    }

    private fun seedVersion2Database() {
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersion2Schema(db)
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
                kcal_100, carb_100, fat_100, protein_100,
                last_synced_at, canonical_external_key
            ) VALUES
                (7, 'off-v2', 'OFF', 'Rice', 'Brand V2', '9988776655443', 360.0, 78.0, 1.0, 7.0, 2000, 'off::sid:off-v2')
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO nutrition_override(
                food_id, kcal_100, carb_100, fat_100, protein_100, note, created_at, updated_at
            ) VALUES
                (7, 355.0, 77.0, 1.2, 7.1, 'v2-override', 1000, 1100)
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
                (700, '2026-04-01', 120, 'lunch', 7, 100.0, 'g', 'OVERRIDE', 355.0, 77.0, 1.2, 7.1, 1200, 1200)
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO daily_summary(
                local_date, kcal_target, kcal_intake, kcal_burned, kcal_remaining,
                carb_total, fat_total, protein_total, updated_at
            ) VALUES
                ('2026-04-01', 2200.0, 355.0, 0.0, 1845.0, 77.0, 1.2, 7.1, 1200)
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

    private fun createVersion2Schema(db: SupportSQLiteDatabase) {
        createVersion1Schema(db)
        db.execSQL("ALTER TABLE food_item ADD COLUMN canonical_external_key TEXT")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_food_item_canonical_external_key ON food_item(canonical_external_key)"
        )
    }

    private fun assertTableColumnExists(db: SupportSQLiteDatabase, table: String, column: String) {
        val sql = "PRAGMA table_info($table)"
        db.query(sql).use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (name == column) {
                    found = true
                    val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                    assertEquals("Column $table.$column must be nullable", 0, notNull)
                    break
                }
            }
            assertTrue("Expected column $column in table $table", found)
        }
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

    private fun percentile(sortedValues: List<Long>, percentile: Int): Long {
        check(sortedValues.isNotEmpty()) { "Cannot compute percentile from empty values" }
        check(percentile in 0..100) { "Percentile must be in [0, 100]" }

        val rank = kotlin.math.ceil((percentile / 100.0) * sortedValues.size).toInt().coerceAtLeast(1)
        return sortedValues[rank - 1]
    }
}
