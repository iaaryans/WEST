package it.vfsfitvnm.vimusic.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object WestAnalytics {
    private const val SDK_VERSION = "aptabase-kotlin@0.0.8"
    private val SESSION_TIMEOUT: Long = TimeUnit.HOURS.toMillis(1)

    private var appKey: String? = null
    private var apiUrl: String? = null
    private var sessionId = UUID.randomUUID()
    private var lastTouched = Date()

    private var isDebug = false
    private var appVersion = "0.5.4"
    private var appBuildNumber = "20"
    private var deviceModel = Build.MODEL ?: ""
    private var locale = Locale.getDefault().language ?: "en"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "west-analytics").apply { isDaemon = true }
    }

    private val hosts = mapOf(
        "US" to "https://us.aptabase.com",
        "EU" to "https://eu.aptabase.com",
        "DEV" to "http://localhost:3000"
    )

    private val dateFormatter: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun initialize(context: Context, key: String) {
        try {
            val parts = key.split("-")
            if (parts.size != 3 || !hosts.containsKey(parts[1])) {
                return
            }

            val region = parts[1]
            val baseHost = hosts[region] ?: return
            this.apiUrl = "$baseHost/api/v0/event"
            this.appKey = key

            val pm = context.packageManager
            val pkg = pm.getPackageInfo(context.packageName, 0)
            this.appVersion = pkg.versionName ?: "0.5.4"
            this.appBuildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toString()
            }
            this.isDebug = 0 != (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
            this.locale = Locale.getDefault().language
            this.deviceModel = Build.MODEL ?: ""
        } catch (_: Throwable) {
            // Safe fallback: never disrupt the app
        }
    }

    fun trackEvent(eventName: String, props: Map<String, Any> = emptyMap()) {
        val currentKey = appKey ?: return
        val targetUrl = apiUrl ?: return

        executor.execute {
            try {
                val now = Date()
                if (now.time - lastTouched.time > SESSION_TIMEOUT) {
                    sessionId = UUID.randomUUID()
                }
                lastTouched = now

                val payload = JSONObject().apply {
                    put("timestamp", dateFormatter.format(now))
                    put("sessionId", sessionId.toString().lowercase())
                    put("eventName", eventName)
                    put("systemProps", JSONObject().apply {
                        put("isDebug", isDebug)
                        put("osName", "Android")
                        put("osVersion", Build.VERSION.RELEASE ?: "")
                        put("locale", locale)
                        put("appVersion", appVersion)
                        put("appBuildNumber", appBuildNumber)
                        put("sdkVersion", SDK_VERSION)
                        put("deviceModel", deviceModel)
                    })
                    if (props.isNotEmpty()) {
                        put("props", JSONObject(props))
                    }
                }

                val url = URL(targetUrl)
                val conn = (url.openConnection() as? HttpURLConnection) ?: return@execute
                try {
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("App-Key", currentKey)
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.doOutput = true

                    BufferedWriter(OutputStreamWriter(conn.outputStream, "UTF-8")).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    // Read response code to complete connection
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            } catch (_: Throwable) {
                // Silently ignore network failures to protect user experience
            }
        }
    }
}
