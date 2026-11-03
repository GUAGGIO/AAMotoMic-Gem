package com.valerio.aamotomic.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioRecordingConfiguration;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;

import java.util.List;

/**
 * Event-Driven + Watchdog Foreground Service for routing Android Auto voice commands
 * to the motorcycle Bluetooth intercom (Cardo, Sena, UClear, etc.).
 *
 * Specific fixes in v10.1-Pro (POCO X7 Pro / HyperOS optimization):
 * 1. Fixed music interruption loop: USAGE_MEDIA is completely separated from voice triggers.
 * 2. Proper release cleanup: clearCommunicationDevice() is always invoked on Android 12+,
 *    restoring full-fidelity A2DP stereo when speech ends instead of staying trapped in mono HFP/SCO.
 * 3. MediaTek / Snapdragon dual-routing fix: on API 31+, uses setCommunicationDevice() exclusively,
 *    preventing legacy startBluetoothSco() race conditions on Dimensity 8300 / HyperOS.
 * 4. Advanced Hotword filter: filters out AudioSource.HOTWORD (1999) and silenced clients (isClientSilenced),
 *    preventing background keyword detectors from falsely activating voice mode.
 * 5. Smart Navigation Filter: ignores USAGE_ASSISTANCE_NAVIGATION_GUIDANCE (12) when mic is idle.
 */
public class AudioRouterService extends Service {
    private static final String CHANNEL_ID = "aamotomic_pro_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_PREARM_AA_MIC = "com.valerio.aamotomic.pro.ACTION_PREARM_AA_MIC";
    public static final String ACTION_INTERRUPT_SPEECH = "com.valerio.aamotomic.pro.ACTION_INTERRUPT_SPEECH";

    public static volatile boolean isRunning = false;
    public static volatile boolean isSessionOpen = false;
    public static volatile boolean isScoForced = false;
    public static volatile boolean isSpeakingNow = false;
    public static volatile String activeDeviceSummary = "In attesa...";

    public static boolean isAssistantCurrentlySpeaking() {
        return isSessionOpen && isSpeakingNow;
    }

    private AudioManager audioManager;
    private Handler mainHandler;
    private PowerManager.WakeLock cpuWakeLock;
    private AudioTrack scoKeepAliveTrack;

    private String targetMac = "";
    private long idleIntervalMs = 1000L;
    private long releaseDelayMs = 5000L;

    private int lastMode = -1;
    private boolean lastRecState = false;
    private boolean lastSpeakState = false;
    private boolean lastScoState = false;
    private int lastRecordingUid = -1;

    private long lastActiveTimestamp = 0L;
    private long cooldownUntilTimestamp = 0L;
    private long lastSpeechDetectedTimestamp = 0L;
    private long lastLogThrottleTimestamp = 0L;
    private long lastForceScoTimestamp = 0L;
    private boolean wasMusicActiveOnOpen = false;
    private boolean hasAssistantSpokenInSession = false;
    private boolean scoConnectedInSession = false;
    private boolean speechInterruptedByUser = false;
    private long speechInterruptedTimestamp = 0L;
    private boolean releaseInProgress = false;

    private final Runnable releaseStep1Runnable = new Runnable() {
        @Override
        public void run() {
            if (!releaseInProgress || isSessionOpen) return;
            try {
                audioManager.stopBluetoothSco();
                audioManager.setBluetoothScoOn(false);
            } catch (Exception ignored) {
            }
            mainHandler.postDelayed(releaseStep2Runnable, 100L);
        }
    };

    private final Runnable releaseStep2Runnable = new Runnable() {
        @Override
        public void run() {
            if (!releaseInProgress || isSessionOpen) return;
            try {
                if (audioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
                    audioManager.setMode(AudioManager.MODE_NORMAL);
                }
            } catch (Exception ignored) {
            }
            finishReleaseState();
        }
    };

    private int aggressiveReassertTick = 0;
    private int aggressiveSessionReasserts = 0;

