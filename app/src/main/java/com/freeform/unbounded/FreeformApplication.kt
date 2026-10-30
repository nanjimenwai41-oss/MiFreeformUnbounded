package com.freeform.unbounded

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.UserManager
import androidx.core.content.ContextCompat
import org.lsposed.hiddenapibypass.HiddenApiBypass

class FreeformApplication : Application() {
    companion object {
        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod(
                    "setEnableOnBackInvokedCallback",
                    Boolean::class.javaPrimitiveType,
                )
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The in-memory mask rendezvous is available before first unlock. Do
        // not open credential-protected appearance preferences in that phase.
        // Initialize appearance on unlock even if the provider created this
        // Application hours before MainActivity is opened.
        if (getSystemService(UserManager::class.java)?.isUserUnlocked != true) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Intent.ACTION_USER_UNLOCKED) return
                    runCatching { unregisterReceiver(this) }
                    initializeAppearance()
                }
            }
            ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED),
                ContextCompat.RECEIVER_NOT_EXPORTED)
            // Close the check/register race if unlock happened in between.
            if (getSystemService(UserManager::class.java)?.isUserUnlocked == true) {
                runCatching { unregisterReceiver(receiver) }
                initializeAppearance()
            }
            return
        }
        initializeAppearance()
    }

    private fun initializeAppearance() {
        AppSettingsRepository.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val enable = AppSettingsRepository.settings.value.predictiveBack
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
            )
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }
    }
}
