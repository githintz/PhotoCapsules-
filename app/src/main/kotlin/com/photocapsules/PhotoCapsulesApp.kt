package com.photocapsules

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.photocapsules.core.data.JsonFileLibraryRepository
import com.photocapsules.core.data.LibraryRepository
import com.photocapsules.data.SettingsStore
import org.osmdroid.config.Configuration
import java.io.File

class PhotoCapsulesApp : Application() {
    /** Scope for work that must outlive a screen or dialog (network lookups, eclipse paths). */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

    lateinit var settings: SettingsStore
        private set
    lateinit var library: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        settings.applyGlobals()
        library = JsonFileLibraryRepository(File(filesDir, "library.json"))
        // osmdroid: identify the app to tile servers (OSM tile usage policy) and keep the cache in app storage.
        Configuration.getInstance().apply {
            userAgentValue = "PhotoCapsules/${BuildConfig.VERSION_NAME} (Android; com.photocapsules)"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 600L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 500L * 1024 * 1024
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TIMER, "Timer", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "Timer finished", NotificationManager.IMPORTANCE_HIGH))
    }

    companion object {
        const val CHANNEL_TIMER = "timer"
        const val CHANNEL_ALERT = "timer_alert"
        lateinit var instance: PhotoCapsulesApp
            private set
    }
}
