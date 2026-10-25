package com.example.helloworldwidget

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object CounterRepository {
    private const val PREFS = "counter_widget"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_COUNTERS = "counters"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val KEY_ERROR = "error"
    private const val MIN_FETCH_INTERVAL_MS = 60_000L
    private const val MAX_RESPONSE_BYTES = 1_048_576
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun endpoint(context: Context): String =
        preferences(context).getString(KEY_ENDPOINT, "").orEmpty()

    fun saveEndpoint(context: Context, value: String) {
        val endpoint = value.trim()
        val parsed = try {
            URL(endpoint)
        } catch (exception: Exception) {
            throw IllegalArgumentException("Enter a complete HTTP or HTTPS URL.", exception)
        }
        require(
            (parsed.protocol.equals("https", ignoreCase = true) ||
                parsed.protocol.equals("http", ignoreCase = true)) &&
                !parsed.host.isNullOrBlank()
        ) {
            "Enter a complete HTTP or HTTPS URL."
        }
        val prefs = preferences(context)
        if (prefs.getString(KEY_ENDPOINT, "") != endpoint) {
            prefs.edit()
                .putString(KEY_ENDPOINT, endpoint)
                .putString(KEY_COUNTERS, "[]")
                .putLong(KEY_LAST_ATTEMPT, 0L)
                .remove(KEY_ERROR)
                .apply()
        }
    }

    fun counters(context: Context): List<Counter> {
        val saved = preferences(context).getString(KEY_COUNTERS, "[]") ?: "[]"
        val array = JSONArray(saved)
        return List(array.length()) { index -> Counter.fromJson(array.getJSONObject(index)) }
    }

    fun lastError(context: Context): String? =
        preferences(context).getString(KEY_ERROR, null)

    fun reportError(context: Context, message: String) {
        saveError(context, message)
    }

    fun refreshIfStale(context: Context, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        executor.execute {
            val prefs = preferences(appContext)
            val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
            if (System.currentTimeMillis() - lastAttempt >= MIN_FETCH_INTERVAL_MS) {
                prefs.edit().putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()).apply()
                try {
                    val response = request(appContext, JSONObject().put("command", "get"))
                    saveResponse(appContext, response, "get")
                } catch (exception: Exception) {
                    saveError(appContext, exception.message ?: "Could not refresh counters.")
                }
            }
            onComplete?.let { mainHandler.post(it) }
        }
    }

    fun sendCommand(
        context: Context,
        command: JSONObject,
        onComplete: (Result<JSONObject>) -> Unit,
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching {
                val name = command.optString("command")
                val response = request(appContext, command)
                saveResponse(appContext, response, name)
                response
            }.onFailure { exception ->
                saveError(appContext, exception.message ?: "The request failed.")
            }
            mainHandler.post { onComplete(result) }
        }
    }

    private fun request(context: Context, payload: JSONObject): JSONObject {
        val endpoint = endpoint(context)
        if (endpoint.isBlank()) {
            throw IOException("Set the backend URL in the app before using the widget.")
        }

        val isGet = payload.optString("command") == "get"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = if (isGet) "GET" else "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (!isGet) doOutput = true
        }

        try {
            if (!isGet) {
                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                ?: throw IOException("The server returned HTTP $statusCode without a response.")
            val bytes = stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) {
                        throw IOException("The server response is too large.")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val response = try {
                JSONObject(String(bytes, StandardCharsets.UTF_8))
            } catch (exception: Exception) {
                throw IOException("The server returned invalid JSON.", exception)
            }
            if (response.optString("command") != payload.optString("command")) {
                throw IOException("The server response command did not match the request.")
            }
            if (response.optString("status") != "ok") {
                throw IOException(response.optString("message").ifBlank { "The server returned an error." })
            }
            if (statusCode !in 200..299) {
                throw IOException("The server returned HTTP $statusCode.")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun saveResponse(context: Context, response: JSONObject, command: String) {
        val rawCounters = response.optJSONArray("counters")
            ?: throw IOException("The server response did not include counters.")
        val parsed = JSONArray()
        for (index in 0 until rawCounters.length()) {
            val counter = Counter.fromJson(rawCounters.getJSONObject(index))
            parsed.put(counter.toJson())
        }
        if (command == "add") {
            val id = response.optString("id")
            if (id.isBlank() || (0 until parsed.length()).none { parsed.getJSONObject(it).optString("id") == id }) {
                throw IOException("The server did not return the new counter ID.")
            }
        }
        preferences(context).edit()
            .putString(KEY_COUNTERS, parsed.toString())
            .remove(KEY_ERROR)
            .putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis())
            .apply()
    }

    private fun saveError(context: Context, message: String) {
        preferences(context).edit().putString(KEY_ERROR, message).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
