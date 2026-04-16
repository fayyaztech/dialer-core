package com.fayyaztech.dialer_core.model

import android.bluetooth.BluetoothDevice

// DISCONNECT_TRACKING
/**
 * Snapshot of call context captured at STATE_RINGING and updated through STATE_ACTIVE.
 * Used by DisconnectReasonClassifier to determine the exact disconnect reason.
 *
 * All fields are mutable so the live snapshot can be updated by dynamic BroadcastReceivers
 * and onCallAudioStateChanged throughout the call lifetime.
 */
data class CallSnapshot(
    /** True once the call has entered STATE_RINGING */
    var wasRinging: Boolean = false,
    /** Current audio route from CallAudioState (ROUTE_EARPIECE, ROUTE_SPEAKER, etc.) */
    var audioRoute: Int = 0,
    /** Bluetooth device that was connected when the call started, if any */
    var bluetoothDevice: BluetoothDevice? = null,
    /** True if ACTION_SCREEN_OFF fired while the call was ringing or active */
    var screenWasOff: Boolean = false,
    /** True if the power/side key was pressed (same signal as screenWasOff for call context) */
    var powerKeyPressed: Boolean = false,
    /** True if airplane mode was enabled during the call */
    var airplaneModeOn: Boolean = false,
    /** True if SIM state changed to ABSENT during the call */
    var simRemoved: Boolean = false,
    /** True if a Bluetooth ACL disconnection was observed during the call */
    var bluetoothLostJustBefore: Boolean = false,
    /** "incoming" or "outgoing" */
    var callDirection: String = "unknown",
    /** Signal strength level (0-4) at call start, -1 if unavailable */
    var signalStrength: Int = -1,
    /** Timestamp when this snapshot was first created */
    val snapshotTimeMs: Long = System.currentTimeMillis()
)
