package com.fayyaztech.dialer_core.model

// DISCONNECT_TRACKING
/**
 * Fine-grained classification of why a call ended.
 * Derived by DisconnectReasonClassifier from DisconnectCause code + CallSnapshot context.
 */
enum class DisconnectReason {
    // User actions
    USER_INITIATED,        // red end-call button while ACTIVE
    USER_REJECT_RINGING,   // red button / swipe-dismiss while RINGING
    POWER_KEY_END,         // power button pressed (SCREEN_OFF during ringing/active)
    NOTIFICATION_REJECT,   // declined from notification or system lock screen

    // System / hardware
    BLUETOOTH_DISCONNECT,  // BT audio device lost connection mid-call
    APP_PROCESS_DIED,      // process was killed while call was live
    AUDIO_FOCUS_LOST,      // permanent audio focus loss during call

    // Network / radio
    SIGNAL_LOST,           // radio drop / no signal
    AIRPLANE_MODE,         // airplane mode toggled on during call
    SIM_REMOVED,           // SIM state changed to ABSENT
    HANDOVER_FAILED,       // VoLTE / WiFi calling handover failure

    // Remote party
    REMOTE_HANGUP,         // far end hung up normally
    REMOTE_REJECT_BUSY,    // line was busy or remote rejected
    NO_ANSWER_TIMEOUT,     // outgoing call timed out without answer

    UNKNOWN
}
