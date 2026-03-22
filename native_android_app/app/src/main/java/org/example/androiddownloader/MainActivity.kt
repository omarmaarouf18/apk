package org.example.androiddownloader

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity() {
    // لتخزين بيانات التحميل مؤقتاً في حال انتظار الموافقة على الصلاحيات
    private var pendingDownload: ExtractedVideo? = null

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
                    modifier = Modifier.fillMaxSize().padding(16.dp),
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
                    ) { Text("Login") }

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
                    ) { Text("Quality: $quality") }

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
                                Toast.makeText(this@MainActivity, "Enter URL first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            // بدء عملية جلب الرابط والتحميل
                            fetchAndDownload(url.trim(), quality) { message ->
                                runOnUiThread { status = message }
                            }
                        },
                        enabled = isLoggedIn,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Download") }

                    Text(text = status)
                }
            }
        }
    }

    private fun fetchAndDownload(inputUrl: String, quality: String, onStatus: (String) -> Unit) {
        onStatus("Resolving download URL...")
        Thread {
            try {
                val extracted = resolveDownloadUrl(inputUrl, quality)
                runOnUiThread {
                    if (!hasRequiredPermissions()) {
                        pendingDownload = extracted
                        requestRequiredPermissions()
                        onStatus("Grant permissions to start download")
                        return@runOnUiThread
                    }
                    enqueueDownload(this, extracted.url, extracted.title)
                    onStatus("Download started: ${extracted.title}")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    onStatus("Error: ${e.message}")
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun resolveDownloadUrl(inputUrl: String, quality: String): ExtractedVideo {
        // إذا كان الرابط مباشراً، لا نرسله للسيرفر
        if (looksLikeDirectMediaUrl(inputUrl)) {
            val title = URLUtil.guessFileName(inputUrl, null, null).ifBlank { "download_${System.currentTimeMillis()}" }
            return ExtractedVideo(inputUrl, title)
        }

        val qualityParam = mapQualityToBackendFormat(quality)
        val encodedUrl = URLEncoder.encode(inputUrl, "UTF-8")
        val endpoint = "$EXTRACTOR_API_BASE/extract?url=$encodedUrl&quality=$qualityParam"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 45000
        }

        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optString("status") != "success") throw Exception("Server failed to extract link")
            
            return ExtractedVideo(
                url = json.getString("url"),
                title = json.optString("title", "download_${System.currentTimeMillis()}")
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) true 
        else ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
        }
    }

    private fun enqueueDownload(context: Context, mediaUrl: String, title: String) {
        val request = DownloadManager.Request(Uri.parse(mediaUrl))
            .setTitle(title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, title)

        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(context, "Downloading to folder: Downloads", Toast.LENGTH_SHORT).show()
    }

    private fun mapQualityToBackendFormat(q: String) = when (q) {
        QUALITY_BEST -> "best"; QUALITY_720 -> "720"; QUALITY_WORST -> "worst"; QUALITY_AUDIO -> "audio"; else -> "best"
    }

    private fun looksLikeDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".webm")
    }

    companion object {
        private const val QUALITY_BEST = "best"
        private const val QUALITY_720 = "720p"
        private const val QUALITY_WORST = "worst"
        private const val QUALITY_AUDIO = "audio"
        private val QUALITY_OPTIONS = listOf(QUALITY_BEST, QUALITY_720, QUALITY_WORST, QUALITY_AUDIO)
        
        // رابط سيرفر Railway الخاص بك
        private const val EXTRACTOR_API_BASE = "https://backend-production-5272.up.railway.app"
    }

    private data class ExtractedVideo(val url: String, val title: String)
}