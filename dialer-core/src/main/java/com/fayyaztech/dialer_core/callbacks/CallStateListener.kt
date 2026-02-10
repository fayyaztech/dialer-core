package com.fayyaztech.dialer_core.callbacks

/**
 * Generic callback interface for call state changes.
 * This allows any platform (Flutter, React Native, native Android, etc.)
 * to listen for and respond to call state changes without the core module
 * having any knowledge of the consuming platform.
 */
interface CallStateListener {
    /**
     * Called when a call transitions to ACTIVE state (call was answered).
     *
     * @param phoneNumber The phone number involved in the call
     * @param callDirection The direction of the call: "incoming" or "outgoing"
     * @param callTimestampMs The timestamp when the call started, in milliseconds
     */
    fun onCallAnswered(
        phoneNumber: String,
        callDirection: String,
        callTimestampMs: Long
    )

    /**
     * Called when a call transitions to DISCONNECTED state (call ended).
     *
     * @param phoneNumber The phone number involved in the call
     * @param callDirection The direction of the call: "incoming" or "outgoing"
     * @param disconnectReason The raw disconnect reason from Android Telecom DisconnectCause
     * @param disconnectCauseCode The numeric code from DisconnectCause (e.g., LOCAL, REMOTE, MISSED)
     */
    fun onCallEnded(
        phoneNumber: String,
        callDirection: String,
        disconnectReason: String,
        disconnectCauseCode: Int
    )

    /**
     * Called when a new call is added to the system.
     *
     * @param phoneNumber The phone number of the new call
     * @param callDirection The direction: "incoming" or "outgoing"
     */
    fun onCallAdded(phoneNumber: String, callDirection: String) {}

    /**
     * Called when a call is removed from the system.
     *
     * @param phoneNumber The phone number of the removed call
     */
    fun onCallRemoved(phoneNumber: String) {}
}
