package com.fayyaztech.dialer_core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.fayyaztech.dialer_core.ui.call.CallScreenActivity
import android.util.Log

class IncomingCallReceiver : BroadcastReceiver() {
    
    companion object {
        const val TAG = "IncomingCallReceiver"
        private var lastCallTime = 0L
        private var lastPhoneNumber = ""
        private const val DEBOUNCE_DELAY = 2000L // 2 seconds
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        val action = intent.action
        Log.d(TAG, "Received action: $action")
        
        when (action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                
                Log.d(TAG, "Phone state: $state, Number: $incomingNumber")
                
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        // Incoming call ringing - prevent duplicate launches
                        val currentTime = System.currentTimeMillis()
                        val phoneNumber = incomingNumber ?: "Unknown"
                        
                        // Check if this is a duplicate call within debounce period
                        if (phoneNumber == lastPhoneNumber && 
                            (currentTime - lastCallTime) < DEBOUNCE_DELAY) {
                            Log.d(TAG, "Ignoring duplicate call notification")
                            return
                        }
                        
                        lastCallTime = currentTime
                        lastPhoneNumber = phoneNumber
                        
                        Log.d(TAG, "Incoming call from: $phoneNumber")
                        // Incoming/ringing call - conference/merge are not available yet
                        launchCallScreen(context, phoneNumber, "Incoming call...", canConference = false, canMerge = false)
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        // Call answered or outgoing call started
                        Log.d(TAG, "Call active")
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        // Call ended or no call - reset tracking
                        Log.d(TAG, "Call idle")
                        lastPhoneNumber = ""
                        lastCallTime = 0L
                    }
                }
            }
        }
    }
    
    private fun launchCallScreen(context: Context, phoneNumber: String, callState: String,
                                 canConference: Boolean = false, canMerge: Boolean = false) {
        try {
            val intent = Intent(context, CallScreenActivity::class.java).apply {
                putExtra("PHONE_NUMBER", phoneNumber)
                putExtra("CALL_STATE", callState)
                putExtra(CallScreenActivity.EXTRA_CAN_CONFERENCE, canConference)
                putExtra(CallScreenActivity.EXTRA_CAN_MERGE, canMerge)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Call screen launched successfully for: $phoneNumber")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception launching call screen", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch call screen", e)
        }
    }
}
