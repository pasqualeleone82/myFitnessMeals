package com.myfitnessmeals.app.data.repository

import com.myfitnessmeals.app.data.local.FoodDao
import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.mapper.OffFoodMapper
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.integration.off.OffCatalogClient
import com.myfitnessmeals.app.integration.off.OffCatalogClientResult
import com.myfitnessmeals.app.integration.off.OffCatalogError
import com.myfitnessmeals.app.integration.off.OffHttpCatalogClient

class LocalFoodRepository(
    private val foodDao: FoodDao,
    private val offCatalogClient: OffCatalogClient? = OffHttpCatalogClient(),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun upsertFood(food: FoodItemEntity): Long {
        val normalized = food.withCanonicalExternalKey()
        return if (normalized.source.equals(OFF_SOURCE, ignoreCase = true) && normalized.canonicalExternalKey != null) {
            foodDao.upsertOffConflictAware(normalized)
        } else {
            foodDao.upsert(normalized)
        }
    }

    suspend fun getFoodById(id: Long): FoodItemEntity? = foodDao.getById(id)

    suspend fun getFoodByBarcode(barcode: String): FoodItemEntity? = foodDao.getByBarcode(barcode)

    suspend fun searchFoodByText(query: String, limit: Int = 20): FoodLookupResult<List<FoodItemEntity>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return FoodLookupResult.NotFound
        }
        val normalizedLimit = normalizeSearchLimit(limit)
        val escapedLikeQuery = toEscapedLikeContainsQuery(normalizedQuery)

        val cached = foodDao.searchByName(escapedLikeQuery, normalizedLimit)
        if (cached.isNotEmpty()) {
            return FoodLookupResult.Success(
                data = cached,
                source = ResolvedSource.CACHE,
            )
        }

        val client = offCatalogClient ?: return FoodLookupResult.Error(OffCatalogError.Unavailable)
        return when (val remote = client.searchByText(normalizedQuery, normalizedLimit)) {
            is OffCatalogClientResult.Success -> {
                val syncedAt = nowEpochMillis()
                val mapped = remote.data
                    .mapNotNull { product -> OffFoodMapper.toFoodItemEntity(product, syncedAt) }
                    .map { entity -> entity.withCanonicalExternalKey() }
                    .distinctBy { entity ->
                        entity.canonicalExternalKey ?: "fallback:${entity.name.trim().lowercase()}|${entity.brand?.trim()?.lowercase().orEmpty()}"
                    }
                if (mapped.isEmpty()) {
                    FoodLookupResult.NotFound
                } else {
                    val persisted = mapped.map { entity ->
                        val id = persistOffEntityIdempotent(entity)
                        entity.copy(id = id)
                    }
                    FoodLookupResult.Success(
                        data = persisted,
                        source = ResolvedSource.OFF,
                    )
                }
            }
            is OffCatalogClientResult.NotFound -> FoodLookupResult.NotFound
            is OffCatalogClientResult.Error -> FoodLookupResult.Error(remote.error)
        }
    }

    suspend fun searchFoodByBarcode(barcode: String): FoodLookupResult<FoodItemEntity> {
        val normalizedBarcode = barcode.trim()
        if (normalizedBarcode.isBlank()) {
            return FoodLookupResult.NotFound
        }

        val cached = foodDao.getByBarcode(normalizedBarcode)
        if (cached != null) {
            return FoodLookupResult.Success(
                data = cached,
                source = ResolvedSource.CACHE,
            )
        }

        val client = offCatalogClient ?: return FoodLookupResult.Error(OffCatalogError.Unavailable)
        return when (val remote = client.searchByBarcode(normalizedBarcode)) {
            is OffCatalogClientResult.Success -> {
                val mapped = OffFoodMapper.toFoodItemEntity(
                    product = remote.data,
                    syncedAtEpochMillis = nowEpochMillis(),
                )?.withCanonicalExternalKey() ?: return FoodLookupResult.NotFound
                val id = persistOffEntityIdempotent(mapped)
                FoodLookupResult.Success(
                    data = mapped.copy(id = id),
                    source = ResolvedSource.OFF,
                )
            }
            is OffCatalogClientResult.NotFound -> FoodLookupResult.NotFound
            is OffCatalogClientResult.Error -> FoodLookupResult.Error(remote.error)
        }
    }

    private fun normalizeSearchLimit(limit: Int): Int = limit.coerceIn(MIN_SEARCH_LIMIT, MAX_SEARCH_LIMIT)

    private suspend fun persistOffEntityIdempotent(entity: FoodItemEntity): Long {
        return foodDao.upsertOffConflictAware(entity.withCanonicalExternalKey())
    }

    private fun FoodItemEntity.withCanonicalExternalKey(): FoodItemEntity {
        return copy(canonicalExternalKey = canonicalExternalKey())
    }

    private fun FoodItemEntity.canonicalExternalKey(): String? {
        val normalizedSource = source.trim().lowercase().ifBlank { return null }
        val normalizedSourceId = sourceId?.trim()?.lowercase()?.ifBlank { null }
        if (normalizedSourceId != null) {
            return "$normalizedSource::sid:$normalizedSourceId"
        }

        val normalizedBarcode = barcode?.trim()?.lowercase()?.ifBlank { null }
        if (normalizedBarcode != null) {
            return "bar:$normalizedBarcode"
        }

        return null
    }

    private fun toEscapedLikeContainsQuery(raw: String): String {
        val escaped = raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    private companion object {
        private const val MIN_SEARCH_LIMIT = 1
        private const val MAX_SEARCH_LIMIT = 100
        private const val OFF_SOURCE = "OFF"
    }
}

sealed class FoodLookupResult<out T> {
    data class Success<T>(
        val data: T,
        val source: ResolvedSource,
    ) : FoodLookupResult<T>()

    data class Error(val error: OffCatalogError) : FoodLookupResult<Nothing>()

    data object NotFound : FoodLookupResult<Nothing>()
}
