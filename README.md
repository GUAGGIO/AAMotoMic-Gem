# AAMotoMic Gem 🏍️🎙️

[![Android](https://img.shields.io/badge/Android-11%20to%2016%20(HyperOS%20%7C%20OneUI%20%7C%20Pixel)-blue.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-11.0--Gem-success.svg)](https://github.com/GUAGGIO/AAMotoMic-Gem/releases)
[![License](https://img.shields.io/badge/License-GPLv3%2FMIT-orange.svg)](LICENSE)

**AAMotoMic Gem** is an advanced open-source Android utility that seamlessly routes Android Auto microphone input and Google Assistant / Gemini voice responses directly to your motorcycle Bluetooth helmet intercom (Cardo, Sena, Midland, FreedConn, VR1, etc.).

> **Credits & Heritage:** This project is a modernized continuation and fork based on the original **[AAMotoMic](https://github.com/moster67/AAMotoMic)** developed by **[@moster67](https://github.com/moster67)** / *tillekesoft*. Huge credit to the original author for pioneering motorcycle Bluetooth SCO routing!

---

## 🔍 The Deep-Dive: What Was Happening & Why We Built Gem

### 1. The Headunit Microphone Trap (TF807 / Zlink5 / Carpuride)
When connecting your Android phone to a motorcycle display (such as the TF807, Carpuride, Ottocast, or Podofo running Linux/Zlink), the display's firmware establishes an OpenCar/GAL (Google Auto Link) protocol handshake over Wi-Fi. In that handshake, the head unit advertises audio capabilities including an audio source: **`CAR.GAL.MIC.LITE`**.

Inside Android Auto (`com.google.android.projection.gearhead`), Google's code checks the vehicle capabilities:
* If the head unit advertises a microphone, Android Auto binds its internal `ICarAudioRecord` IPC service directly to the head unit's Wi-Fi network socket (`192.168.10.1`).
* When Google Assistant or Gemini activates, it reads microphone packets exclusively from that Wi-Fi socket, **completely ignoring the phone's local Android audio subsystem and your helmet intercom**.
* On a motorcycle, that screen microphone is exposed to rushing wind and engine vibrations, rendering speech recognition 100% useless at speed.

---

### 2. The 6.0-Second Android Timeout Mystery (Solved!)
The biggest issue riders faced on modern Android versions (Android 14, 15, and 16 / Xiaomi HyperOS / Samsung OneUI) was that **long Google Assistant and Gemini answers would cut off abruptly after exactly 6 seconds**.

Through low-level logcat and reverse-engineering of Android's `AudioService` and `AudioDeviceBroker`, we uncovered the root cause:
1. When an app requests communication routing via `setCommunicationDevice()` or `startBluetoothSco()`, Android's internal `AudioDeviceBroker` schedules a strict 6000ms watchdog timer (`MSG_I_CHECK_COMMUNICATION_ROUTE_CLIENT_STATE`).
2. Every 6 seconds, this timer executes:
   ```java
   mAudioService.isPlaybackActiveForUid(clientUid) || mAudioService.isRecordingActiveForUid(clientUid)
   ```
3. Because Google Assistant and Gemini execute under a separate system UID (`10175`), once you stop speaking your question, the routing app's own UID shows **no active recording and no active playback**.
4. At exactly the 6.0-second mark, `AudioDeviceBroker` flags the client as inactive and **forcibly tears down the Bluetooth SCO channel**, severing the audio stream while Gemini is still reading the answer!

#### 💡 The Gem Keep-Alive Solution:
**AAMotoMic Gem** injects an active, zero-filled digital silence `AudioTrack` (`USAGE_VOICE_COMMUNICATION_SIGNALLING`) under its own UID while a voice session is active. This satisfies `PlaybackActivityMonitor` at the kernel/framework level without consuming audio bandwidth, altering volume, or affecting music focus. As a result, **Gemini can speak uninterrupted for 30 seconds, 1 minute, or as long as needed.**

---

### 3. The "Zombie Release" Callback Race Condition
In previous implementations, natural speech pauses between sentences (which often last 15ms to 80ms in synthesized AI voices) triggered an asynchronous release callback. 

By the time the callback fired (~150ms later), Gemini had already begun sentence #2, but the delayed callback executed anyway and severed the audio channel mid-sentence.

**AAMotoMic Gem** implements:
* Immediate cancellation of all scheduled release `Runnable`s (`removeCallbacks`) upon playback reactivation.
* A 2.5-second continuity smoothing filter that bridges sentence transitions.
* An intelligent 8.5-second post-utterance wait window allowing Gemini to process complex queries over cellular networks before considering the interaction finished.

---

### 4. Instant Helmet Button Speech Interruption
If Gemini is reading a lengthy response (e.g., a long Wikipedia summary or news article) and you want to silence it immediately:
* Simply **press the multi-function / phone button on your helmet intercom**.
* The app detects the intercom button interrupt event, immediately terminates the speech response, cancels the voice session, and restores full-fidelity A2DP stereo music in ~100ms.

---

### 5. Event-Driven 0ms Architecture (No Battery-Draining Polling)
* Completely eliminates polling latency by registering native Android listeners:
  * `AudioManager.AudioRecordingCallback`
  * `AudioManager.AudioPlaybackCallback`
  * `AudioManager.OnCommunicationDeviceChangedListener`
* Reacts at the hardware interrupt level (0ms latency) instead of waiting for a timer tick.

---

### 6. Full Bilingual Internationalization (EN / IT)
* English by default for the global riding community.
* Instant 1-tap `🌐 IT` / `🌐 EN` language switch directly in the header bar.
* Responsive button layout preventing text clipping on modern high-DPI displays.
* Integrated privacy-safe 600-line Diagnostic Logger with 1-tap automatic sanitization (anonymizes MAC addresses and device names).

---

## 📱 Hardware & Software Compatibility

### 🔬 Verified & Tested Hardware (Our Testbed)
Every feature, timeout fix, and audio behavior in **v11.0-Gem** was specifically tested and verified on:
* **Phone:** Xiaomi POCO X7 Pro (`Xiaomi 2412DPC0AG`)
* **OS:** Xiaomi HyperOS (Android 15 / MediaTek Dimensity 8300-Ultra)
* **Helmet Intercom:** VR1 Bluetooth Helmet Intercom (`4E:F4:81:8E:E7:05`)
* **Motorcycle Display:** TF807 Linux Motorcycle Display (`1600x600`, Zlink5 v1.0.1, OpenCar GAL 1.4)
* **Voice Engines:** Google Gemini & Google Assistant via Android Auto (`com.google.android.projection.gearhead`)

### 🌐 Expected Compatibility (Standard Android Bluetooth Architecture)
Because AAMotoMic Gem relies on standard Android Bluetooth HFP/SCO and AudioManager APIs (API 24 to 35):
* **Intercoms:** Any Bluetooth headset or helmet intercom supporting standard Bluetooth HFP/SCO (Cardo, Sena, Midland, FreedConn, etc.).
* **Head Units:** Any aftermarket Linux/Android motorcycle display running Android Auto (Carpuride, Ottocast, Podofo, etc.) or wired/wireless motorcycle headunits.
* **Smartphones:** Modern Android devices running Android 11 through 16. *(Note: Feedback and test logs from riders with Samsung OneUI, Google Pixel, or other devices are welcome!)*

---

## 🛠️ Quick Start Guide

1. Download [`AAMotoMic-Gem.apk`](https://github.com/GUAGGIO/AAMotoMic-Gem/releases/latest) from the [Releases page](https://github.com/GUAGGIO/AAMotoMic-Gem/releases).
2. Install the APK on your phone and open **AAMotoMic Gem**.
3. Tap **`SCAN BT`** and select your paired helmet intercom.
4. Tap **`SAVE & RESTART`**.
5. Enable **`Auto-Start upon target intercom Bluetooth connection`**.
6. Mount your phone, connect to your motorcycle screen, and talk to Gemini hands-free!

---

## 🏗️ Building from Source

This project is structured as a standard Android Studio / Gradle project with an included standalone build script.

Requirements:
* Android SDK (API 35, Build Tools 35.0.0)
* JDK 17+ or 21
* PowerShell or Gradle

To compile and sign the release APK locally:
```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```
The output APK is generated at `AAMotoMic-Gem.apk`.

---

## 📜 License & Acknowledgments

* Based on the original [AAMotoMic](https://github.com/moster67/AAMotoMic) by **@moster67** (*tillekesoft*).
* Licensed under **GPLv3 / MIT**.
* Developed, reverse-engineered, and optimized by **Valerio** with Google DeepMind Antigravity.
