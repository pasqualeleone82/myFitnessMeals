package com.myfitnessmeals.app.integration.off

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import org.json.JSONException
import org.json.JSONObject

class OffHttpCatalogClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) : OffCatalogClient {
    override suspend fun searchByText(
        query: String,
        limit: Int,
    ): OffCatalogClientResult<List<OffRemoteProduct>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return OffCatalogClientResult.NotFound
        }

        return withContext(ioDispatcher) {
            val endpoint = buildUrl(
                path = "/cgi/search.pl",
                params = mapOf(
                    "search_terms" to normalizedQuery,
                    "search_simple" to "1",
                    "action" to "process",
                    "json" to "1",
                    "page_size" to limit.coerceIn(1, 100).toString(),
                ),
            )

            when (val response = executeGet(endpoint)) {
                is HttpResult.TransportError -> OffCatalogClientResult.Error(response.error)
                is HttpResult.Http -> {
                    when {
                        response.statusCode == HttpURLConnection.HTTP_NOT_FOUND -> OffCatalogClientResult.NotFound
                        response.statusCode == HTTP_RATE_LIMIT -> OffCatalogClientResult.Error(OffCatalogError.RateLimit)
                        response.statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT -> OffCatalogClientResult.Error(OffCatalogError.Timeout)
                        response.statusCode in 500..599 -> OffCatalogClientResult.Error(OffCatalogError.Unavailable)
                        response.statusCode !in 200..299 -> OffCatalogClientResult.Error(OffCatalogError.Unavailable)
                        else -> parseSearchPayload(response.body)
                    }
                }
            }
        }
    }

    override suspend fun searchByBarcode(barcode: String): OffCatalogClientResult<OffRemoteProduct> {
        val normalizedBarcode = normalizeBarcodeOrNull(barcode)
        if (normalizedBarcode == null) {
            return OffCatalogClientResult.NotFound
        }

        return withContext(ioDispatcher) {
            val endpoint = buildUrl(path = "/api/v2/product/$normalizedBarcode.json")

            when (val response = executeGet(endpoint)) {
                is HttpResult.TransportError -> OffCatalogClientResult.Error(response.error)
                is HttpResult.Http -> {
                    when {
                        response.statusCode == HttpURLConnection.HTTP_NOT_FOUND -> OffCatalogClientResult.NotFound
                        response.statusCode == HTTP_RATE_LIMIT -> OffCatalogClientResult.Error(OffCatalogError.RateLimit)
                        response.statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT -> OffCatalogClientResult.Error(OffCatalogError.Timeout)
                        response.statusCode in 500..599 -> OffCatalogClientResult.Error(OffCatalogError.Unavailable)
                        response.statusCode !in 200..299 -> OffCatalogClientResult.Error(OffCatalogError.Unavailable)
                        else -> parseBarcodePayload(response.body)
                    }
                }
            }
        }
    }

    private fun parseSearchPayload(body: String): OffCatalogClientResult<List<OffRemoteProduct>> {
        return try {
            val root = JSONObject(body)
            val productsJson = root.optJSONArray("products")
                ?: return OffCatalogClientResult.Error(OffCatalogError.MalformedPayload)

            val products = mutableListOf<OffRemoteProduct>()
            for (index in 0 until productsJson.length()) {
                val productObject = productsJson.optJSONObject(index) ?: continue
                val mapped = mapProduct(productObject) ?: continue
                products.add(mapped)
            }

            if (products.isEmpty()) {
                OffCatalogClientResult.NotFound
            } else {
                OffCatalogClientResult.Success(products)
            }
        } catch (_: JSONException) {
            OffCatalogClientResult.Error(OffCatalogError.MalformedPayload)
        }
    }

    private fun parseBarcodePayload(body: String): OffCatalogClientResult<OffRemoteProduct> {
        return try {
            val root = JSONObject(body)
            val status = root.optInt("status", -1)
            if (status == 0) {
                return OffCatalogClientResult.NotFound
            }
            if (status != 1) {
                return OffCatalogClientResult.Error(OffCatalogError.MalformedPayload)
            }

            val productObject = root.optJSONObject("product")
                ?: return OffCatalogClientResult.Error(OffCatalogError.MalformedPayload)
            val mapped = mapProduct(productObject)
                ?: return OffCatalogClientResult.Error(OffCatalogError.MalformedPayload)

            OffCatalogClientResult.Success(mapped)
        } catch (_: JSONException) {
            OffCatalogClientResult.Error(OffCatalogError.MalformedPayload)
        }
    }

    private fun mapProduct(productObject: JSONObject): OffRemoteProduct? {
        val name = productObject.optString("product_name").trim()
            .ifBlank { productObject.optString("generic_name").trim() }
        if (name.isBlank()) {
            return null
        }

        val barcode = productObject.optString("code").trim().ifBlank { null }
        val externalId = productObject.optString("_id").trim().ifBlank {
            barcode ?: return null
        }

        val brand = productObject.optString("brands").trim()
            .ifBlank { null }
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.ifBlank { null }

        val nutriments = productObject.optJSONObject("nutriments") ?: JSONObject()
        val nutrients = mapNutrients(nutriments)

        return OffRemoteProduct(
            externalId = externalId,
            name = name,
            brand = brand,
            barcode = barcode,
            nutrients = nutrients,
        )
    }

    private fun mapNutrients(nutriments: JSONObject): OffRemoteNutrients {
        val kcalPer100g = nutriments.readDouble("energy-kcal_100g")
        val carbPer100g = nutriments.readDouble("carbohydrates_100g")
        val fatPer100g = nutriments.readDouble("fat_100g")
        val proteinPer100g = nutriments.readDouble("proteins_100g")

        val kcalPer100ml = nutriments.readDouble("energy-kcal_100ml")
        val carbPer100ml = nutriments.readDouble("carbohydrates_100ml")
        val fatPer100ml = nutriments.readDouble("fat_100ml")
        val proteinPer100ml = nutriments.readDouble("proteins_100ml")

        val hasMlValues = listOf(kcalPer100ml, carbPer100ml, fatPer100ml, proteinPer100ml).any { it != null }
        val hasGramValues = listOf(kcalPer100g, carbPer100g, fatPer100g, proteinPer100g).any { it != null }
        val referenceUnit = if (hasMlValues && !hasGramValues) {
            OffReferenceUnit.MILLILITER
        } else {
            OffReferenceUnit.GRAM
        }

        return OffRemoteNutrients(
            kcalPer100g = kcalPer100g,
            carbPer100g = carbPer100g,
            fatPer100g = fatPer100g,
            proteinPer100g = proteinPer100g,
            kcalPer100ml = kcalPer100ml,
            carbPer100ml = carbPer100ml,
            fatPer100ml = fatPer100ml,
            proteinPer100ml = proteinPer100ml,
            referenceUnit = referenceUnit,
        )
    }

    private fun buildUrl(path: String, params: Map<String, String> = emptyMap()): URL {
        val normalizedBase = baseUrl.trimEnd('/')
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        if (params.isEmpty()) {
            return URL(normalizedBase + normalizedPath)
        }

        val query = params.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
        return URL("$normalizedBase$normalizedPath?$query")
    }

    private fun executeGet(url: URL): HttpResult {
        val connection = connectionFactory(url)
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResult.Http(statusCode = statusCode, body = body)
        } catch (_: SocketTimeoutException) {
            HttpResult.TransportError(OffCatalogError.Timeout)
        } catch (_: InterruptedIOException) {
            HttpResult.TransportError(OffCatalogError.Timeout)
        } catch (_: IOException) {
            HttpResult.TransportError(OffCatalogError.Unavailable)
        } finally {
            connection.disconnect()
        }
    }

    private sealed class HttpResult {
        data class Http(val statusCode: Int, val body: String) : HttpResult()

        data class TransportError(val error: OffCatalogError) : HttpResult()
    }

    private fun JSONObject.readDouble(key: String): Double? {
        if (!has(key)) {
            return null
        }

        val raw = opt(key)
        val numeric = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }

        if (numeric == null || !numeric.isFinite() || numeric < 0.0) {
            return null
        }
        return numeric
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun normalizeBarcodeOrNull(rawBarcode: String): String? {
        val candidate = rawBarcode.trim()
        if (candidate.isBlank()) {
            return null
        }
        if (!candidate.all { it.isDigit() }) {
            return null
        }
        if (candidate.length !in VALID_BARCODE_LENGTHS) {
            return null
        }
        return candidate
    }

    private companion object {
        private const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org"
        private const val DEFAULT_TIMEOUT_MILLIS = 2_500
        private const val HTTP_RATE_LIMIT = 429
        private const val USER_AGENT = "myFitnessMeals/0.1 (android)"
        private val VALID_BARCODE_LENGTHS = setOf(8, 12, 13, 14)
    }
}
