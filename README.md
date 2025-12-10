# Dialer Core

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)

A reusable Android library module that provides core telephony/dialer functionality for building custom dialer applications.

## Overview

`dialer-core` handles all the complex Android Telecom framework integration, call management, and in-call UI, allowing you to focus on building custom contacts, call history, and dialpad screens for your dialer app.

### What's Included

- ✅ Complete `InCallService` implementation
- ✅ Modern Jetpack Compose in-call UI with Material3
- ✅ Multi-call support (hold, swap, merge, conference)
- ✅ Audio routing (earpiece, speaker, bluetooth)
- ✅ Call screening framework
- ✅ Lock screen support with full-screen incoming call UI
- ✅ DTMF keypad for tone dialing
- ✅ Proximity sensor integration
- ✅ OEM compatibility workarounds

## Quick Start

### Installation

Add to your `settings.gradle.kts`:
```kotlin
include(":dialer-core")
```

Add dependency in `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":dialer-core"))
}
```

### Basic Setup

1. **Request Default Dialer Role:**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val roleManager = getSystemService(RoleManager::class.java)
    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    startActivityForResult(intent, REQUEST_CODE)
}
```

2. **Add Dialer Intent Filters** to your MainActivity:
```xml
<intent-filter>
    <action android:name="android.intent.action.DIAL" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

3. **Make Calls:**
```kotlin
val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:1234567890"))
startActivity(intent)
```

That's it! The module handles incoming calls, in-call UI, audio routing, and call management automatically.

## Features

### Call Management
- Automatic incoming call detection and UI display
- Full-screen lock screen notifications
- Active call state management
- Call audio routing (earpiece/speaker/bluetooth)
- Microphone mute/unmute

### Multi-Call Operations
- Hold and resume calls
- Swap between multiple calls
- Merge calls into conference
- Add new call during active call

### In-Call UI
- Beautiful Material3 Compose design
- Real-time call duration timer
- Contact name resolution
- Interactive DTMF keypad
- Audio route selector (phone/speaker/bluetooth)
- Visual call state indicators

### Services
- `DefaultInCallService` - Core Telecom integration
- `CallScreeningService` - Spam detection framework
- `CallStateObserverService` - Background monitoring
- `CallActionReceiver` - Notification actions
- `IncomingCallReceiver` - Phone state handling

## Architecture

```
YourDialerApp/
├── app/                          # Your custom UI
│   ├── MainActivity.kt          # Tabs, navigation
│   ├── ui/
│   │   ├── dialpad/            # Custom dialpad
│   │   ├── contacts/           # Contact list
│   │   └── history/            # Call history
│   └── data/                   # ViewModels, repos
│
└── dialer-core/                 # This module
    ├── services/
    │   ├── DefaultInCallService.kt
    │   ├── CallScreeningService.kt
    │   └── ...
    └── ui/
        └── call/
            └── CallScreenActivity.kt  # In-call screen
```

## Requirements

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 15+)
- **Kotlin**: 1.9+
- **Compose**: BOM 2024.01.00+

## Documentation

- [Full Integration Guide](README-integration.md)
- [API Reference](#api-reference)
- [Customization Guide](#customization)

## Customization

### Custom Theme

```kotlin
import com.fayyaztech.dialer_core.ui.theme.DefaultDialerTheme

// Use your own theme instead
YourCustomTheme {
    // Your app content
}
```

### Extend Services

```kotlin
class CustomInCallService : DefaultInCallService() {
    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        // Your custom logic
    }
}
```

## Permissions

All required permissions are declared in the module manifest and automatically merged:

- `CALL_PHONE`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `WRITE_CALL_LOG`
- `READ_CONTACTS`, `ANSWER_PHONE_CALLS`, `RECORD_AUDIO`
- `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`

You must still request runtime permissions in your app for Android 6.0+.

## API Reference

### DefaultInCallService

```kotlin
// Access current call
DefaultInCallService.currentCall: Call?

// Control audio
DefaultInCallService.muteCall(isMuted: Boolean)
DefaultInCallService.setSpeaker(enable: Boolean)

// Multi-call info
DefaultInCallService.getActiveCallCount(): Int
DefaultInCallService.getAllCalls(): List<Call>
```

## Testing

### Emulator
```bash
# Simulate incoming call
adb emu gsm call 5551234567

# Simulate call end
adb emu gsm cancel 5551234567
```

### Testing Checklist
- [ ] Make outgoing call
- [ ] Receive incoming call
- [ ] Answer from lock screen
- [ ] Hold/resume
- [ ] Mute/unmute
- [ ] Switch audio routes
- [ ] Multi-call operations
- [ ] DTMF tones

## Building

```bash
./gradlew :dialer-core:assemble
./gradlew :dialer-core:test
```

## Contributing

Contributions welcome! Please submit PRs with:
- Clear description
- Tests for new features
- Documentation updates

## License

```
Copyright 2025 Fayyaz Tech

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
```

See [LICENSE](LICENSE) for full details.

## Support

- **Issues**: [GitHub Issues](https://github.com/fayyaztech-sycet/dialer-core/issues)
- **Email**: support@fayyaztech.com

## Related Projects

- [DefaultDialer](https://github.com/fayyaztech-sycet/DefaultDialer) - Example implementation

---

**Made with ❤️ by Fayyaz Tech**
