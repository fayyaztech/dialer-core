package com.fayyaztech.dialer_core.services

import android.telecom.Call.Details
import android.telecom.CallScreeningService as TelecomCallScreeningService
import android.util.Log

/**
 * Minimal Telecom CallScreeningService stub.
 * This implementation currently allows all calls — update to add blocking or
 * spam detection as needed. Keeping a dedicated CallScreeningService (separate
 * from the in-call UI service) avoids name collisions with the platform class
 * and ensures correct binding behavior on Android 13+.
 */
class CallScreeningService : TelecomCallScreeningService() {
    companion object { private const val TAG = "CallScreeningService" }

    override fun onScreenCall(details: Details) {
        // Minimal stub: we're not performing any screening decision here.
        // Leaving this method without a call to respondToCall will let the
        // platform continue normal processing and avoids compile-time
        // dependency on CallResponse on devices/SDKs where it's unavailable.
        try {
            Log.d(TAG, "onScreenCall (no action): ${'$'}{details.handle}")
        } catch (e: Exception) {
            Log.w(TAG, "onScreenCall failed", e)
        }
    }
}