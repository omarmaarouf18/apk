package org.example.androiddownloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var username by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var url by remember { mutableStateOf("") }
                var status by remember { mutableStateOf("Not logged in") }
                var isLoggedIn by remember { mutableStateOf(false) }
                var quality by remember { mutableStateOf(QUALITY_BEST) }
                var qualityMenuExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Android Downloader (Native)")

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (username == "admin" && password == "admin") {
                                isLoggedIn = true
                                status = "Authenticated"
                            } else {
                                status = "Invalid credentials"
                            }
                        }
                    ) {
                        Text("Login")
                    }

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Video URL") },
                        enabled = isLoggedIn,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { qualityMenuExpanded = true },
                        enabled = isLoggedIn,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quality: $quality")
                    }

                    DropdownMenu(
                        expanded = qualityMenuExpanded,
                        onDismissRequest = { qualityMenuExpanded = false }
                    ) {
                        QUALITY_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    quality = option
                                    qualityMenuExpanded = false
                                }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (url.isBlank()) {
                                status = "Enter URL first"
                                return@Button
                            }
                            status = "Resolving download URL..."
                            Thread {
                                try {
                                    val resolved = resolveDownloadUrl(url.trim(), quality)
                                    enqueueDownload(this@MainActivity, resolved)
                                    runOnUiThread {
                                        status = "Download started. Check notification panel."
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        status = "Error: ${e.message}"
                                    }
                                }
                            }.start()
                        },
                        enabled = isLoggedIn,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download")
                    }

                    Text(text = status)
                }
            }
        }
    }

    private fun resolveDownloadUrl(inputUrl: String, quality: String): String {
        if (looksLikeDirectMediaUrl(inputUrl)) {
            return inputUrl
        }

        if (EXTRACTOR_API_BASE.isBlank()) {
            throw IllegalArgumentException(
                "This native app can download direct media URLs. " +
                    "For YouTube/TikTok extraction, set EXTRACTOR_API_BASE in MainActivity.kt " +
                    "to your Flask backend URL (e.g., https://video-extractor.onrender.com)"
            )
        }

        val qualityParam = mapQualityToBackendFormat(quality)
        val encodedUrl = URLEncoder.encode(inputUrl, Charsets.UTF_8.name())
        val encodedQuality = URLEncoder.encode(qualityParam, Charsets.UTF_8.name())
        val endpoint = "$EXTRACTOR_API_BASE/extract?url=$encodedUrl&quality=$encodedQuality"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 45000
        }

        try {
            val statusCode = conn.responseCode
            val inputStream = if (statusCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream
            } else {
                conn.errorStream
            }

            val body = inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val status = json.optString("status", "")
            if (status != "success") {
                val message = json.optString("message", "Unknown error from backend")
                throw Exception(message)
            }

            val directUrl = json.optString("url", "")
            val title = json.optString("title", "Unknown")

            if (directUrl.isBlank()) {
                throw Exception("Backend returned empty download URL")
            }

            return directUrl
        } finally {
            conn.disconnect()
        }
    }

    private fun mapQualityToBackendFormat(quality: String): String {
        return when (quality) {
            QUALITY_BEST -> "best"
            QUALITY_720 -> "720"
            QUALITY_WORST -> "worst"
            QUALITY_AUDIO -> "audio"
            else -> "best"
        }
    }

    private fun looksLikeDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.endsWith(".mp4") ||
            lower.endsWith(".mp3") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".webm") ||
            lower.endsWith(".m3u8")
    }

    private fun enqueueDownload(context: Context, mediaUrl: String) {
        val guessedName = URLUtil.guessFileName(mediaUrl, null, null)
        val fileName = if (guessedName.isNullOrBlank()) {
            "download_${System.currentTimeMillis()}.bin"
        } else {
            guessedName
        }

        val request = DownloadManager.Request(Uri.parse(mediaUrl))
            .setTitle(fileName)
            .setDescription("Android Downloader")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val manager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }

    companion object {
        private const val QUALITY_BEST = "best"
        private const val QUALITY_720 = "720p"
        private const val QUALITY_WORST = "worst"
        private const val QUALITY_AUDIO = "audio"

        private val QUALITY_OPTIONS = listOf(
            QUALITY_BEST,
            QUALITY_720,
            QUALITY_WORST,
            QUALITY_AUDIO
        )

        // Flask backend API base URL for video extraction
        // Examples:
        //   Local: "http://192.168.1.100:5000"
        //   Render: "https://your-app.onrender.com"
        //   Railway: "https://your-app-name.railway.app"
        // Keep empty to allow only direct media links (.mp4, .mp3, etc.)
        private const val EXTRACTOR_API_BASE = "https://your-backend-url-here.onrender.com"
    }
}
