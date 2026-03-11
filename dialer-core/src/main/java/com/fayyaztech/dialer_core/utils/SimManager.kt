package com.fayyaztech.dialer_core.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat

/**
 * Utility class for managing SIM cards and dual-SIM scenarios.
 * 
 * Supports:
 * - Android 5.1+ (API 22) for basic multi-SIM
 * - Android 10+ (API 29) enhanced SIM selection
 * - OEM compatibility: Samsung, Xiaomi, OnePlus, Oppo, Vivo
 * 
 * Features:
 * - Detect available SIM cards
 * - Get SIM details (carrier name, number, slot)
 * - Select preferred SIM for outgoing calls
 * - Handle per-contact SIM preferences
 */
class SimManager(private val context: Context) {

    companion object {
        private const val TAG = "SimManager"
        
        // Preference keys
        const val PREF_SIM_SELECTION = "sim_selection_prefs"
        const val PREF_DEFAULT_SIM_PREFIX = "default_sim_"
        const val PREF_CONTACT_SIM_PREFIX = "contact_sim_"
    }

    private val telecomManager: TelecomManager? = 
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        
    private val telephonyManager: TelephonyManager? = 
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        
    private val subscriptionManager: SubscriptionManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    } else null

    /**
     * Check if device supports dual SIM or multiple SIM cards
     */
    fun isDualSimDevice(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ - Reliable method
                telephonyManager?.activeModemCount ?: 0 > 1
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // Android 5.1+ - Subscription-based detection
                getAvailableSimCards().size > 1
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect dual SIM: ${e.message}")
            false
        }
    }

    /**
     * Get list of available SIM cards with details
     * @return List of SIM card information
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun getAvailableSimCards(): List<SimCardInfo> {
        val simCards = mutableListOf<SimCardInfo>()
        
        try {
            // Check permission
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "READ_PHONE_STATE permission not granted")
                return simCards
            }

            // Get active subscriptions
            val subscriptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            } else {
                emptyList()
            }

            subscriptions.forEachIndexed { index, subInfo ->
                try {
                    val simInfo = SimCardInfo(
                        subscriptionId = subInfo.subscriptionId,
                        slotIndex = subInfo.simSlotIndex,
                        displayName = subInfo.displayName?.toString() ?: "SIM ${index + 1}",
                        carrierName = subInfo.carrierName?.toString() ?: "Unknown Carrier",
                        phoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            subInfo.number ?: ""
                        } else {
                            @Suppress("DEPRECATION")
                            subInfo.number ?: ""
                        },
                        countryIso = subInfo.countryIso ?: "",
                        isEmbedded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            subInfo.isEmbedded
                        } else {
                            false
                        },
                        isActive = true
                    )
                    simCards.add(simInfo)
                    Log.d(TAG, "Found SIM: $simInfo")
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading SIM ${index + 1}: ${e.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception accessing SIM info: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SIM cards: ${e.message}")
        }

        return simCards
    }

    /**
     * Get phone account handles for making calls
     * This is used with TelecomManager.placeCall()
     */
    fun getPhoneAccountHandles(): List<PhoneAccountHandle> {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                emptyList()
            } else {
                telecomManager?.callCapablePhoneAccounts ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get phone accounts: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get phone account for a specific SIM subscription
     * @param subscriptionId The subscription ID from SimCardInfo
     * @return PhoneAccountHandle for the SIM, or null if not found
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun getPhoneAccountForSubscription(subscriptionId: Int): PhoneAccountHandle? {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }

            val phoneAccounts = telecomManager?.callCapablePhoneAccounts ?: return null
            
            // Find matching phone account
            phoneAccounts.find { handle ->
                try {
                    val account = telecomManager?.getPhoneAccount(handle)
                    // Try to match by subscription ID in extras
                    val accountSubId = account?.extras?.getInt("android.telecom.extra.SUBSCRIPTION_ID", -1) ?: -1
                    accountSubId == subscriptionId
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get phone account for subscription $subscriptionId: ${e.message}")
            null
        }
    }

    /**
     * Get the default SIM for voice calls
     * @return Subscription ID of default SIM, or -1 if not set
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun getDefaultVoiceSubscriptionId(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SubscriptionManager.getDefaultVoiceSubscriptionId()
            } else {
                -1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get default voice SIM: ${e.message}")
            -1
        }
    }

    /**
     * Save preferred SIM for a contact
     * @param contactNumber The phone number of the contact
     * @param subscriptionId The preferred SIM subscription ID
     */
    fun saveContactSimPreference(contactNumber: String, subscriptionId: Int) {
        try {
            val prefs = context.getSharedPreferences(PREF_SIM_SELECTION, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("$PREF_CONTACT_SIM_PREFIX${normalizePhoneNumber(contactNumber)}", subscriptionId)
                .apply()
            Log.d(TAG, "Saved SIM preference for $contactNumber: SIM $subscriptionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save contact SIM preference: ${e.message}")
        }
    }

    /**
     * Get preferred SIM for a contact
     * @param contactNumber The phone number of the contact
     * @return Subscription ID, or -1 if no preference set
     */
    fun getContactSimPreference(contactNumber: String): Int {
        return try {
            val prefs = context.getSharedPreferences(PREF_SIM_SELECTION, Context.MODE_PRIVATE)
            prefs.getInt("$PREF_CONTACT_SIM_PREFIX${normalizePhoneNumber(contactNumber)}", -1)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get contact SIM preference: ${e.message}")
            -1
        }
    }

    /**
     * Clear SIM preference for a contact
     */
    fun clearContactSimPreference(contactNumber: String) {
        try {
            val prefs = context.getSharedPreferences(PREF_SIM_SELECTION, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("$PREF_CONTACT_SIM_PREFIX${normalizePhoneNumber(contactNumber)}")
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear contact SIM preference: ${e.message}")
        }
    }

    /**
     * Normalize phone number for consistent storage
     * Removes spaces, dashes, parentheses and keeps only digits and +
     */
    private fun normalizePhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9+]"), "")
    }

    /**
     * Get human-readable SIM information
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun getSimDisplayInfo(subscriptionId: Int): String? {
        val simCards = getAvailableSimCards()
        return simCards.find { it.subscriptionId == subscriptionId }?.let { sim ->
            "${sim.displayName} (${sim.carrierName})"
        }
    }

    /**
     * Check if a specific SIM is available and active
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun isSimActive(subscriptionId: Int): Boolean {
        return getAvailableSimCards().any { 
            it.subscriptionId == subscriptionId && it.isActive 
        }
    }
}

/**
 * Data class representing SIM card information
 */
data class SimCardInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val phoneNumber: String,
    val countryIso: String,
    val isEmbedded: Boolean = false,
    val isActive: Boolean = true
) {
    /**
     * Convert to map for Flutter communication
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "subscriptionId" to subscriptionId,
        "slotIndex" to slotIndex,
        "displayName" to displayName,
        "carrierName" to carrierName,
        "phoneNumber" to phoneNumber,
        "countryIso" to countryIso,
        "isEmbedded" to isEmbedded,
        "isActive" to isActive
    )
}