    private AudioManager.AudioRecordingCallback recordingCallback;
    private AudioManager.AudioPlaybackCallback playbackCallback;
    private Object commDeviceListener; // AudioManager.OnCommunicationDeviceChangedListener (API 31+)
    private Object modeChangedListener; // AudioManager.OnModeChangedListener (API 31+)

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            evaluateAudioState("watchdog");
            long nextDelay = computeNextTickMs();
            mainHandler.postDelayed(this, nextDelay);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DiagLogger.ensureServerStarted(this);
        mainHandler = new Handler(Looper.getMainLooper());
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            // CPU partial wake lock ensures reliable background routing without turning screen ON in pocket
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AAMotoMicPro::VoiceSessionLock");
            cpuWakeLock.setReferenceCounted(false);
        }
        createNotificationChannel();
        registerNativeAudioCallbacks();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        targetMac = AppPrefs.getTargetMac(this);
        idleIntervalMs = AppPrefs.getCheckIntervalMs(this);
        releaseDelayMs = AppPrefs.getReleaseDelayMs(this);

        Notification notification = buildNotification("Monitoraggio attivo (" +
                (targetMac.isEmpty() ? "Nessun MAC" : targetMac) + ")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (targetMac.isEmpty()) {
            DiagLogger.log(this, "[SERVICE] Avvio annullato: nessun MAC configurato.");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_PREARM_AA_MIC.equals(action)) {
                DiagLogger.log(this, "[AA-BRIDGE] Pre-arm microfono Bluetooth SCO per Android Auto!");
                isSessionOpen = true;
                lastActiveTimestamp = System.currentTimeMillis();
                speechInterruptedByUser = false;
                acquireWakeLock();
                forceScoToTarget();
                mainHandler.removeCallbacks(watchdogRunnable);
                mainHandler.post(watchdogRunnable);
                return START_STICKY;
            } else if (ACTION_INTERRUPT_SPEECH.equals(action)) {
                DiagLogger.log(this, "[AA-BRIDGE] Interruzione risposta Assistente richiesta da pulsante!");
                speechInterruptedByUser = true;
                speechInterruptedTimestamp = System.currentTimeMillis();
                interruptAssistantSpeech();
                releaseScoSession("Risposta interrotta da pulsante interfono (AA Bridge)");
                return START_STICKY;
            }
        }

        isRunning = true;
        DiagLogger.log(this, "=== AAMotoMic Gem Service Started ===");
        DiagLogger.log(this, "Device: " + Build.MANUFACTURER + " " + Build.MODEL
                + " | Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        DiagLogger.log(this, "Config: target=" + targetMac
                + " | watchdog=" + idleIntervalMs + "ms"
                + " | releaseDelay=" + releaseDelayMs + "ms"
                + " | aggressive=" + AppPrefs.isAggressiveMode(this)
                + " | geminiLive=" + AppPrefs.isGeminiLiveMode(this));
        logAvailableDevices();

        mainHandler.removeCallbacks(watchdogRunnable);
        mainHandler.post(watchdogRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        mainHandler.removeCallbacksAndMessages(null);
        unregisterNativeAudioCallbacks();
        releaseWakeLock();
        performFullAudioCleanup();
        isSessionOpen = false;
        isScoForced = false;
        activeDeviceSummary = "Servizio fermato";
        DiagLogger.log(this, "[SERVICE] Servizio arrestato e risorse audio rilasciate.");
        super.onDestroy();
    }

    private long computeNextTickMs() {
        if (isSessionOpen || isScoForced) {
            return AppPrefs.isAggressiveMode(this) ? 150L : 300L;
        }
        return AppPrefs.getCheckIntervalMs(this);
    }

    /**
     * Registers native callbacks so microphone routing reacts in ~0ms
     * instead of waiting for the polling timer tick.
     */
    private void registerNativeAudioCallbacks() {
        if (audioManager == null) return;

        recordingCallback = new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                mainHandler.post(() -> evaluateAudioState("event:recordingChanged"));
            }
        };
        audioManager.registerAudioRecordingCallback(recordingCallback, mainHandler);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackCallback = new AudioManager.AudioPlaybackCallback() {
                @Override
                public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                    mainHandler.post(() -> evaluateAudioState("event:playbackChanged"));
                }
            };
            audioManager.registerAudioPlaybackCallback(playbackCallback, mainHandler);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioManager.OnCommunicationDeviceChangedListener devListener = device -> {
                String addr = (device != null) ? safeGetAddress(device) : "none";
                mainHandler.post(() -> {
                    // Protect routing ONLY while user is speaking OR Assistant is actively replying!
                    // If the Assistant is speaking (mic idle) and SCO drops after already being connected,
                    // the user pressed the intercom button to interrupt the response.
                    long now = System.currentTimeMillis();
                    if (isSessionOpen && !releaseInProgress && !speechInterruptedByUser) {
                        if (!lastRecState && hasAssistantSpokenInSession && scoConnectedInSession && !isScoOnSafe()) {
                            evaluateAudioState("event:intercomButtonInterrupt");
                            return;
                        }
                        if ((lastRecState || lastSpeakState) && !addr.equalsIgnoreCase(targetMac)
                                && (now - lastForceScoTimestamp >= 1500L)) {
                            DiagLogger.log(AudioRouterService.this,
                                    "[EVENT-OVERRIDE] Mic/Audio spostato durante voce/risposta attiva. Ri-forzo interfono.");
                            aggressiveSessionReasserts++;
                            forceScoToTarget();
                        }
                    }
                });
            };
            commDeviceListener = devListener;
            audioManager.addOnCommunicationDeviceChangedListener(getMainExecutor(), devListener);

            AudioManager.OnModeChangedListener mListener = mode ->
                    mainHandler.post(() -> evaluateAudioState("event:modeChanged=" + mode));
            modeChangedListener = mListener;
            audioManager.addOnModeChangedListener(getMainExecutor(), mListener);
        }
    }

    private void unregisterNativeAudioCallbacks() {
        if (audioManager == null) return;
        try {
            if (recordingCallback != null) {
                audioManager.unregisterAudioRecordingCallback(recordingCallback);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
                audioManager.unregisterAudioPlaybackCallback(playbackCallback);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (commDeviceListener instanceof AudioManager.OnCommunicationDeviceChangedListener) {
                    audioManager.removeOnCommunicationDeviceChangedListener(
                            (AudioManager.OnCommunicationDeviceChangedListener) commDeviceListener);
                }
                if (modeChangedListener instanceof AudioManager.OnModeChangedListener) {
                    audioManager.removeOnModeChangedListener(
                            (AudioManager.OnModeChangedListener) modeChangedListener);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Core evaluation logic determining whether a voice command or call is active.
     */
    private void evaluateAudioState(String triggerSource) {
        if (!isRunning || audioManager == null) return;

        targetMac = AppPrefs.getTargetMac(this);
        releaseDelayMs = AppPrefs.getReleaseDelayMs(this);
        long now = System.currentTimeMillis();

        int mode = audioManager.getMode();
        boolean recActive = isAudioRecordingActive();
        if (recActive && speechInterruptedByUser) {
            speechInterruptedByUser = false;
        }
        boolean speakActive = isAssistantPlaybackActive(recActive, now);
        boolean musicActive = audioManager.isMusicActive();
        boolean scoOn = isScoOnSafe();

        if (mode != lastMode) {
            DiagLogger.log(this, "[MODE] " + lastMode + " -> " + mode + " (" + triggerSource + ")");
            lastMode = mode;
        }

        if (scoOn != lastScoState) {
            DiagLogger.log(this, "[SCO] State: " + lastScoState + " -> " + scoOn);
            lastScoState = scoOn;
        }

        if (isSessionOpen && scoOn) {
            scoConnectedInSession = true;
        }

        // If the Assistant is speaking (mic idle) and SCO drops after already being connected,
        // the user pressed the physical intercom button to stop the response!
        if (isSessionOpen && !releaseInProgress && !recActive
                && hasAssistantSpokenInSession && (speakActive || lastSpeakState)
                && scoConnectedInSession && !scoOn) {
            DiagLogger.log(this, "[INTERRUPT] Pulsante interfono premuto durante risposta -> stop immediato Assistente e ripristino A2DP!");
            speechInterruptedByUser = true;
            speechInterruptedTimestamp = now;
            scoConnectedInSession = false;
            lastSpeakState = false;
            lastSpeechDetectedTimestamp = 0L;
            interruptAssistantSpeech();
            releaseScoSession("Risposta interrotta da pulsante interfono");
            return;
        }

        if (recActive != lastRecState || speakActive != lastSpeakState) {
            DiagLogger.log(this, "[STATE] trigger=" + triggerSource
                    + " mode=" + mode
                    + " rec=" + recActive
                    + " speak=" + speakActive
                    + " sco=" + scoOn
                    + " music=" + musicActive
                    + " forced=" + isScoForced
                    + " session=" + isSessionOpen);
        }

        boolean recRisingEdge = recActive && !lastRecState;
        lastRecState = recActive;
        lastSpeakState = speakActive;
        isSpeakingNow = speakActive;

        // Determine if an active voice session should be routed right now.
        // IMPORTANT: Do NOT use (mode == MODE_IN_COMMUNICATION) as a trigger here because
        // our own service sets MODE_IN_COMMUNICATION to prevent Android's 6-second idle SCO drop
        // during long Assistant answers!
        boolean activeNow = recActive || speakActive || (mode == AudioManager.MODE_IN_CALL);

        if (activeNow) {
            // Cancel any pending release steps immediately so a resumed sentence is NEVER cut off!
            if (releaseInProgress) {
                releaseInProgress = false;
                mainHandler.removeCallbacks(releaseStep1Runnable);
                mainHandler.removeCallbacks(releaseStep2Runnable);
                DiagLogger.log(this, "[RELEASE] Rilascio annullato: ripresa attività vocale/risposta.");
            }
            if (!isSessionOpen) {
                isSessionOpen = true;
                wasMusicActiveOnOpen = musicActive;
                hasAssistantSpokenInSession = false;
                scoConnectedInSession = scoOn;
                speechInterruptedByUser = false;
                acquireWakeLock();
                DiagLogger.log(this, "[SESSION] Aperta | WasMusicActive=" + wasMusicActiveOnOpen
                        + " | trigger=" + triggerSource);
                updateNotification("Sessione Vocale Attiva -> Interfono");
            }
            if (speakActive) {
                hasAssistantSpokenInSession = true;
            }
            lastActiveTimestamp = now;
            checkAndForceSco(recRisingEdge);
        } else if (isScoForced && !releaseInProgress) {
            // If Assistant hasn't spoken yet after user finished talking, allow at least 8.5s
            // for Google Assistant / Gemini server processing over mobile data!
            long effectiveWaitMs = hasAssistantSpokenInSession
                    ? releaseDelayMs
                    : Math.max(releaseDelayMs, 8500L);
            if (now - lastActiveTimestamp >= effectiveWaitMs) {
                releaseScoSession("Fine parlato: release delay (" + effectiveWaitMs + "ms) scaduto");
            }
        }

        // Safety timeout to prevent any stuck session: 300s (Gemini Live) or 45s (standard)
        if (isScoForced && !releaseInProgress) {
            long maxSessionMs = AppPrefs.isGeminiLiveMode(this) ? 300_000L : 45_000L;
            if (now - lastActiveTimestamp >= maxSessionMs && !recActive && !speakActive) {
                DiagLogger.log(this, "[SESSION] Safety timeout (" + (maxSessionMs / 1000) + "s) scaduto -> rilascio.");
                releaseScoSession("Safety timeout");
            }
        }
    }

    private void checkAndForceSco(boolean recRisingEdge) {
        String currentAddr = getCurrentCommunicationDeviceAddress();
        boolean scoOn = isScoOnSafe();
        long now = System.currentTimeMillis();

        // Keep our UID active in PlaybackActivityMonitor so AudioDeviceBroker never triggers
        // its 6000ms MSG_I_CHECK_COMMUNICATION_ROUTE_CLIENT_STATE inactivity drop!
        startScoKeepAliveTrack();

        if (currentAddr.equalsIgnoreCase(targetMac)) {
            if (!isScoForced) {
                isScoForced = true;
                activeDeviceSummary = " Connesso a " + targetMac;
                DiagLogger.log(this, ">>> SUCCESS: Microfono instradato all'interfono (" + targetMac + ")");
            }
            if (!scoOn) {
                // Re-activate if user is speaking OR Assistant is actively speaking its response,
                // with 1500ms debounce so async SCO negotiation has time to complete.
                if ((recRisingEdge || lastRecState || lastSpeakState)
                        && (now - lastForceScoTimestamp >= 1500L)) {
                    DiagLogger.log(this, "[SCO] SCO disconnesso durante voce/risposta attiva, riattivo...");
                    forceScoToTarget();
                }
            } else if (AppPrefs.isAggressiveMode(this)) {
                aggressiveReassertTick++;
                if ((recRisingEdge || aggressiveReassertTick >= 6)
                        && (now - lastForceScoTimestamp >= 1500L)) {
                    aggressiveReassertTick = 0;
                    aggressiveSessionReasserts++;
                    forceScoToTarget();
                }
            }
        } else {
            if (now - lastForceScoTimestamp >= 1500L || recRisingEdge) {
                if (now - lastLogThrottleTimestamp > 2000L) {
                    DiagLogger.log(this, "[ROUTING] Mic attuale='" + currentAddr + "', forzo target='" + targetMac + "'");
                    lastLogThrottleTimestamp = now;
                }
                forceScoToTarget();
            }
        }
    }

    /**
     * Routes the microphone and Assistant voice output to the target intercom.
     * Starts a silent AudioTrack in our own UID before calling setCommunicationDevice(),
     * preventing Android 14/15/16's AudioDeviceBroker from killing the SCO link after 6.0s
     * when Google Assistant (different UID) is speaking a long answer.
     */
    @SuppressWarnings("deprecation")
    private void forceScoToTarget() {
        lastForceScoTimestamp = System.currentTimeMillis();
        startScoKeepAliveTrack();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                List<AudioDeviceInfo> devices = audioManager.getAvailableCommunicationDevices();
                boolean found = false;
                for (AudioDeviceInfo dev : devices) {
                    int type = dev.getType();
                    if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                            || type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                        String addr = safeGetAddress(dev);
                        if (addr.equalsIgnoreCase(targetMac) || (addr.isEmpty() && devices.size() == 1)) {
                            boolean ok = audioManager.setCommunicationDevice(dev);
                            found = true;
                            if (ok) {
                                isScoForced = true;
                                activeDeviceSummary = "Attivo su " + dev.getProductName() + " (" + addr + ")";
                                DiagLogger.log(this, "[SCO] setCommunicationDevice SUCCESS: type="
                                        + type + " addr=" + addr + " name=" + dev.getProductName());
                            } else {
                                DiagLogger.log(this, "[SCO] setCommunicationDevice FAILED: type="
                                        + type + " addr=" + addr);
                            }
                            break;
                        }
                    }
                }
                if (!found) {
                    DiagLogger.log(this, "[SCO] Target " + targetMac + " non presente tra communication devices disponibili.");
                }
            } catch (Exception e) {
                DiagLogger.log(this, "[SCO] Errore setCommunicationDevice: " + e.getMessage());
            }
        } else {
            // Android 7 - 11 fallback
            try {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.startBluetoothSco();
                audioManager.setBluetoothScoOn(true);
            } catch (Exception e) {
                DiagLogger.log(this, "[SCO] startBluetoothSco legacy error: " + e.getMessage());
            }
            isScoForced = true;
            activeDeviceSummary = "SCO Legacy attivo (" + targetMac + ")";
        }
    }

    /**
     * Maintains a zero-filled silent AudioTrack under our own UID while a voice session is open.
     * In Android's AudioDeviceBroker, setCommunicationDevice() starts a 6000ms timer
     * (MSG_I_CHECK_COMMUNICATION_ROUTE_CLIENT_STATE) that checks:
     *   mAudioService.isPlaybackActiveForUid(crc.getUid()) || mAudioService.isRecordingActiveForUid(crc.getUid())
     * Because Google Assistant runs under a different UID, our UID would otherwise show no active
     * playback/recording and AudioDeviceBroker would tear down SCO every 6.0 seconds!
     * Playing digital silence with USAGE_VOICE_COMMUNICATION_SIGNALLING keeps our UID active in
     * PlaybackActivityMonitor without affecting music or audio focus.
     */
    private void startScoKeepAliveTrack() {
        if (scoKeepAliveTrack != null) {
            try {
                if (scoKeepAliveTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    return;
                }
                scoKeepAliveTrack.play();
                return;
            } catch (Exception ignored) {
                stopScoKeepAliveTrack();
            }
        }
        try {
            int sampleRate = 8000;
            int frameCount = 800; // 100ms of 16-bit mono digital silence
            byte[] silence = new byte[frameCount * 2];
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            scoKeepAliveTrack = new AudioTrack(
                    attrs,
                    format,
                    silence.length,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            scoKeepAliveTrack.write(silence, 0, silence.length);
            scoKeepAliveTrack.setLoopPoints(0, frameCount, -1);
            scoKeepAliveTrack.play();
            DiagLogger.log(this, "[KEEPALIVE] Silent AudioTrack attivo (anti-timeout 6s AudioDeviceBroker).");
        } catch (Exception e) {
            DiagLogger.log(this, "[KEEPALIVE] Errore avvio AudioTrack: " + e.getMessage());
        }
    }

    private void stopScoKeepAliveTrack() {
        if (scoKeepAliveTrack != null) {
            try {
                scoKeepAliveTrack.setLoopPoints(0, 0, 0);
                scoKeepAliveTrack.pause();
                scoKeepAliveTrack.flush();
                scoKeepAliveTrack.stop();
            } catch (Exception ignored) {
            }
            try {
                scoKeepAliveTrack.release();
            } catch (Exception ignored) {
            }
            scoKeepAliveTrack = null;
            DiagLogger.log(this, "[KEEPALIVE] Silent AudioTrack rilasciato.");
        }
    }

    /**
     * Cleanly releases the communication device routing, seamlessly restoring
     * high-definition A2DP stereo music playback without pausing or cutting audio.
     */
    @SuppressWarnings("deprecation")
    private void releaseScoSession(String reason) {
        if (releaseInProgress) return;
        releaseInProgress = true;
        isSessionOpen = false;
        isScoForced = false;

        DiagLogger.log(this, "[RELEASE] Avvio rilascio mic (" + reason + ")");
        stopScoKeepAliveTrack();
        releaseWakeLock();
        cooldownUntilTimestamp = System.currentTimeMillis() + 2500L;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice();
                DiagLogger.log(this, "[RELEASE] clearCommunicationDevice eseguito -> ripristino A2DP stereo.");
            } catch (Exception e) {
                DiagLogger.log(this, "[RELEASE] clearCommunicationDevice warning: " + e.getMessage());
            }
        }

        mainHandler.removeCallbacks(releaseStep1Runnable);
        mainHandler.removeCallbacks(releaseStep2Runnable);
        mainHandler.postDelayed(releaseStep1Runnable, 100L);
    }

    private void finishReleaseState() {
        if (AppPrefs.isAggressiveMode(this) && aggressiveSessionReasserts > 0) {
            DiagLogger.log(this, "[AGGRESSIVE] Fine sessione | re-asserts=" + aggressiveSessionReasserts);
        }
        aggressiveSessionReasserts = 0;
        aggressiveReassertTick = 0;
        isScoForced = false;
        isSessionOpen = false;
        isSpeakingNow = false;
        wasMusicActiveOnOpen = false;
        hasAssistantSpokenInSession = false;
        scoConnectedInSession = false;
        lastSpeechDetectedTimestamp = 0L;
        releaseInProgress = false;
        activeDeviceSummary = "In ascolto (" + targetMac + ")";
        updateNotification("Monitoraggio attivo (" + targetMac + ")");
        DiagLogger.log(this, "[SESSION] Sessione chiusa. Audio A2DP stereo libero.");
    }

    /**
     * Requests transient exclusive AudioFocus for 350ms and then abandons it.
     * This causes Google Assistant / Gemini (and Android Auto CAR.AUDIO.AFM) to receive
     * onAudioFocusLoss(AUDIOFOCUS_LOSS_TRANSIENT) and immediately abort speech playback,
     * while allowing any underlying music player (Spotify, YouTube Music, etc.) to resume A2DP stereo.
     */
    @SuppressWarnings("deprecation")
    private void interruptAssistantSpeech() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                AudioFocusRequest req = new AudioFocusRequest.Builder(
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(focusChange -> {})
                        .build();
                int res = audioManager.requestAudioFocus(req);
                DiagLogger.log(this, "[INTERRUPT] Transient Exclusive AudioFocus richiesto (res=" + res + ") per fermare TTS/Gemini.");
                mainHandler.postDelayed(() -> {
                    try {
                        audioManager.abandonAudioFocusRequest(req);
                        DiagLogger.log(this, "[INTERRUPT] Transient Exclusive AudioFocus rilasciato -> musica libera.");
                    } catch (Exception ignored) {
                    }
                }, 350L);
            } else {
                AudioManager.OnAudioFocusChangeListener listener = focusChange -> {};
                audioManager.requestAudioFocus(listener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
                mainHandler.postDelayed(() -> {
                    try {
                        audioManager.abandonAudioFocus(listener);
                    } catch (Exception ignored) {
                    }
                }, 350L);
            }
        } catch (Exception e) {
            DiagLogger.log(this, "[INTERRUPT] Errore interruptAssistantSpeech: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void performFullAudioCleanup() {
        stopScoKeepAliveTrack();
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice();
            }
            audioManager.stopBluetoothSco();
            audioManager.setBluetoothScoOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) {
        }
    }

    /**
     * Checks if a genuine user voice recording is active.
     * Filters out silenced clients and background keyword/hotword detectors (source 1999).
     */
    private boolean isAudioRecordingActive() {
        if (audioManager == null) return false;
        try {
            List<AudioRecordingConfiguration> configs = audioManager.getActiveRecordingConfigurations();
            if (configs != null && !configs.isEmpty()) {
                for (AudioRecordingConfiguration cfg : configs) {
                    if (cfg == null) continue;

                    // Filter out background clients silenced by the OS (Android 10+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            if (cfg.isClientSilenced()) {
                                continue;
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    int source = cfg.getClientAudioSource();
                    // AudioSource.HOTWORD = 1999 (standby detector)
                    // AudioSource.REMOTE_SUBMIX = 8 (system screen audio)
                    if (source == 1999 || source == MediaRecorder.AudioSource.REMOTE_SUBMIX) {
                        continue;
                    }

                    // Only real voice recording sources
                    if (source == MediaRecorder.AudioSource.MIC
                            || source == MediaRecorder.AudioSource.VOICE_RECOGNITION
                            || source == MediaRecorder.AudioSource.VOICE_COMMUNICATION
                            || source == MediaRecorder.AudioSource.UNPROCESSED
                            || source == MediaRecorder.AudioSource.DEFAULT) {

                        int uid = cfg.getClientAudioSessionId();
                        try {
                            Object res = cfg.getClass().getMethod("getClientUid").invoke(cfg);
                            if (res instanceof Integer) {
                                uid = (Integer) res;
                            }
                        } catch (Exception ignored) {
                        }

                        if (uid != lastRecordingUid) {
                            lastRecordingUid = uid;
                            DiagLogger.log(this, "[RECORDING] Voce attiva (source=" + source + ", uid=" + uid + ")");
                        }
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            DiagLogger.log(this, "[ERROR] isAudioRecordingActive: " + e.getMessage());
        }

        if (lastRecordingUid != -1) {
            DiagLogger.log(this, "[RECORDING] Terminato.");
            lastRecordingUid = -1;
        }
        return false;
    }

    /**
     * Checks if Google Assistant or Gemini is actively speaking back to the user.
     * Accurately detects:
     * 1. USAGE_ASSISTANT (16)
     * 2. USAGE_ASSISTANCE_ACCESSIBILITY (11 - Google Speech Services / TTS long readouts)
     * 3. USAGE_VOICE_COMMUNICATION (2 - VoIP / call / duplex assistant)
     * 4. Multi-sentence Speech Smoothing: bridges brief natural pauses between chunks (up to 2000ms)
     *    so long spoken answers are NEVER truncated mid-sentence!
     * USAGE_MEDIA (1) is always strictly excluded.
     */
    private boolean isAssistantPlaybackActive(boolean isRecordingNow, long now) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || audioManager == null) {
            return false;
        }
        boolean speechDetectedNow = false;

        try {
            List<AudioPlaybackConfiguration> configs = audioManager.getActivePlaybackConfigurations();
            if (configs != null) {
                for (AudioPlaybackConfiguration cfg : configs) {
                    if (cfg == null) continue;
                    AudioAttributes attrs = cfg.getAudioAttributes();
                    if (attrs == null) continue;
                    int usage = attrs.getUsage();
                    if (usage == AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING) continue;

                    // Assistant / Gemini Voice response (16)
                    if (usage == AudioAttributes.USAGE_ASSISTANT) {
                        speechDetectedNow = true;
                        break;
                    }

                    // Google Speech Services / Text-to-Speech (11) - used for long answers on Android Auto!
                    // Enabled whenever a voice session is active or mic was recording
                    if (usage == AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY && (isSessionOpen || isRecordingNow)) {
                        speechDetectedNow = true;
                        break;
                    }

                    // Active Voice Communication (2)
                    if (usage == AudioAttributes.USAGE_VOICE_COMMUNICATION) {
                        int mode = audioManager.getMode();
                        if (mode == AudioManager.MODE_IN_COMMUNICATION
                                || mode == AudioManager.MODE_IN_CALL
                                || isSessionOpen
                                || isRecordingNow) {
                            speechDetectedNow = true;
                            break;
                        }
                    }

                    // Navigation Guidance (12): only trigger SCO if mic is actively recording
                    if (usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) {
                        if (isRecordingNow) {
                            speechDetectedNow = true;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            DiagLogger.log(this, "[ERROR] isAssistantPlaybackActive: " + e.getMessage());
        }

        if (!speechDetectedNow) {
            if (speechInterruptedByUser) {
                speechInterruptedByUser = false;
            }
        } else if (speechInterruptedByUser) {
            if (now - speechInterruptedTimestamp < 5000L) {
                return false;
            }
            speechInterruptedByUser = false;
        }

        if (speechDetectedNow) {
            lastSpeechDetectedTimestamp = now;
            return true;
        }

        // Multi-sentence speech smoothing: keep speechActive TRUE during natural pauses
        // between sentences (up to 2500ms), preventing long answers from cutting off
        if (isSessionOpen && lastSpeechDetectedTimestamp > 0L) {
            if (now - lastSpeechDetectedTimestamp < 2500L) {
                return true;
            }
        }

        return false;
    }

    private void acquireWakeLock() {
        try {
            if (cpuWakeLock != null && !cpuWakeLock.isHeld()) {
                cpuWakeLock.acquire(60_000L);
                DiagLogger.log(this, "[WAKELOCK] CPU PartialWakeLock acquisito (schermo rimane spento).");
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
                cpuWakeLock.release();
                DiagLogger.log(this, "[WAKELOCK] CPU PartialWakeLock rilasciato.");
            }
        } catch (Exception ignored) {
        }
    }

    private String getCurrentCommunicationDeviceAddress() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioManager != null) {
            try {
                AudioDeviceInfo dev = audioManager.getCommunicationDevice();
                if (dev != null) {
                    return safeGetAddress(dev);
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public static void logAvailableDevicesStatic(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            List<AudioDeviceInfo> list = am.getAvailableCommunicationDevices();
            StringBuilder sb = new StringBuilder();
            for (AudioDeviceInfo d : list) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("type=").append(d.getType())
                        .append(",addr=").append(safeGetAddress(d))
                        .append(",name=").append(d.getProductName());
            }
            DiagLogger.log(ctx, "[DEVICES] Available: [" + sb + "]");
        } catch (Exception e) {
            DiagLogger.log(ctx, "[DEVICES] Query error: " + e.getMessage());
        }
    }

    private void logAvailableDevices() {
        logAvailableDevicesStatic(this);
    }

    private static String safeGetAddress(AudioDeviceInfo dev) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                String addr = dev.getAddress();
                return addr != null ? addr : "";
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    @SuppressWarnings("deprecation")
    private boolean isScoOnSafe() {
        try {
            return audioManager != null && audioManager.isBluetoothScoOn();
        } catch (Exception e) {
            return false;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AAMotoMic Pro Routing Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantiene attivo il routing del microfono verso l'interfono moto");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String contentText) {
        Intent openIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("AAMotoMic Pro")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && isRunning) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }
}
