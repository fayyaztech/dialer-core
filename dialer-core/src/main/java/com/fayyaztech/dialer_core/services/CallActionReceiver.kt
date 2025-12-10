package com.fayyaztech.dialer_core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACCEPT_CALL" -> {
                Log.d("CallActionReceiver", "Accept call action received")
                DefaultInCallService.currentCall?.answer(0)
            }
            "REJECT_CALL" -> {
                Log.d("CallActionReceiver", "Reject call action received")
                DefaultInCallService.currentCall?.reject(false, null)
            }
            "HANGUP_CALL" -> {
                Log.d("CallActionReceiver", "Hangup call action received")
                if (DefaultInCallService.currentCall != null) {
                    DefaultInCallService.currentCall?.disconnect()
                } else {
                    // If call is already null/gone, ensure notification is cleared
                    Log.d("CallActionReceiver", "Current call is null, forcing notification cancellation")
                    DefaultInCallService.cancelNotification()
                }
            }
        }
    }
}