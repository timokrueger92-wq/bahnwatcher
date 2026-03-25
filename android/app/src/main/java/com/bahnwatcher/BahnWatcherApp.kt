package com.bahnwatcher

import android.app.Application
import com.bahnwatcher.worker.MonitoringWorker

class BahnWatcherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MonitoringWorker.createNotificationChannel(this)
    }
}
