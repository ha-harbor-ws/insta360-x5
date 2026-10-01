package ws.harbor.insta360x5.osc

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class OscException(message: String) : Exception(message)

data class CameraFile(
    val name: String,
    val fileUrl: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateTime: String,
)

class OscClient(
    private val host: String,
    private val port: Int,
    timeoutSec: Long = 30,
) {
    private val base = "http://$host:$port"
    private val jsonType = "application/json;charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutSec, TimeUnit.SECONDS)
        .readTimeout(timeoutSec, TimeUnit.SECONDS)
        .writeTimeout(timeoutSec, TimeUnit.SECONDS)
        .build()

    fun info(): JSONObject = getJson("/osc/info")

    fun state(): JSONObject = postJson("/osc/state", null)

    fun execute(payload: JSONObject): JSONObject {
        val result = postJson("/osc/commands/execute", payload)
        raiseIfError(result)
        return result
    }

    fun commandStatus(id: String): JSONObject {
        val body = JSONObject().put("id", id)
        val result = postJson("/osc/commands/status", body)
        raiseIfError(result)
        return result
    }

    fun listFiles(entryCount: Int = 20): Pair<List<CameraFile>, Int> {
        val payload = JSONObject()
            .put("name", "camera.listFiles")
            .put(
                "parameters",
                JSONObject()
                    .put("fileType", "image")
                    .put("entryCount", entryCount)
                    .put("maxThumbSize", 0),
            )
        val result = execute(payload)
        val results = result.optJSONObject("results") ?: JSONObject()
        val entries = results.optJSONArray("entries") ?: JSONArray()
        val total = results.optInt("totalEntries", entries.length())
        val files = mutableListOf<CameraFile>()
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            files += CameraFile(
                name = e.optString("name", "?"),
                fileUrl = e.optString("fileUrl", ""),
                size = e.optLong("size", 0),
                width = e.optInt("width", 0),
                height = e.optInt("height", 0),
                dateTime = e.optString("dateTimeZone", e.optString("dateTime", "?")),
            )
        }
        return files to total
    }

    fun deleteFiles(urls: List<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        val payload = JSONObject()
            .put("name", "camera.delete")
            .put("parameters", JSONObject().put("fileUrls", arr))
        execute(payload)
    }

    fun downloadToFile(fileUrl: String, dest: File) {
        val request = Request.Builder().url(fileUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw OscException("Не удалось скачать $fileUrl: HTTP ${response.code}")
            }
            val body = response.body ?: throw OscException("Пустой ответ при скачивании")
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    fun takePicture(
        stitch: String,
        hdr: String,
        onProgress: ((Double?) -> Unit)? = null,
    ): List<String> {
        val optionsResp = execute(
            JSONObject()
                .put("name", "camera.getOptions")
                .put(
                    "parameters",
                    JSONObject().put(
                        "optionNames",
                        JSONArray()
                            .put("captureMode")
                            .put("hdr")
                            .put("photoStitching")
                            .put("photoStitchingSupport"),
                    ),
                ),
        )
        val opts = optionsResp.optJSONObject("results")
            ?.optJSONObject("options")
            ?: JSONObject()
        val support = opts.optJSONArray("photoStitchingSupport")
        val stitchSupport = mutableListOf<String>()
        if (support != null) {
            for (i in 0 until support.length()) stitchSupport += support.getString(i)
        }
        var finalStitch = stitch
        if (finalStitch != "none" && finalStitch !in stitchSupport) {
            finalStitch = "none"
        }

        val setOpts = JSONObject()
            .put("captureMode", "image")
            .put("hdr", hdr)
        if ("ondevice" in stitchSupport || "none" in stitchSupport || stitchSupport.isEmpty()) {
            setOpts.put("photoStitching", finalStitch)
        }
        execute(
            JSONObject()
                .put("name", "camera.setOptions")
                .put("parameters", JSONObject().put("options", setOpts)),
        )

        val take = execute(JSONObject().put("name", "camera.takePicture"))
        val results: JSONObject = if (take.has("id")) {
            val id = take.getString("id")
            val deadline = System.currentTimeMillis() + 120_000
            var last = take
            while (true) {
                val status = commandStatus(id)
                last = status
                val progress = status.optJSONObject("progress")?.optDouble("completion")
                onProgress?.invoke(progress)
                if (status.optString("state") == "done") {
                    break
                }
                if (System.currentTimeMillis() > deadline) {
                    throw OscException("Таймаут ожидания снимка")
                }
                Thread.sleep(1000)
            }
            last.optJSONObject("results") ?: JSONObject()
        } else if (take.optString("state") == "done") {
            take.optJSONObject("results") ?: JSONObject()
        } else {
            throw OscException("Нет id команды в ответе: $take")
        }

        val urls = linkedSetOf<String>()
        results.optString("fileUrl").takeIf { it.isNotBlank() }?.let { urls += it }
        val group = results.optJSONArray("_fileGroup")
        if (group != null) {
            for (i in 0 until group.length()) {
                val u = group.optString(i)
                if (u.isNotBlank()) urls += u
            }
        }
        if (urls.isEmpty()) {
            throw OscException("Снимок готов, но URL файлов не найдены: $results")
        }
        return urls.toList()
    }

    private fun getJson(path: String): JSONObject {
        val request = Request.Builder()
            .url("$base$path")
            .header("Accept", "application/json")
            .header("X-XSRF-Protected", "1")
            .get()
            .build()
        return executeRequest(request)
    }

    private fun postJson(path: String, payload: JSONObject?): JSONObject {
        val body = (payload?.toString() ?: "").toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$base$path")
            .header("Accept", "application/json")
            .header("X-XSRF-Protected", "1")
            .post(body)
            .build()
        return executeRequest(request)
    }

    private fun executeRequest(request: Request): JSONObject {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw OscException("HTTP ${response.code} ${request.url.encodedPath}: $text")
                }
                if (text.isBlank()) return JSONObject()
                return JSONObject(text)
            }
        } catch (e: OscException) {
            throw e
        } catch (e: Exception) {
            throw OscException(
                "Нет связи с камерой ($base). Подключитесь к Wi‑Fi камеры. ${e.message}",
            )
        }
    }

    private fun raiseIfError(result: JSONObject) {
        if (result.optString("state") == "error" || result.has("error")) {
            val err = result.optJSONObject("error") ?: JSONObject()
            val code = err.optString("code", "unknown")
            val message = err.optString("message", result.toString())
            when (code) {
                "unactivated" -> throw OscException(
                    "Камера не активирована. Активируйте её в официальном приложении Insta360.",
                )
                "disabledCommand" -> throw OscException(
                    "Команда недоступна в текущем режиме. $message",
                )
                else -> throw OscException("OSC error [$code]: $message")
            }
        }
    }
}
