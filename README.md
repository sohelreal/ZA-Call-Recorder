# ZA Call Recorder (Android) - Two-Sided Recording Edition

Automatic Phone Call Recorder & Voice Memo app for Android, configured to build automatically into an `.apk` using **GitHub Actions**.

## Two-Sided Recording Setup (Important for Android 10+)
Because Google restricts direct internal call-line capture (`VOICE_CALL`) on unrooted Android 10+ devices, this app uses three mechanisms to capture **both sides** of every call:
1. **Accessibility Service (`CallAccessibilityService`)**: Keeps audio capture active during phone calls (same method used by Cube ACR). Open the app drawer -> **Enable Accessibility Service** -> turn on **ZA Call Recorder**.
2. **Smart Audio Source Fallback**: Automatically tries `VOICE_CALL` -> `VOICE_COMMUNICATION` -> `VOICE_RECOGNITION` -> `MIC`.
3. **Auto-Speakerphone Boost (Optional)**: Toggle **Auto Speakerphone** in the app drawer to automatically route call audio through the speaker during recording, guaranteeing loud, clear two-sided audio on any phone.

## How to Build the APK on GitHub
1. Upload all files (including `.github/workflows/build-apk.yml`) to your GitHub repository.
2. Open the **Actions** tab -> **Build ZA Call Recorder APK**.
3. Download **`ZA-Call-Recorder-debug-apk`** from **Artifacts**, unzip, and install `app-debug.apk`.
