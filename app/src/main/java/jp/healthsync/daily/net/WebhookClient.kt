package jp.healthsync.daily.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import jp.healthsync.daily.settings.AuthHeaderDecision
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class WebhookResponse(
    val status: Int,
    val ok: Boolean,
    val detail: String,
)

class WebhookClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    fun post(url: String, auth: AuthHeaderDecision.Include?, json: String): WebhookResponse {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json; charset=utf-8")
            .post(json.toRequestBody(JSON))
        if (auth != null) {
            builder.header(auth.name, auth.value)
        }
        http.newCall(builder.build()).execute().use { response ->
            val body = try {
                response.body?.string().orEmpty()
            } catch (_: IOException) {
                ""
            }
            return WebhookResponse(
                status = response.code,
                ok = response.isSuccessful,
                detail = body.replace("\n", " ").take(180),
            )
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val USER_AGENT = "HealthDaily/1.0"
    }
}
