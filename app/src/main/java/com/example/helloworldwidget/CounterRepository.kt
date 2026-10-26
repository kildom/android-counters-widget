package com.example.helloworldwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.Looper
import android.app.KeyguardManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object CounterRepository {
    class RequestCancelledException : IOException("Request stopped because the screen turned off or the device was locked.")

    private class RequestFailure(
        message: String,
        val retryable: Boolean,
        cause: Throwable? = null,
    ) : IOException(message, cause)

    private const val PREFS = "counter_widget"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_COUNTERS = "counters"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val KEY_ERROR = "error"
    private const val KEY_BACKGROUND_COLOR = "background_color"
    private const val KEY_TEXT_COLOR = "text_color"
    private const val KEY_OVERDUE_COLOR = "overdue_color"
    private const val KEY_POSITIVE_PROGRESS_COLOR = "positive_progress_color"
    private const val KEY_NEGATIVE_PROGRESS_COLOR = "negative_progress_color"
    private const val KEY_PROGRESS_BACKGROUND_COLOR = "progress_background_color"
    private const val KEY_PROGRESS_TEXT_COLOR = "progress_text_color"
    private const val KEY_BUTTON_BACKGROUND_COLOR = "button_background_color"
    private const val DEFAULT_BACKGROUND_COLOR = 0xFF6750A4.toInt()
    private const val DEFAULT_TEXT_COLOR = Color.WHITE
    private const val DEFAULT_OVERDUE_COLOR = 0xFFFF6B6B.toInt()
    private const val DEFAULT_POSITIVE_PROGRESS_COLOR = 0xFF2196F3.toInt()
    private const val DEFAULT_NEGATIVE_PROGRESS_COLOR = 0xFFF44336.toInt()
    private const val DEFAULT_PROGRESS_BACKGROUND_COLOR = 0xFFC0C0C0.toInt()
    private const val DEFAULT_PROGRESS_TEXT_COLOR = Color.BLACK
    private const val DEFAULT_BUTTON_BACKGROUND_COLOR = 0xFF808080.toInt()
    private const val MIN_FETCH_INTERVAL_MS = 60_000L
    private const val MAX_RESPONSE_BYTES = 1_048_576
    private const val MAX_REQUEST_ATTEMPTS = 5
    private const val RETRY_DELAY_MS = 3_000L
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private class RequestSession(private val context: Context) : AutoCloseable {
        private val stopped = AtomicBoolean(false)
        private val retrySignal = CountDownLatch(1)
        @Volatile
        private var activeConnection: HttpURLConnection? = null
        private var receiverRegistered = false

        private val screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                cancel()
            }
        }

        init {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(screenOffReceiver, filter)
            }
            receiverRegistered = true
        }

        fun ensureActive() {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
                cancel()
            }
            if (stopped.get()) throw RequestCancelledException()
        }

        fun attach(connection: HttpURLConnection) {
            activeConnection = connection
            if (stopped.get()) {
                connection.disconnect()
                throw RequestCancelledException()
            }
        }

        fun detach(connection: HttpURLConnection) {
            if (activeConnection === connection) activeConnection = null
        }

        fun waitBeforeRetry() {
            ensureActive()
            retrySignal.await(RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
            ensureActive()
        }

        private fun cancel() {
            if (stopped.compareAndSet(false, true)) {
                activeConnection?.disconnect()
                retrySignal.countDown()
            }
        }

        override fun close() {
            if (receiverRegistered) {
                context.unregisterReceiver(screenOffReceiver)
                receiverRegistered = false
            }
            activeConnection?.disconnect()
            activeConnection = null
        }
    }

    fun endpoint(context: Context): String =
        preferences(context).getString(KEY_ENDPOINT, "").orEmpty()

    fun backgroundColor(context: Context): Int =
        preferences(context).getInt(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)

    fun textColor(context: Context): Int =
        preferences(context).getInt(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR)

    fun overdueColor(context: Context): Int =
        preferences(context).getInt(KEY_OVERDUE_COLOR, DEFAULT_OVERDUE_COLOR)

    fun positiveProgressColor(context: Context): Int =
        preferences(context).getInt(KEY_POSITIVE_PROGRESS_COLOR, DEFAULT_POSITIVE_PROGRESS_COLOR)

    fun negativeProgressColor(context: Context): Int =
        preferences(context).getInt(KEY_NEGATIVE_PROGRESS_COLOR, DEFAULT_NEGATIVE_PROGRESS_COLOR)

    fun progressBackgroundColor(context: Context): Int =
        preferences(context).getInt(KEY_PROGRESS_BACKGROUND_COLOR, DEFAULT_PROGRESS_BACKGROUND_COLOR)

    fun progressTextColor(context: Context): Int =
        preferences(context).getInt(KEY_PROGRESS_TEXT_COLOR, DEFAULT_PROGRESS_TEXT_COLOR)

    fun buttonBackgroundColor(context: Context): Int =
        preferences(context).getInt(KEY_BUTTON_BACKGROUND_COLOR, DEFAULT_BUTTON_BACKGROUND_COLOR)

    fun saveWidgetColors(
        context: Context,
        background: Int,
        text: Int,
        overdue: Int,
        positiveProgress: Int,
        negativeProgress: Int,
        progressBackground: Int,
        progressText: Int,
        buttonBackground: Int,
    ) {
        preferences(context).edit()
            .putInt(KEY_BACKGROUND_COLOR, background)
            .putInt(KEY_TEXT_COLOR, text)
            .putInt(KEY_OVERDUE_COLOR, overdue)
            .putInt(KEY_POSITIVE_PROGRESS_COLOR, positiveProgress)
            .putInt(KEY_NEGATIVE_PROGRESS_COLOR, negativeProgress)
            .putInt(KEY_PROGRESS_BACKGROUND_COLOR, progressBackground)
            .putInt(KEY_PROGRESS_TEXT_COLOR, progressText)
            .putInt(KEY_BUTTON_BACKGROUND_COLOR, buttonBackground)
            .apply()
    }

    fun parseColor(value: String): Int {
        val normalized = value.trim().let { if (it.startsWith("#")) it else "#$it" }
        require(Regex("^#[0-9a-fA-F]{6}$").matches(normalized)) {
            "Enter a color as #RRGGBB."
        }
        return try {
            Color.parseColor(normalized)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Enter a color as #RRGGBB or #AARRGGBB.", exception)
        }
    }

    fun saveEndpoint(context: Context, value: String) {
        val endpoint = value.trim()
        if (endpoint.isNotEmpty()) {
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
        refresh(context, force = false, onComplete = onComplete)
    }

    fun refreshNow(context: Context, onComplete: (() -> Unit)? = null) {
        refresh(context, force = true, onComplete = onComplete)
    }

    private fun refresh(context: Context, force: Boolean, onComplete: (() -> Unit)?) {
        val appContext = context.applicationContext
        executor.execute {
            val prefs = preferences(appContext)
            try {
                if (!deviceCanRunRequests(appContext)) return@execute
                val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
                if (force || System.currentTimeMillis() - lastAttempt >= MIN_FETCH_INTERVAL_MS) {
                    prefs.edit().putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()).apply()
                    try {
                        val response = requestWithRetries(appContext, JSONObject().put("command", "get"))
                        saveResponse(appContext, response, "get")
                    } catch (cancelled: RequestCancelledException) {
                        return@execute
                    } catch (exception: Exception) {
                        saveError(appContext, exception.message ?: "Could not refresh counters.")
                    }
                }
                onComplete?.let { mainHandler.post(it) }
            } catch (exception: Exception) {
                saveError(appContext, exception.message ?: "Could not refresh counters.")
                onComplete?.let { mainHandler.post(it) }
            }
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
                val response = requestWithRetries(appContext, command)
                saveResponse(appContext, response, name)
                response
            }.onFailure { exception ->
                if (exception !is RequestCancelledException) {
                    saveError(appContext, exception.message ?: "The request failed.")
                }
            }
            mainHandler.post { onComplete(result) }
        }
    }

    private fun requestWithRetries(context: Context, payload: JSONObject): JSONObject {
        RequestSession(context).use { session ->
            var lastFailure: RequestFailure? = null
            repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
                session.ensureActive()
                try {
                    return request(context, payload, session)
                } catch (cancelled: RequestCancelledException) {
                    throw cancelled
                } catch (failure: RequestFailure) {
                    if (!failure.retryable) throw failure
                    lastFailure = failure
                    if (attempt < MAX_REQUEST_ATTEMPTS - 1) {
                        session.waitBeforeRetry()
                    }
                }
            }

            val finalFailure = lastFailure
                ?: RequestFailure("The request failed without an error response.", false)
            throw RequestFailure(
                "Request failed after $MAX_REQUEST_ATTEMPTS attempts: ${finalFailure.message}",
                false,
                finalFailure,
            )
        }
    }

    private fun request(context: Context, payload: JSONObject, session: RequestSession): JSONObject {
        session.ensureActive()
        val endpoint = endpoint(context)
        if (endpoint.isBlank()) {
            throw RequestFailure("Set the backend URL in the app before using the widget.", false)
        }

        val isGet = payload.optString("command") == "get"
        val connection = try {
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = if (isGet) "GET" else "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                if (!isGet) doOutput = true
            }
        } catch (exception: Exception) {
            throw RequestFailure("Could not connect to the server.", true, exception)
        }

        try {
            session.attach(connection)
            if (!isGet) {
                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val bytes = if (stream == null) {
                ByteArray(0)
            } else {
                try {
                    stream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_RESPONSE_BYTES) {
                                throw RequestFailure("The server response is too large.", false)
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                } catch (failure: RequestFailure) {
                    throw failure
                } catch (exception: IOException) {
                    throw RequestFailure("Could not read the server response.", true, exception)
                }
            }
            val response = if (bytes.isEmpty()) null else parseResponse(bytes, statusCode in 200..299)
            if (statusCode !in 200..299) {
                if (response != null) {
                    validateCommand(response, payload)
                    if (response.optString("status") == "error") {
                        throw RequestFailure(
                            response.optString("message").ifBlank { "The server returned an error." },
                            false,
                        )
                    }
                }
                throw RequestFailure("The server returned HTTP $statusCode.", true)
            }
            if (response == null) {
                throw RequestFailure("The server returned an empty response.", false)
            }
            validateCommand(response, payload)
            if (response.optString("status") != "ok") {
                throw RequestFailure(
                    response.optString("message").ifBlank { "The server returned an error." },
                    false,
                )
            }
            session.ensureActive()
            return response
        } catch (cancelled: RequestCancelledException) {
            throw cancelled
        } catch (failure: RequestFailure) {
            throw failure
        } catch (exception: IOException) {
            throw RequestFailure("Network request failed.", true, exception)
        } catch (exception: Exception) {
            throw RequestFailure(exception.message ?: "The request failed.", false, exception)
        } finally {
            session.detach(connection)
            connection.disconnect()
        }
    }

    private fun deviceCanRunRequests(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return powerManager.isInteractive && !keyguardManager.isKeyguardLocked
    }

    private fun parseResponse(bytes: ByteArray, successStatus: Boolean): JSONObject {
        try {
            return JSONObject(String(bytes, StandardCharsets.UTF_8))
        } catch (exception: Exception) {
            if (!successStatus) {
                throw RequestFailure("The server returned an HTTP error.", true, exception)
            }
            throw RequestFailure("The server returned invalid JSON.", false, exception)
        }
    }

    private fun validateCommand(response: JSONObject, payload: JSONObject) {
        if (response.optString("command") != payload.optString("command")) {
            throw RequestFailure("The server response command did not match the request.", false)
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
