package com.myfitnessmeals.app.integration.off

data class OffRemoteProduct(
    val externalId: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val nutrients: OffRemoteNutrients,
)

data class OffRemoteNutrients(
    val kcalPer100g: Double?,
    val carbPer100g: Double?,
    val fatPer100g: Double?,
    val proteinPer100g: Double?,
    val kcalPer100ml: Double? = null,
    val carbPer100ml: Double? = null,
    val fatPer100ml: Double? = null,
    val proteinPer100ml: Double? = null,
    val referenceUnit: OffReferenceUnit = OffReferenceUnit.GRAM,
)

enum class OffReferenceUnit {
    GRAM,
    MILLILITER,
}
