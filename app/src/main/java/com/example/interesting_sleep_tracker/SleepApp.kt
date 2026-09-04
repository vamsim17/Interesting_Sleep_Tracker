package com.example.interesting_sleep_tracker

import android.app.Application
import com.example.interesting_sleep_tracker.data.SleepRepository
import com.example.interesting_sleep_tracker.service.SleepNotifications

class SleepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SleepRepository.init(this)
        SleepNotifications.createChannels(this)
    }
}
