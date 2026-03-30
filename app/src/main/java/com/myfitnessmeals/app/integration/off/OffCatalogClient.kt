package com.myfitnessmeals.app.integration.off

interface OffCatalogClient {
    suspend fun searchByText(query: String, limit: Int): OffCatalogClientResult<List<OffRemoteProduct>>

    suspend fun searchByBarcode(barcode: String): OffCatalogClientResult<OffRemoteProduct>
}

sealed class OffCatalogClientResult<out T> {
    data class Success<T>(val data: T) : OffCatalogClientResult<T>()

    data object NotFound : OffCatalogClientResult<Nothing>()

    data class Error(val error: OffCatalogError) : OffCatalogClientResult<Nothing>()
}

sealed class OffCatalogError {
    data object Timeout : OffCatalogError()

    data object RateLimit : OffCatalogError()

    data object Unavailable : OffCatalogError()

    data object MalformedPayload : OffCatalogError()
}
