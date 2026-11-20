# ZA Call Recorder (Android) - v3 (Pixel / Android 14+ Fix)

Automatic Phone Call Recorder & Voice Memo app for Android, configured to build automatically into an `.apk` using **GitHub Actions**.

## What's Fixed in v3
1. **Fixes Silent Recordings on Pixel / Android 10+**: Moves `MediaRecorder` directly inside `CallAccessibilityService` using `AudioSource.VOICE_RECOGNITION` (the only audio source AOSP allows an Accessibility Service to capture unmuted during a cellular call).
2. **Fixes Missing Caller Name & Phone Number**: Queries Android's Call Log & Contacts automatically after the call ends to label every recording with the **Caller Name**, **Phone Number**, and **Direction (`IN` / `OUT`)**.
3. **Modern Speakerphone Routing (Android 12+)**: Uses `AudioManager.setCommunicationDevice()` on Android 12+ (Pixel 7) when Auto Speakerphone Boost is enabled.
4. **Working Tabs**: `ALL`, `INCALL`, and `OUTCALL` now filter recordings automatically.

## How to Build the APK on GitHub
1. Upload all files (including `.github/workflows/build-apk.yml`) to your GitHub repository.
2. Open the **Actions** tab -> **Build ZA Call Recorder APK**.
3. Download **`ZA-Call-Recorder-debug-apk`** from **Artifacts**, unzip, and install `app-debug.apk`.
