package com.lightdownloader.app

import android.app.Application
import com.lightdownloader.app.data.MediaRepository
import com.lightdownloader.app.data.local.AppDatabase
import java.io.File

class LightDownloaderApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val repository: MediaRepository by lazy {
        MediaRepository(this, database)
    }

    override fun onCreate() {
        super.onCreate()
        // 1.1 no longer keeps a plain-text activity log.
        runCatching { File(filesDir, "download_history_log.txt").delete() }
    }
}
