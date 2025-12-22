# Dialer Core Module

A reusable Android library module providing complete telephony and in-call functionality for dialer applications. This module handles all call-related services, in-call UI, and Telecom framework integration, allowing you to focus on customizing contacts, dialpad, and other UI features in your main app.

## Overview

The `dialer-core` module provides:

- **Complete Telecom Framework Integration**: InCallService, CallScreeningService, and call state management
- **Modern In-Call UI**: Built with Jetpack Compose and Material3 design
- **Call Management**: Audio routing (speaker/earpiece/bluetooth), mute, hold, merge, swap
- **Notification Support**: Android 12+ `CallStyle` notifications (non-dismissible), persistent across multi-call scenarios.
- **Advanced Multi-Call Support**: Conference calling with automatic state recovery, selection-based swapping, and intelligent focus management.
- **Lock Screen Support**: Full-screen intent for incoming calls, screen management with proximity sensor
- **OEM Compatibility**: Tested workarounds for Samsung, Xiaomi, and other OEM-specific issues
- **Future Ready**: Roadmap includes Flutter support and cross-platform extensions.

## Architecture

### Core Components

#### Services

1. **DefaultInCallService** (`com.fayyaztech.dialer_core.services.DefaultInCallService`)
   - Binds to Android Telecom framework
   - Manages call lifecycle (onCallAdded, onCallRemoved)
   - Handles audio routing and state changes
   - Launches CallScreenActivity for incoming/outgoing calls
   - Manages foreground notifications

2. **CallScreeningService** (`com.fayyaztech.dialer_core.services.CallScreeningService`)
   - Implements Telecom CallScreeningService
   - Placeholder for spam detection/call blocking logic
   - Can be extended for custom call screening

3. **CallStateObserverService** (`com.fayyaztech.dialer_core.services.CallStateObserverService`)
   - Lightweight foreground service for call state monitoring
   - Observes IDLE transitions for analytics/telemetry
   - Minimal implementation to avoid race conditions with Telecom

4. **CallActionReceiver** (`com.fayyaztech.dialer_core.services.CallActionReceiver`)
   - Handles notification actions (accept, reject, hangup)
   - Bridges notification taps to call actions

5. **IncomingCallReceiver** (`com.fayyaztech.dialer_core.services.IncomingCallReceiver`)
   - Listens for PHONE_STATE broadcasts
   - Launches CallScreenActivity for incoming calls
   - Includes debouncing logic to prevent duplicate launches

#### UI Components

1. **CallScreenActivity** (`com.fayyaztech.dialer_core.ui.call.CallScreenActivity`)
   - Full-featured in-call UI built with Jetpack Compose
   - Features:
     - Contact name resolution
     - Call duration timer
     - Audio routing menu (speaker, earpiece, bluetooth)
     - Mute/unmute with visual feedback
     - Hold/resume with multi-call support
     - DTMF keypad for entering digits during calls
     - Merge/swap/conference for multi-call scenarios
     - Proximity sensor integration (screen off near face)
   - Handles lock screen display with proper flags
   - Material3 themed with dark mode support

2. **Theme** (`com.fayyaztech.dialer_core.ui.theme`)
   - DefaultDialerTheme with Material3 color scheme
   - Dark-first design with lime accent
   - Can be used or overridden in your app

## Integration Guide

### Step 1: Add Module Dependency

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":dialer-core"))
    // ... other dependencies
}
```

### Step 2: Configure Your App Manifest

The dialer-core module automatically declares all required components and permissions through manifest merging. Your app manifest only needs to declare your main Activity and dialer intent filters:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Note: Telephony permissions are declared in dialer-core -->
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.YourApp">
        
        <!-- Your main launcher activity with dialer intent filters -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Dialer intent filters -->
            <intent-filter>
                <action android:name="android.intent.action.DIAL" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.DIAL" />
                <action android:name="android.intent.action.CALL" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:scheme="tel" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="tel" />
            </intent-filter>
        </activity>
        
        <!-- CallScreenActivity and services are automatically included from dialer-core -->
    </application>
</manifest>
```

### Step 3: Request Default Dialer Role

Before your app can handle calls, the user must set it as the default dialer (Android 6.0+):

