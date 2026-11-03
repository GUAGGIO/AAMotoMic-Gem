# AAMotoMic Gem 🏍️🎙️

[![Android](https://img.shields.io/badge/Android-11%20to%2016%20(HyperOS%20%7C%20OneUI)-blue.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-11.0--Gem-success.svg)](https://github.com/moster67/AAMotoMic)
[![License](https://img.shields.io/badge/License-GPLv3%2FMIT-orange.svg)](LICENSE)

**AAMotoMic Gem** is an advanced open-source Android utility that routes Android Auto microphone input and Google Assistant / Gemini voice responses directly to your motorcycle Bluetooth helmet intercom (Cardo, Sena, Midland, FreedConn, VR1, etc.).

> **Credits:** This project is a modernized continuation and fork based on the original **[AAMotoMic](https://github.com/moster67/AAMotoMic)** developed by **[@moster67](https://github.com/moster67)** / *tillekesoft*. Huge thanks to the original author for pioneering motorcycle Bluetooth SCO routing!

---

## 🚀 Why AAMotoMic Gem?

When using **Android Auto** on a motorcycle with an aftermarket display or head unit (Carpuride, Ottocast, Podofo, TF807, etc.), Android Auto attempts to capture voice commands through the head unit's external microphone. On a motorcycle, that microphone is either non-existent or heavily buffeted by high-speed wind, causing Google Assistant or Gemini to fail completely.

The original *AAMotoMic* solved this by forcing Bluetooth SCO to the helmet intercom. However, on modern Android versions (Android 13, 14, 15, and 16 / HyperOS / OneUI), aggressive system power savers and Google's internal audio manager introduced fatal bugs that caused frequent cut-offs.

**AAMotoMic Gem** fixes these issues completely.

---

## ✨ Key Enhancements in v11.0-Gem

### 1. ⏱️ 6-Second Gemini / Assistant Cut-Off Fix (Audio Keep-Alive)
* **The Android Bug:** In Android 14/15/16, the system `AudioDeviceBroker` monitors audio playback. If Google Assistant speaks an answer longer than 6 seconds, the system notices no active audio from the routing app's UID and forcibly tears down the Bluetooth SCO channel (`MSG_I_CHECK_COMMUNICATION_ROUTE_CLIENT_STATE`).
* **The Gem Fix:** Maintains a zero-filled digital silence track (`USAGE_VOICE_COMMUNICATION_SIGNALLING`) under our UID while speech is active. Assistant responses now play continuously and cleanly, whether they last 10 seconds or 3 minutes.

### 2. 🛑 Instant Helmet Button Speech Interruption
* If Gemini or Google Assistant is giving a lengthy response and you want to stop it, simply **tap the multi-function button on your helmet intercom**.
* The app detects the button press, immediately interrupts the assistant speech, terminates the voice session, and restores full-fidelity A2DP stereo music in ~100ms.

### 3. ⚡ Event-Driven 0ms Latency (No Polling Lag)
* Uses native Android `AudioRecordingCallback`, `AudioPlaybackCallback`, and `OnCommunicationDeviceChangedListener`.
* Microsecond-fast reaction time when voice sessions open or close, eliminating delays and battery drain from constant background polling.

### 4. 🌐 Native Bilingual UI (English Default + Italian)
* Designed for the international riding community.
* Full English interface by default with an instant `🌐 IT` / `🌐 EN` language switch toggle right in the header bar.

### 5. 🗺️ Clean Navigation Prompts
* Normal Android Auto turn-by-turn guidance (Google Maps / Waze) ducks your music smoothly over standard stereo A2DP channels without forcing telephone audio or causing headset beeps.

### 6. 📋 Integrated Diagnostic Logger with Privacy Sanitizer
* Real-time 600-line auto-rotating log viewer built into the app.
* Includes a 1-tap **"Copy Sanitized"** feature that automatically hides MAC addresses and device names so riders can safely share logs for troubleshooting.

---

## 📱 Compatibility

* **OS:** Android 11, 12, 13, 14, 15, and 16.
* **Tested Skins:** Xiaomi HyperOS, Samsung OneUI, Google Pixel, Motorola MyUX.
* **Tested Voice Engines:** Google Gemini Live, Google Assistant.
* **Supported Head Units:** TF807, Carpuride, Ottocast, Podofo, Coral Vision, and all Linux/Android motorcycle displays.
* **Supported Intercoms:** Cardo Packtalk, Sena 50S/30K/20S, Midland, FreedConn, VR1, and generic Bluetooth 5.x HFP/SCO intercoms.

---

## 🛠️ Quick Setup Guide

1. Download and install **`AAMotoMic-Gem.apk`**.
2. Grant Bluetooth and Audio permissions when prompted.
3. Tap **`SCAN BT`** and choose your helmet intercom from the list of paired devices.
4. Tap **`SAVE & RESTART`**.
5. Enable **`Auto-Start upon target intercom Bluetooth connection`**.
6. Connect to your motorcycle display, ride, and enjoy crystal-clear helmet voice commands!

---

## 📦 Building from Source

Requirements:
* Android SDK (API 35 / Build Tools 35.0.0)
* JDK 17+ or 21
* PowerShell or Gradle

To build and sign the release APK:
```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```
The output APK is generated at `D:\Valerio\PROGETTI\AAMotoMic\AAMotoMic-Gem.apk`.

---

## 📜 License & Credits

* Forked from [moster67/AAMotoMic](https://github.com/moster67/AAMotoMic).
* Licensed under GPLv3 / MIT.
* Developed and optimized by **Valerio** with Google DeepMind Antigravity.
