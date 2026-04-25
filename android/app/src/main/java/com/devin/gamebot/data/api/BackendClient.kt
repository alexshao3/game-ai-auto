package com.devin.gamebot.data.api

import android.util.Log
import com.devin.gamebot.BuildConfig
import com.devin.gamebot.data.api.models.GenerateRecipeResponse
import com.devin.gamebot.data.api.models.VisionResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Talks to the FastAPI proxy. We keep one process-wide instance because Ktor's
 * CIO engine maintains a connection pool internally.
 */
class BackendClient(private val baseUrl: String = BuildConfig.BACKEND_URL) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    /** POST /v1/llm/vision — single-image VLM call used by the executor. */
    suspend fun vision(imageFile: File, instruction: String, expectJson: Boolean = true): VisionResponse =
        withContext(Dispatchers.IO) {
            val response = client.post("$baseUrl/v1/llm/vision") {
                setBody(MultiPartFormDataContent(formData {
                    append(
                        "image",
                        imageFile.readBytes(),
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"frame.jpg\"")
                        },
                    )
                    append("instruction", instruction)
                    append("expect_json", expectJson.toString())
                }))
            }
            require(response.status == HttpStatusCode.OK) {
                "Vision call failed: ${response.status} ${response.bodyAsText()}"
            }
            json.decodeFromString(VisionResponse.serializer(), response.bodyAsText())
        }

    /** POST /v1/recipe/generate — multi-frame recipe extraction. */
    suspend fun generateRecipe(
        frames: List<File>,
        sessionName: String,
        gamePackage: String? = null,
    ): GenerateRecipeResponse = withContext(Dispatchers.IO) {
        val response = client.post("$baseUrl/v1/recipe/generate") {
            setBody(MultiPartFormDataContent(formData {
                frames.forEachIndexed { idx, file ->
                    append(
                        "frames",
                        file.readBytes(),
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"frame_${idx}.jpg\"")
                        },
                    )
                }
                append("session_name", sessionName)
                gamePackage?.let { append("game_package", it) }
            }))
        }
        require(response.status == HttpStatusCode.OK) {
            "Recipe generation failed: ${response.status} ${response.bodyAsText()}"
        }
        json.decodeFromString(GenerateRecipeResponse.serializer(), response.bodyAsText())
    }

    companion object {
        private const val TAG = "BackendClient"

        @Volatile private var instance: BackendClient? = null

        fun get(): BackendClient = instance ?: synchronized(this) {
            instance ?: BackendClient().also {
                instance = it
                Log.i(TAG, "BackendClient initialised at ${BuildConfig.BACKEND_URL}")
            }
        }
    }
}
