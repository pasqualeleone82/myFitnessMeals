package com.myfitnessmeals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: FoodItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(food: FoodItemEntity): Long

    @Update
    suspend fun update(food: FoodItemEntity): Int

    @Query("SELECT * FROM food_item WHERE id = :id")
    suspend fun getById(id: Long): FoodItemEntity?

    @Query("SELECT * FROM food_item WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodItemEntity?

    @Query("SELECT * FROM food_item WHERE source = 'OFF' AND source_id = :sourceId LIMIT 1")
    suspend fun getOffBySourceId(sourceId: String): FoodItemEntity?

    @Query("SELECT * FROM food_item WHERE source = 'OFF' AND barcode = :barcode LIMIT 1")
    suspend fun getOffByBarcode(barcode: String): FoodItemEntity?

    @Query("SELECT * FROM food_item WHERE canonical_external_key = :canonicalKey LIMIT 1")
    suspend fun getByCanonicalExternalKey(canonicalKey: String): FoodItemEntity?

    @Query("SELECT COUNT(*) FROM food_item WHERE canonical_external_key = :canonicalKey")
    suspend fun countByCanonicalExternalKey(canonicalKey: String): Int

    @Query(
        "SELECT * FROM food_item " +
            "WHERE source = 'OFF' " +
            "AND lower(name) = lower(:name) " +
            "AND ((brand IS NULL AND :brand IS NULL) OR lower(brand) = lower(:brand)) " +
            "LIMIT 1"
    )
    suspend fun getOffByNameBrand(name: String, brand: String?): FoodItemEntity?

    @Query("SELECT * FROM food_item WHERE name LIKE :query ESCAPE '\\' ORDER BY last_synced_at DESC LIMIT :limit")
    suspend fun searchByName(query: String, limit: Int): List<FoodItemEntity>

    @Transaction
    suspend fun upsertOffConflictAware(food: FoodItemEntity): Long {
        val canonicalKey = food.canonicalExternalKey ?: return upsert(food)
        val candidate = food.copy(canonicalExternalKey = canonicalKey)

        val insertedId = insertIgnore(candidate)
        if (insertedId != -1L) {
            return insertedId
        }

        val existing = getByCanonicalExternalKey(canonicalKey)
            ?: throw IllegalStateException("Missing OFF row after unique-key conflict for key=$canonicalKey")

        val merged = mergeOffEntity(existing, candidate)
        update(merged)
        return existing.id
    }
}

private fun mergeOffEntity(existing: FoodItemEntity, incoming: FoodItemEntity): FoodItemEntity {
    return existing.copy(
        sourceId = incoming.sourceId.normalizedOrNull() ?: existing.sourceId,
        source = incoming.source,
        name = incoming.name.ifBlank { existing.name },
        brand = incoming.brand.normalizedOrNull() ?: existing.brand,
        barcode = incoming.barcode.normalizedOrNull() ?: existing.barcode,
        kcal100 = incoming.kcal100 ?: existing.kcal100,
        carb100 = incoming.carb100 ?: existing.carb100,
        fat100 = incoming.fat100 ?: existing.fat100,
        protein100 = incoming.protein100 ?: existing.protein100,
        lastSyncedAt = maxOf(existing.lastSyncedAt, incoming.lastSyncedAt),
        canonicalExternalKey = incoming.canonicalExternalKey ?: existing.canonicalExternalKey,
    )
}

private fun String?.normalizedOrNull(): String? = this?.trim()?.ifBlank { null }
