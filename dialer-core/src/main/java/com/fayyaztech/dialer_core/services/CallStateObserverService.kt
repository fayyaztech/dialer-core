package com.fayyaztech.dialer_core.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Lightweight service that observes telephony IDLE transitions for analytics only.
 *
 * NOTE: This class intentionally avoids performing any detailed call-state calculations (durations,
 * disconnect reasons) — those are handled by DefaultInCallService (Telecom). Keeping this service
 * minimal avoids duplicate logs, race conditions, and incorrect metrics on VoLTE/IMS/OEM devices.
 */
class CallStateObserverService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    companion object {
        const val TAG = "CallStateObserverService"
    }

    override fun onCreate() {
        super.onCreate()

        // Keep service foreground so it isn't killed during transitions. This
        // is for analytics/telemetry only — the service must remain lightweight
        // (no duration calculations, no disconnect guessing).
        startAsForegroundService()

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerTelephonyCallbackForAndroid12Plus()
        } else {
            registerPhoneStateListenerForOlderVersions()
        }

        Log.d(TAG, "CallStateObserverService started")
    }

    private fun startAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "call_monitor_channel"

            val channel =
                    NotificationChannel(
                            channelId,
                            "Call Monitoring",
                            NotificationManager.IMPORTANCE_LOW
                    )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val notification =
                    Notification.Builder(this, channelId)
                            .setContentTitle("Monitoring calls")
                            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                            .build()

            try {
                startForeground(1001, notification)
            } catch (e: SecurityException) {
                // Don't crash if the platform refuses startForeground with the configured
                // type. Android 14+ enforces additional phone-call foreground permissions
                // for services using the phoneCall FGS type — which our app may not have
                // unless it's the default dialer. Log the exception detail for diagnostics
                // and continue without making the service crash.
                Log.e(TAG, "startForeground denied — ${e.message}", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallbackForAndroid12Plus() {
        telephonyCallback =
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        // Only observe IDLE state and let DefaultInCallService handle
                        // detailed metrics.
                        if (state == TelephonyManager.CALL_STATE_IDLE) {
                            Log.d(
                                    TAG,
                                    "CALL_STATE_IDLE observed — a call ended (observed via TelephonyCallback)"
                            )
                        }
                    }
                }

        try {
            telephonyManager.registerTelephonyCallback(
                    mainExecutor,
                    telephonyCallback as TelephonyCallback
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for telephony callback", e)
        }
    }

    private fun registerPhoneStateListenerForOlderVersions() {
        phoneStateListener =
                object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        if (state == TelephonyManager.CALL_STATE_IDLE) {
                            Log.d(
                                    TAG,
                                    "CALL_STATE_IDLE observed — a call ended (observed via PhoneStateListener)"
                            )
                        }
                    }
                }

        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for phone listener", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
        }

        Log.d(TAG, "CallStateObserverService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
