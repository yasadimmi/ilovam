package com.netscope

import android.app.Application
import com.netscope.util.Notifier
import com.netscope.util.Oui
import com.netscope.util.Prefs

class NetScopeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Oui.load(this)
        Notifier.init(this)
    }
}
