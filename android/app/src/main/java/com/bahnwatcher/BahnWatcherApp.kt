package com.bahnwatcher

import android.app.Application
import com.bahnwatcher.worker.MonitoringWorker

class BahnWatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MonitoringWorker.createNotificationChannel(this)
    }
}