```kotlin
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager

fun requestDefaultDialerRole(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = activity.getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                activity.startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_DIALER)
            }
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val telecomManager = activity.getSystemService(TelecomManager::class.java)
        val currentDefaultDialer = telecomManager.getDefaultDialerPackage()
        if (activity.packageName != currentDefaultDialer) {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
            activity.startActivity(intent)
        }
    }
}

companion object {
    const val REQUEST_CODE_SET_DEFAULT_DIALER = 123
}
```

### Step 4: Request Runtime Permissions

Request all required permissions at runtime:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

fun requestDialerPermissions(activity: Activity) {
    val permissions = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    
    val permissionsToRequest = permissions.filter {
        ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
    }
    
    if (permissionsToRequest.isNotEmpty()) {
        ActivityCompat.requestPermissions(
            activity,
            permissionsToRequest.toTypedArray(),
            REQUEST_CODE_PERMISSIONS
        )
    }
}

companion object {
    const val REQUEST_CODE_PERMISSIONS = 124
}
```

### Step 5: Make Outgoing Calls

Use Android's standard ACTION_CALL intent:

```kotlin
import android.content.Intent
import android.net.Uri

fun makeCall(context: Context, phoneNumber: String) {
    val intent = Intent(Intent.ACTION_CALL).apply {
        data = Uri.parse("tel:$phoneNumber")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) 
        == PackageManager.PERMISSION_GRANTED) {
        context.startActivity(intent)
    } else {
        // Request CALL_PHONE permission
    }
}
```

### Step 6: Access Call State (Optional)

Access the current call from your app:

```kotlin
import com.fayyaztech.dialer_core.services.DefaultInCallService
import android.telecom.Call

// Get current active call
val currentCall: Call? = DefaultInCallService.currentCall

// Get number of active calls
val callCount: Int = DefaultInCallService.getActiveCallCount()

// Get all active calls
val allCalls: List<Call> = DefaultInCallService.getAllCalls()

// Control call audio
DefaultInCallService.muteCall(true)  // Mute
DefaultInCallService.setSpeaker(true)  // Enable speaker
```

## Theming and Customization

### Using the Default Theme

The dialer-core provides a `DefaultDialerTheme` you can use in your app:

```kotlin
import com.fayyaztech.dialer_core.ui.theme.DefaultDialerTheme

setContent {
    DefaultDialerTheme {
        // Your composables here
    }
}
```

### Customizing the CallScreen UI

The `CallScreenActivity` uses the theme from dialer-core by default. To customize:

**Option 1: Extend DefaultDialerTheme**

Create your own theme that extends or modifies the colors:

```kotlin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CustomColorScheme = darkColorScheme(
    primary = Color(0xFF00FF00),  // Your custom color
    // ... other colors
)

@Composable
fun YourAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CustomColorScheme,
        content = content
    )
}
```

**Option 2: Override CallScreenActivity**

For complete UI control, extend `CallScreenActivity` and override the `onCreate` to use your own Composable:

```kotlin
class CustomCallScreenActivity : com.fayyaztech.dialer_core.ui.call.CallScreenActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            YourAppTheme {
                // Your custom call screen UI
                CustomCallScreen(...)
            }
        }
    }
}
```

Then declare your custom activity in your app's manifest:

```xml
<activity
    android:name=".CustomCallScreenActivity"
    android:exported="true"
    android:launchMode="singleTop"
    android:showWhenLocked="true"
    android:turnScreenOn="true">
    <!-- Same intent filters as CallScreenActivity -->
