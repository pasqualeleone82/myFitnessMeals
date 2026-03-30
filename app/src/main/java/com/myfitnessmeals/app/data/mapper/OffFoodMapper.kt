package com.myfitnessmeals.app.data.mapper

import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.integration.off.OffReferenceUnit
import com.myfitnessmeals.app.integration.off.OffRemoteProduct

object OffFoodMapper {
    fun toFoodItemEntity(product: OffRemoteProduct, syncedAtEpochMillis: Long): FoodItemEntity? {
        val normalizedName = product.name.trim()
        if (normalizedName.isBlank()) {
            return null
        }

        return FoodItemEntity(
            sourceId = product.externalId,
            source = "OFF",
            name = normalizedName,
            brand = product.brand?.trim()?.ifBlank { null },
            barcode = product.barcode?.trim()?.ifBlank { null },
            kcal100 = preferredValue(product, isKcal = true),
            carb100 = preferredValue(product, macro = Macro.CARB),
            fat100 = preferredValue(product, macro = Macro.FAT),
            protein100 = preferredValue(product, macro = Macro.PROTEIN),
            lastSyncedAt = syncedAtEpochMillis,
        )
    }

    private fun preferredValue(product: OffRemoteProduct, isKcal: Boolean): Double? {
        val nutrients = product.nutrients
        val primary = if (nutrients.referenceUnit == OffReferenceUnit.MILLILITER) {
            nutrients.kcalPer100ml
        } else {
            nutrients.kcalPer100g
        }
        val fallback = if (nutrients.referenceUnit == OffReferenceUnit.MILLILITER) {
            nutrients.kcalPer100g
        } else {
            nutrients.kcalPer100ml
        }
        return normalizeNonNegative(primary ?: fallback)
    }

    private fun preferredValue(product: OffRemoteProduct, macro: Macro): Double? {
        val nutrients = product.nutrients
        val (primary, fallback) = if (nutrients.referenceUnit == OffReferenceUnit.MILLILITER) {
            when (macro) {
                Macro.CARB -> nutrients.carbPer100ml to nutrients.carbPer100g
                Macro.FAT -> nutrients.fatPer100ml to nutrients.fatPer100g
                Macro.PROTEIN -> nutrients.proteinPer100ml to nutrients.proteinPer100g
            }
        } else {
            when (macro) {
                Macro.CARB -> nutrients.carbPer100g to nutrients.carbPer100ml
                Macro.FAT -> nutrients.fatPer100g to nutrients.fatPer100ml
                Macro.PROTEIN -> nutrients.proteinPer100g to nutrients.proteinPer100ml
            }
        }
        return normalizeNonNegative(primary ?: fallback)
    }

    private fun normalizeNonNegative(value: Double?): Double? {
        if (value == null) {
            return null
        }
        return if (value >= 0.0) value else null
    }

    private enum class Macro {
        CARB,
        FAT,
        PROTEIN,
    }
}
