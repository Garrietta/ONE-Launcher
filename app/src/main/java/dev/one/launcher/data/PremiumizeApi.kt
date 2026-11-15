package dev.one.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Premiumize API client — OWN CLOUD ONLY.
 *
 * Allowed endpoints:
 * - GET /account/info
 * - GET /folder/list (browse your own cloud folders)
 * - GET /folder/search (searches ONLY your own storage)
 * - GET /item/details (a file already in your cloud; returns its link)
 * - GET /item/listall (flat list of files you own)
 *
 * Never called here: /transfer/create, /transfer/directdl, /cache/*,
 * or anything involving magnets, torrents, or hoster links. There is no
 * "download this game from the internet" in ONE — the Download action
 * only pulls a file already in your Premiumize cloud to this device.
 */
@Serializable
data class PremiumizeAccount(
    val customer_id: String = "",
    val premium_until: Long = 0,
    val status: String = ""
)

@Serializable
data class PremiumizeItem(
    val id: String = "",
    val name: String = "",
    val type: String = "file",
    val size: Long = 0,
    val created_at: Long = 0,
    val link: String? = null
)

@Serializable
data class PremiumizeBreadcrumb(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class FolderListResponse(
    val status: String = "",
    val content: List<PremiumizeItem> = emptyList(),
    val breadcrumbs: List<PremiumizeBreadcrumb> = emptyList()
)

@Serializable
data class FolderSearchResponse(
    val status: String = "",
    val content: List<PremiumizeItem> = emptyList()
)

@Serializable
data class ItemListAllResponse(
    val status: String = "",
    val files: List<PremiumizeItem> = emptyList()
)

sealed class PremiumizeResult<out T> {
    data class Success<T>(val value: T) : PremiumizeResult<T>()
    data class Failure(val message: String) : PremiumizeResult<Nothing>()
}

class PremiumizeApi(private val apiKey: String) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun request(path: String): Request = Request.Builder()
        .url("https://www.premiumize.me/api$path")
        .header("Authorization", "Bearer $apiKey")
        .build()

    private suspend fun <T> get(path: String, parse: (String) -> T): PremiumizeResult<T> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request(path)).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        if (response.code == 401 || response.code == 403) {
                            return@withContext PremiumizeResult.Failure(
                                "Premiumize did not accept that API key."
                            )
                        }
                        return@withContext PremiumizeResult.Failure(
                            "Premiumize returned HTTP ${response.code}"
                        )
                    }
                    val decoded = try {
                        parse(body)
                    } catch (e: Exception) {
                        return@withContext PremiumizeResult.Failure(
                            "Could not read the Premiumize response"
                        )
                    }
                    PremiumizeResult.Success(decoded)
                }
            } catch (e: Exception) {
                PremiumizeResult.Failure("Network error: ${e.message ?: "unknown"}")
            }
        }

    suspend fun accountInfo(): PremiumizeResult<PremiumizeAccount> =
        get("/account/info") { body ->
            val obj = json.parseToJsonElement(body).jsonObject
            PremiumizeAccount(
                customer_id = obj["customer_id"]?.jsonPrimitive?.contentOrNull ?: "",
                premium_until = obj["premium_until"]?.jsonPrimitive?.longOrNull ?: 0L,
                status = obj["status"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        }

    suspend fun listFolder(id: String? = null): PremiumizeResult<FolderListResponse> {
        val query = buildString {
            append("/folder/list?includebreadcrumbs=true")
            if (id != null) append("&id=").append(id)
        }
        return get(query) { json.decodeFromString(FolderListResponse.serializer(), it) }
    }

    suspend fun searchOwnCloud(query: String): PremiumizeResult<FolderSearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return get("/folder/search?q=$encoded") {
            json.decodeFromString(FolderSearchResponse.serializer(), it)
        }
    }

    suspend fun itemDetails(id: String): PremiumizeResult<PremiumizeItem> {
        val encoded = java.net.URLEncoder.encode(id, "UTF-8")
        return get("/item/details?id=$encoded") {
            json.decodeFromString(PremiumizeItem.serializer(), it)
        }
    }

    suspend fun listAll(): PremiumizeResult<ItemListAllResponse> =
        get("/item/listall") { json.decodeFromString(ItemListAllResponse.serializer(), it) }
}
