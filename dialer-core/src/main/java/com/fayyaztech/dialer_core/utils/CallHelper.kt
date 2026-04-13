package com.fayyaztech.dialer_core.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.ActivityCompat

/**
 * Helper class for making phone calls with SIM selection support.
 * 
 * Supports:
 * - Single SIM devices (standard call)
 * - Dual SIM devices with automatic/manual SIM selection
 * - Per-contact SIM preferences
 * - OEM compatibility (Samsung, Xiaomi, OnePlus, Oppo, Vivo)
 * 
 * Android API Compatibility:
 * - Android 6.0+ (API 23) for TelecomManager.placeCall
 * - Android 5.1+ (API 22) for multi-SIM support
 * - Fallback to ACTION_CALL for older devices
 */
class CallHelper(private val context: Context) {

    companion object {
        private const val TAG = "CallHelper"
        
        // Extra keys for SIM selection
        const val EXTRA_PHONE_ACCOUNT_HANDLE = "android.telecom.extra.PHONE_ACCOUNT_HANDLE"
        const val EXTRA_START_CALL_WITH_SPEAKERPHONE = "android.telecom.extra.START_CALL_WITH_SPEAKERPHONE"
        const val EXTRA_START_CALL_WITH_VIDEO_STATE = "android.telecom.extra.START_CALL_WITH_VIDEO_STATE"
    }

    private val simManager = SimManager(context)
    private val telecomManager: TelecomManager? = 
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    /**
     * Make a phone call with optional SIM selection
     * 
     * @param phoneNumber The phone number to call
     * @param subscriptionId Optional SIM subscription ID. If -1, uses default or shows picker
     * @param useSpeaker Start call with speakerphone on
     * @param videoCall Start as video call (if supported)
     * @return true if call was initiated successfully
     */
    fun makeCall(
        phoneNumber: String,
        subscriptionId: Int = -1,
        useSpeaker: Boolean = false,
        videoCall: Boolean = false
    ): Boolean {
        try {
            // Check CALL_PHONE permission
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "CALL_PHONE permission not granted")
                return false
            }

            val uri = Uri.fromParts("tel", phoneNumber, null)
            
            // Android 6.0+ (API 23) - Use TelecomManager for better SIM control
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return placeCallWithTelecomManager(uri, subscriptionId, useSpeaker, videoCall)
            } else {
                // Fallback for older devices
                return placeCallWithIntent(uri)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception making call: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call: ${e.message}", e)
            return false
        }
    }

    /**
     * Make a call using contact number with remembered SIM preference
     * 
     * @param phoneNumber The contact's phone number
     * @param useSpeaker Start with speaker on
     * @return true if call initiated
     */
    fun makeCallWithRememberedSim(phoneNumber: String, useSpeaker: Boolean = false): Boolean {
        // Check if there's a saved SIM preference for this contact
        val preferredSim = simManager.getContactSimPreference(phoneNumber)
        
        return if (preferredSim != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // Use remembered SIM if available and valid
            if (simManager.isSimActive(preferredSim)) {
                Log.d(TAG, "Using remembered SIM $preferredSim for $phoneNumber")
                makeCall(phoneNumber, preferredSim, useSpeaker)
            } else {
                Log.w(TAG, "Remembered SIM $preferredSim is not active, using default")
                makeCall(phoneNumber, -1, useSpeaker)
            }
        } else {
            // No preference or not available, use default behavior
            makeCall(phoneNumber, -1, useSpeaker)
        }
    }

    /**
     * Place call using TelecomManager (Android 6.0+)
     * Provides better control over SIM selection
     */
    private fun placeCallWithTelecomManager(
        uri: Uri,
        subscriptionId: Int,
        useSpeaker: Boolean,
        videoCall: Boolean
    ): Boolean {
        try {
            if (telecomManager == null) {
                Log.w(TAG, "TelecomManager not available, falling back to intent")
                return placeCallWithIntent(uri)
            }

            val extras = Bundle().apply {
                if (useSpeaker) {
                    putBoolean(EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
                }
                if (videoCall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    putInt(EXTRA_START_CALL_WITH_VIDEO_STATE, 3) // Video transmit and receive
                }
            }

            // Handle SIM selection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionId != -1) {
                val phoneAccount = simManager.getPhoneAccountForSubscription(subscriptionId)
                if (phoneAccount != null) {
                    extras.putParcelable(EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccount)
                    Log.d(TAG, "Placing call with SIM $subscriptionId")
                } else {
                    Log.w(TAG, "Could not find phone account for SIM $subscriptionId, using default")
                }
            } else if (subscriptionId == -1) {
                // Let system handle SIM selection (will show picker on dual-SIM if no default)
                Log.d(TAG, "Placing call with system default SIM")
            }

            // Place the call
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                telecomManager.placeCall(uri, extras)
                return true
            } else {
                Log.e(TAG, "Missing CALL_PHONE permission")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "TelecomManager.placeCall failed: ${e.message}, falling back to intent", e)
            return placeCallWithIntent(uri)
        }
    }

    /**
     * Fallback method using Intent for older devices or when TelecomManager fails
     */
    private fun placeCallWithIntent(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                context.startActivity(intent)
                true
            } else {
                Log.e(TAG, "Missing CALL_PHONE permission for intent")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Intent-based call failed: ${e.message}", e)
            false
        }
    }

    /**
     * Show SIM selection dialog (system picker) before making a call
     * Works on dual-SIM devices
     * 
     * @param phoneNumber The number to call
     * @return true if picker shown or call initiated
     */
    fun makeCallWithSimPicker(phoneNumber: String): Boolean {
        // On dual-SIM devices, passing no phone account will trigger system picker
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && 
                   simManager.isDualSimDevice()) {
            Log.d(TAG, "Showing SIM picker for dual-SIM device")
            makeCall(phoneNumber, -1) // -1 triggers system picker
        } else {
            // Single SIM or older device
            makeCall(phoneNumber, -1)
        }
    }

    /**
     * Get available SIMs for selection UI
     * @return List of available SIM cards
     */
    fun getAvailableSims(): List<SimCardInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            simManager.getAvailableSimCards()
        } else {
            emptyList()
        }
    }

    /**
     * Check if device has multiple SIM capability
     */
    fun isDualSimDevice(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            simManager.isDualSimDevice()
        } else {
            false
        }
    }

    /**
     * Save SIM preference for a contact
     */
    fun saveSimPreferenceForContact(phoneNumber: String, subscriptionId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            simManager.saveContactSimPreference(phoneNumber, subscriptionId)
        }
    }

    /**
     * Get saved SIM preference for a contact
     * @return Subscription ID or -1 if no preference
     */
    fun getSimPreferenceForContact(phoneNumber: String): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            simManager.getContactSimPreference(phoneNumber)
        } else {
            -1
        }
    }

    /**
     * Clear SIM preference for a contact
     */
    fun clearSimPreferenceForContact(phoneNumber: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            simManager.clearContactSimPreference(phoneNumber)
        }
    }

    /**
     * Get human-readable SIM info for display
     */
    fun getSimDisplayName(subscriptionId: Int): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            simManager.getSimDisplayInfo(subscriptionId)
        } else {
            null
        }
    }
}
