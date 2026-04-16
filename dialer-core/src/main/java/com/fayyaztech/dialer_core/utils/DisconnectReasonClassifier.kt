package com.fayyaztech.dialer_core.utils

import android.telecom.DisconnectCause
import com.fayyaztech.dialer_core.model.CallSnapshot
import com.fayyaztech.dialer_core.model.DisconnectReason

// DISCONNECT_TRACKING
/**
 * Pure classifier — no side effects, no I/O, easy to unit test.
 *
 * Accepts the raw Android DisconnectCause code and the CallSnapshot collected during the call,
 * and returns the most specific DisconnectReason that matches the observed signals.
 *
 * Classification priority order (most-specific first):
 *   LOCAL  → POWER_KEY_END if ringing + screen off
 *          → USER_REJECT_RINGING if ringing
 *          → USER_INITIATED otherwise
 *   REMOTE → REMOTE_HANGUP
 *   BUSY   → REMOTE_REJECT_BUSY
 *   MISSED → NO_ANSWER_TIMEOUT
 *   REJECTED → NOTIFICATION_REJECT  (system / lock-screen rejection)
 *   ERROR  → BLUETOOTH_DISCONNECT if BT lost just before
 *          → AIRPLANE_MODE if airplane mode turned on
 *          → SIM_REMOVED if SIM went absent
 *          → SIGNAL_LOST otherwise
 *   else   → UNKNOWN
 */
object DisconnectReasonClassifier {

    fun classify(causeCode: Int, snapshot: CallSnapshot): DisconnectReason {
        return when (causeCode) {
            DisconnectCause.LOCAL -> when {
                snapshot.wasRinging && snapshot.screenWasOff -> DisconnectReason.POWER_KEY_END
                snapshot.wasRinging                          -> DisconnectReason.USER_REJECT_RINGING
                else                                         -> DisconnectReason.USER_INITIATED
            }
            DisconnectCause.REMOTE   -> DisconnectReason.REMOTE_HANGUP
            DisconnectCause.BUSY     -> DisconnectReason.REMOTE_REJECT_BUSY
            DisconnectCause.MISSED   -> DisconnectReason.NO_ANSWER_TIMEOUT
            DisconnectCause.REJECTED -> DisconnectReason.NOTIFICATION_REJECT
            DisconnectCause.ERROR    -> when {
                snapshot.bluetoothLostJustBefore -> DisconnectReason.BLUETOOTH_DISCONNECT
                snapshot.airplaneModeOn          -> DisconnectReason.AIRPLANE_MODE
                snapshot.simRemoved              -> DisconnectReason.SIM_REMOVED
                else                             -> DisconnectReason.SIGNAL_LOST
            }
            else -> DisconnectReason.UNKNOWN
        }
    }
}
