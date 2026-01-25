package com.mepapp.mobile.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.annotations.SerializedName
import com.mepapp.mobile.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import java.io.File

interface GitHubApiService {
    @GET("repos/fenizo/mepapp/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("assets") val assets: List<GitHubAsset>,
    @SerializedName("body") val body: String
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)

object AppUpdater {
    private const val TAG = "AppUpdater"
    private const val CURRENT_VERSION = "1.0.0" // Update this when releasing new versions
    
    suspend fun checkForUpdate(context: Context): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val retrofit = NetworkModule.createRetrofit("https://api.github.com/")
                val service = retrofit.create(GitHubApiService::class.java)
                val release = service.getLatestRelease()
                
                // Extract version from tag (e.g., "mobile-v1.0.1" -> "1.0.1")
                val latestVersion = release.tagName.removePrefix("mobile-v")
                
                if (isNewerVersion(latestVersion, CURRENT_VERSION)) {
                    // Find APK asset
                    val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    if (apkAsset != null) {
                        return@withContext UpdateInfo(
                            version = latestVersion,
                            downloadUrl = apkAsset.downloadUrl,
                            releaseNotes = release.body,
                            releaseName = release.name
                        )
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                null
            }
        }
    }
    
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrNull(i) ?: 0
            val currentPart = currentParts.getOrNull(i) ?: 0
            
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }
    
    fun downloadAndInstall(context: Context, updateInfo: UpdateInfo): Long {
        val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
            .setTitle("MEP App Update")
            .setDescription("Downloading version ${updateInfo.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MEPApp-${updateInfo.version}.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        
        // Register receiver for download completion
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(context, downloadManager, downloadId)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
        
        return downloadId
    }
    
    private fun installApk(context: Context, downloadManager: DownloadManager, downloadId: Long) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // For Android 7.0+, use FileProvider
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            } else {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
        }
    }
}

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val releaseName: String
)