</activity>
```

### Customizing Call Notifications

Override the notification creation in `DefaultInCallService` by extending it:

```kotlin
class CustomInCallService : com.fayyaztech.dialer_core.services.DefaultInCallService() {
    // Override notification methods as needed
}
```

## Key Features Explained

### Audio Routing

The module handles audio routing robustly:

- **Smart Mode Management**: Only resets audio mode to `NORMAL` when the *last* call is disconnected, preventing audio drops.
- **Audio Focus Handling**: Non-blocking request/abandon logic with clear-down protection.
- **Bluetooth Support**: Seamless switching between bluetooth, speaker, and earpiece.
- **Carrier Compatibility**: Fallback strategies for merge/swap on sensitive networks.

### Proximity Sensor Integration

- Automatically turns screen off when phone is near face (earpiece mode)
- Keeps screen on when speaker is enabled
- Prevents accidental touches during calls

### Multi-Call Support

- **Hold/Resume**: Robust transition handling to prevent audio drops.
- **Swap**: Manual selection menu for 2+ calls; uses sequential hold/unhold with 300ms safety delays.
- **Merge**: Conference two calls with **automatic focus restoration** and unhold fallbacks.
- **Add Call**: Intent-based dialer launch with automatic management of current calls.
- **Notification Persistence**: Notification stays active and non-dismissible until the final call is closed.

### Lock Screen Handling

- Uses proper flags for Android 12+ lock screen display
- `setShowWhenLocked(true)` and `setTurnScreenOn(true)` for API 27+
- Full-screen intent for incoming calls (Android 13+)

### Call State Tracking

The module tracks:
- Disconnect reasons (local, remote, missed, rejected, busy, error)
- Call duration
- Multi-call states
- Audio routing state

## Testing

### Testing on Emulator

1. Set your app as default dialer
2. Open Phone app and make a test call
3. Your app's CallScreenActivity should appear

### Testing Incoming Calls (Emulator)

```bash
# Simulate incoming call
adb shell am start -a android.intent.action.CALL_PRIVILEGED -d tel:555-1234

# Or use emulator extended controls
# Click "..." → Phone → Set incoming call number → Call Device
```

### Testing Multi-Call Scenarios

1. Make a call from your app
2. Put call on hold
3. Make another call
4. Test merge/swap functionality

## Troubleshooting

### App Not Receiving Calls

- Ensure app is set as default dialer
- Check all permissions are granted
- Verify `DefaultInCallService` is declared in merged manifest
- Check Logcat for binding errors

### Audio Routing Issues

- Check `MODIFY_AUDIO_SETTINGS` permission
- Verify audio focus is being requested
- Check device-specific audio routing (some OEMs override behavior)

### Lock Screen Not Showing Call UI

- Verify `USE_FULL_SCREEN_INTENT` permission (Android 13+)
- Check window flags in `CallScreenActivity`
- Ensure `setShowWhenLocked(true)` is called

### CallScreenActivity Not Launching

- Check intent filters in merged manifest
- Verify activity is exported
- Check for SecurityException in logs

## API Reference

### DefaultInCallService

```kotlin
companion object {
    // Current active call
    var currentCall: Call?
    
    // Get number of active calls
    fun getActiveCallCount(): Int
    
    // Get all active calls
    fun getAllCalls(): List<Call>
    
    // Control microphone
    fun muteCall(isMuted: Boolean)
    
    // Control speaker
    fun setSpeaker(enable: Boolean)
    
    // Set audio route
    fun setAudioRoute(route: Int)
    
    // Audio focus management
    fun abandonAudioFocus()
    
    // Notification control
    fun cancelNotification()
}
```

### CallScreenActivity

Public methods accessible when extending:

```kotlin
// Answer incoming call
private fun answerCall()

// Reject incoming call
private fun rejectCall()

// End active call
private fun endCall()

// Toggle mute
private fun toggleMute()

// Set audio route
private fun setAudioRoute(route: Int)

// Toggle hold
private fun toggleHold()

// Send DTMF tone
private fun sendDtmf(digit: Char)

// Get contact name from number
private fun getContactName(phoneNumber: String): String?
```

## Dependencies

The module uses:

- **AndroidX Core**: Core Android utilities
- **Jetpack Compose**: UI framework
- **Material3**: Design system
- **libphonenumber**: Phone number formatting and validation

## Version Compatibility

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 15+)
- **Compile SDK**: 36

Tested on Android versions 7.0 through 15.

## License

This module is part of the DefaultDialer project.

## Support

For issues, questions, or contributions related to the dialer-core module, please refer to the main project repository.

---

---

**Maintained by [FayyazTech](https://github.com/fayyaztech)**

**Note**: This is a reusable module designed for integration into multiple dialer applications. The main app module (containing contacts, dialpad, history screens) is kept separate for easy customization per app.
