package com.example.corepostemergencybutton.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class CorePostApi(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    suspend fun checkStatus(config: CorePostConfig): MobileStatus =
        execute(
            method = "GET",
            path = CHECK_PATH,
            config = config,
            requestBody = null,
            serializer = MobileStatusResponse.serializer(),
        ).second.toDomain()

    suspend fun lock(config: CorePostConfig): MobileActionResult =
        mutate(path = LOCK_PATH, config = config)

    suspend fun unlock(config: CorePostConfig): MobileActionResult =
        mutate(path = UNLOCK_PATH, config = config)

    private suspend fun mutate(path: String, config: CorePostConfig): MobileActionResult {
        val (code, payload) = execute(
            method = "POST",
            path = path,
            config = config,
            requestBody = EMPTY_BODY,
            serializer = MobileActionResponse.serializer(),
        )
        return MobileActionResult(
            httpStatus = code,
            detail = payload.detail ?: "Операция выполнена",
            currentState = payload.currentState?.let(DeviceState::fromWire),
        )
    }

    private suspend fun <T> execute(
        method: String,
        path: String,
        config: CorePostConfig,
        requestBody: RequestBody?,
        serializer: KSerializer<T>,
    ): Pair<Int, T> = withContext(Dispatchers.IO) {
        val headers = SignatureFactory.buildHeaders(
            method = method,
            path = path,
            emergencyId = config.emergencyId,
            panicSecret = config.panicSecret,
        )
        val requestBuilder = Request.Builder()
            .url(config.normalizedBaseUrl + path)

        headers.forEach { (name, value) ->
            requestBuilder.addHeader(name, value)
        }

        if (method == "GET") {
            requestBuilder.get()
        } else {
            requestBuilder.method(method, requestBody ?: EMPTY_BODY)
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw CorePostApiException(
                        statusCode = response.code,
                        message = responseText.toApiErrorMessage(json),
                    )
                }
                val parsed = json.decodeFromString(serializer, responseText)
                response.code to parsed
            }
        } catch (ioException: IOException) {
            throw CorePostApiException(
                statusCode = null,
                message = "Не удалось связаться с сервером: ${ioException.localizedMessage ?: "неизвестная ошибка"}",
            )
        }
    }

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody("application/json".toMediaType())
        private const val CHECK_PATH = "/mobile/check"
        private const val LOCK_PATH = "/mobile/lock"
        private const val UNLOCK_PATH = "/mobile/unlock"
    }
}

class CorePostApiException(
    val statusCode: Int?,
    override val message: String,
) : IOException(message)

private fun String.toApiErrorMessage(json: Json): String {
    if (isBlank()) {
        return "Сервер вернул пустой ответ"
    }
    return runCatching {
        val detail = json.parseToJsonElement(this)
            .jsonObject["detail"]
            ?: return@runCatching this
        when (detail) {
            is JsonPrimitive -> detail.contentOrNull ?: detail.toString()
            is JsonArray -> detail.joinToString(separator = "; ") { element ->
                when (element) {
                    is JsonPrimitive -> element.contentOrNull ?: element.toString()
                    is JsonObject -> json.decodeFromJsonElement<Map<String, String?>>(element)
                        .entries
                        .joinToString(separator = ", ") { (key, value) -> "$key=${value ?: ""}" }
                    else -> element.toString()
                }
            }
            else -> detail.toString()
        }
    }.getOrElse { this }
}
