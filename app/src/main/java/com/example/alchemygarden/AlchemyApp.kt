package com.example.alchemygarden

import android.app.Application
import com.example.alchemygarden.notifications.ExpeditionNotifier

class AlchemyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ExpeditionNotifier.createChannel(this)
    }
}
