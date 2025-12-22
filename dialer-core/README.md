# Dialer Core Library - Versioning Guide

## 📦 Library Versioning Strategy

This library follows **Semantic Versioning (SemVer)**: `MAJOR.MINOR.PATCH`

### Version Format
- **MAJOR**: Breaking changes (API changes, removed features)
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

### Current Version: `1.0.0`

## 🔧 Version Management

### 1. Version Configuration

Versions are managed in `gradle.properties`:

```properties
# Current Version
DIALER_CORE_VERSION_CODE=1
DIALER_CORE_VERSION_NAME=1.0.0
```

### 2. Updating Versions

#### For Minor/Patch Releases:
1. Update `gradle.properties`:
   ```properties
   DIALER_CORE_VERSION_CODE=2
   DIALER_CORE_VERSION_NAME=1.0.1  # or 1.1.0
   ```

2. Update version history below

3. Commit and tag:
   ```bash
   git add .
   git commit -m "Release v1.0.1: Fix audio routing bug"
   git tag -a v1.0.1 -m "Version 1.0.1"
   git push origin v1.0.1
   ```

#### For Major Releases:
1. Update `gradle.properties`:
   ```properties
   DIALER_CORE_VERSION_CODE=100  # Major versions get higher codes
   DIALER_CORE_VERSION_NAME=2.0.0
   ```

2. Update API compatibility notes

3. Tag as major release

### 3. Using Specific Versions

When depending on this library, you can reference specific versions:

```kotlin
// In your app's build.gradle.kts
dependencies {
    implementation("com.fayyaztech:dialer-core:1.0.0")
}
```

## 📋 Version History

### v1.0.0 (2025-12-11) - Initial Release
- ✅ Core telephony services (InCallService, CallScreeningService, etc.)
- ✅ CallScreenActivity with Jetpack Compose UI
- ✅ Multi-call support (hold/swap/merge)
- ✅ Audio routing (speaker, bluetooth, earpiece)
- ✅ DTMF keypad
- ✅ Material3 theming and dark mode
- ✅ Proximity sensor management
- ✅ Notification management
- ✅ Call state observation

### Future Releases
- v1.1.0: Enhanced call recording features
- v1.2.0: Video call support
- v2.0.0: Major API refactoring (breaking changes)

## 🚀 Release Process

1. **Update version** in `gradle.properties`
2. **Test thoroughly** - run all tests and integration tests
3. **Update documentation** - README, API docs
4. **Commit changes** with clear release message
5. **Create git tag** with version
6. **Publish to repository** (GitHub, Maven Central, etc.)
7. **Update dependent projects** to use new version

## 🔄 Dependency Management

### For Local Development
```kotlin
// settings.gradle.kts
include(":dialer-core")
project(":dialer-core").projectDir = File("../dialer-core-repo/dialer-core")
```

### For Published Library
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.fayyaztech:dialer-core:1.0.0")
}
```

## ⚠️ Breaking Changes Policy

- Major version bumps require migration guides
- Deprecated APIs are marked with `@Deprecated` annotation
- Breaking changes are documented in release notes
- Migration period of 6 months for major versions

## 🏷️ Git Tagging Strategy

```bash
# Release tags
git tag -a v1.0.0 -m "Release version 1.0.0"
git tag -a v1.0.1 -m "Hotfix: Audio routing issue"

# Pre-release tags
git tag -a v1.1.0-beta -m "Beta release with new features"
git tag -a v2.0.0-rc.1 -m "Release candidate for v2.0.0"
```

## 📊 Version Compatibility Matrix

| Dialer Core | Min Android | Target Android | Kotlin | Compose |
|-------------|-------------|----------------|--------|---------|
| 1.0.x      | API 24     | API 36        | 2.0.21| 2024.09|
| 1.1.x      | API 24     | API 36        | 2.0.21| 2024.09|
| 2.0.x      | API 26     | API 37        | 2.1.x | 2025.xx|