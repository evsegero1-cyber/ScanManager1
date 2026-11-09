package com.example.warehouseapp

import android.app.Application
import com.example.warehouseapp.reminder.Reminders

class WarehouseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        Reminders.createChannel(this)
    }
}
