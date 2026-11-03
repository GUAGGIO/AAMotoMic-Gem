package com.valerio.aamotomic.pro;

import android.content.Context;

/**
 * Localization helper for AAMotoMic Pro.
 * Default language: English ("en").
 * Selectable: Italian ("it").
 */
public final class I18n {
    private I18n() {}

    public static boolean isIt(Context ctx) {
        return AppPrefs.isItalian(ctx);
    }

    public static String title() {
        return "AAMotoMic Gem";
    }

    public static String subtitle(Context ctx) {
        return isIt(ctx)
                ? "v11.0-Gem • Ottimizzato per Gemini & Android Auto"
                : "v11.0-Gem • Optimized for Gemini & Android Auto";
    }

    public static String btnHelp(Context ctx) {
        return isIt(ctx) ? "ℹ️ GUIDA" : "ℹ️ HELP";
    }

    public static String langToggleLabel(Context ctx) {
        // Shows what language you will switch to or current flag
        return isIt(ctx) ? "🌐 EN" : "🌐 IT";
    }

    public static String statusVoiceActive(Context ctx) {
        return isIt(ctx) ? "🎙️ SESSIONE VOCALE IN CORSO" : "🎙️ VOICE SESSION ACTIVE";
    }

    public static String statusRunning(Context ctx) {
        return isIt(ctx) ? "STATUS: ATTIVO (EVENT-DRIVEN 0ms)" : "STATUS: ACTIVE (EVENT-DRIVEN 0ms)";
    }

    public static String statusIdle(Context ctx) {
        return isIt(ctx) ? "STATUS: IN ATTESA" : "STATUS: IDLE";
    }

    public static String statusIdleSub(Context ctx) {
        return isIt(ctx)
                ? "(Servizio fermo - in attesa di connessione Bluetooth o avvio manuale)"
                : "(Service idle - waiting for Bluetooth connection or manual start)";
    }

    public static String btnStart(Context ctx) {
        return isIt(ctx) ? "AVVIA SERVIZIO" : "START SERVICE";
    }

    public static String btnStop(Context ctx) {
        return isIt(ctx) ? "FERMA SERVIZIO" : "STOP SERVICE";
    }

    public static String coexBanner(Context ctx, String version) {
        return isIt(ctx)
                ? "⚠️ Coesistenza Rilevata: AAMotoMic originale (v" + version + ") è installato.\n"
                + "Le due app possono restare installate insieme, ma ricorda di tenere attivo un solo servizio alla volta."
                : "⚠️ Coexistence Detected: Original AAMotoMic (v" + version + ") is installed.\n"
                + "Both apps can coexist, but keep only one active service at a time.";
    }

    public static String btnOpenOrig(Context ctx) {
        return isIt(ctx)
                ? "APRI IMPOSTAZIONI AAMOTOMIC ORIGINALE (TERMINA)"
                : "OPEN ORIGINAL APP SETTINGS (FORCE STOP)";
    }

    public static String secTargetDevice(Context ctx) {
        return isIt(ctx) ? "DISPOSITIVO INTERFONO TARGET" : "TARGET INTERCOM DEVICE";
    }

    public static String btnScanBt(Context ctx) {
        return "SCAN BT";
    }

    public static String targetNone(Context ctx) {
        return isIt(ctx) ? "Nessun nome salvato" : "No name saved";
    }

    public static String targetSelected(Context ctx, String name) {
        return isIt(ctx) ? "Selezionato: " + name : "Selected: " + name;
    }

    public static String secProFeatures(Context ctx) {
        return isIt(ctx)
                ? "FUNZIONI PRO (MIGLIORAMENTI RISPETTO A V9.0)"
                : "PRO FEATURES & ENHANCEMENTS";
    }

    public static String chkGeminiLive(Context ctx) {
        return isIt(ctx)
                ? "Supporto Esteso Gemini Live & Multi-Turno (5 min)\nEvita il taglio forzato dopo 30s con musica attiva"
                : "Extended Gemini Live & Multi-Turn Support (5 min)\nPrevents 30s music shield timeout during continuous conversation";
    }

    public static String chkAggressive(Context ctx) {
        return isIt(ctx)
                ? "Modalità Routing Aggressivo + Listener Instant-Override\nPer telefoni Samsung OneUI / Xiaomi / OPPO"
                : "Aggressive Routing Mode + Instant-Override Listener\nFor Samsung OneUI / Xiaomi / OPPO devices";
    }

    public static String chkAutoStart(Context ctx) {
        return isIt(ctx)
                ? "Auto-Start alla connessione Bluetooth dell'interfono"
                : "Auto-Start upon target intercom Bluetooth connection";
    }

    public static String chkDiagLog(Context ctx) {
        return isIt(ctx)
                ? "Abilita Log Diagnostico (con auto-rotazione 600 righe)"
                : "Enable Diagnostic Log (with 600-line auto-rotation)";
    }

    public static String secWatchdog(Context ctx) {
        return isIt(ctx)
                ? "INTERVALLO WATCHDOG (OLTRE AI CALLBACK 0ms)"
                : "WATCHDOG INTERVAL (IN ADDITION TO 0ms CALLBACKS)";
    }

    public static String[] intervals(Context ctx) {
        if (isIt(ctx)) {
            return new String[]{
                    "500ms (Ultra Rapido + Callback 0ms)",
                    "1000ms (Default + Callback 0ms)",
                    "2000ms (Rilassato + Callback 0ms)",
                    "5000ms (Battery Saver Reale + Callback 0ms)"
            };
        } else {
            return new String[]{
                    "500ms (Ultra Fast + 0ms Callbacks)",
                    "1000ms (Default + 0ms Callbacks)",
                    "2000ms (Relaxed + 0ms Callbacks)",
                    "5000ms (Real Battery Saver + 0ms Callbacks)"
            };
        }
    }

    public static String secReleaseDelay(Context ctx) {
        return isIt(ctx)
                ? "RELEASE DELAY (TEMPO MANTENIMENTO MIC DOPO VOCE)"
                : "RELEASE DELAY (MIC RETENTION AFTER SPEECH)";
    }

    public static String[] releaseDelays(Context ctx) {
        if (isIt(ctx)) {
            return new String[]{
                    "1.5 Secondi (Rapido)",
                    "3.0 Secondi (Standard)",
                    "5.0 Secondi (Default Consigliato)",
                    "10.0 Secondi (Molto Sicuro per Cambio Brano)"
            };
        } else {
            return new String[]{
                    "1.5 Seconds (Fast)",
                    "3.0 Seconds (Standard)",
                    "5.0 Seconds (Recommended Default)",
                    "10.0 Seconds (Safe for Track Skipping)"
            };
        }
    }

    public static String btnSave(Context ctx) {
        return isIt(ctx) ? "SALVA E RIAVVIA" : "SAVE & RESTART";
    }

    public static String btnDiag(Context ctx) {
        return isIt(ctx) ? "📋 APRI LOG DIAGNOSTICO" : "📋 OPEN DIAGNOSTIC LOG";
    }

    public static String toastSaved(Context ctx) {
        return isIt(ctx) ? "Impostazioni salvate e servizio riavviato!" : "Settings saved and service restarted!";
    }

    public static String toastStarted(Context ctx) {
        return isIt(ctx) ? "Servizio AAMotoMic Pro avviato." : "AAMotoMic Pro service started.";
    }

    public static String toastStopped(Context ctx) {
        return isIt(ctx) ? "Servizio fermato." : "Service stopped.";
    }

    public static String toastBtGranted(Context ctx) {
        return isIt(ctx) ? "Concedi il permesso Bluetooth e riprova." : "Grant Bluetooth permission and try again.";
    }

    public static String toastBtDisabled(Context ctx) {
        return isIt(ctx)
                ? "Il Bluetooth è disattivato. Accendi il Bluetooth e riprova."
                : "Bluetooth is turned off. Please turn on Bluetooth and try again.";
    }

    public static String toastNoPaired(Context ctx) {
        return isIt(ctx) ? "Nessun dispositivo Bluetooth associato trovato." : "No paired Bluetooth devices found.";
    }

    public static String dialogSelectTarget(Context ctx) {
        return isIt(ctx) ? "Seleziona Interfono Target" : "Select Target Intercom";
    }

    // Diagnostic Activity Strings
    public static String diagBack(Context ctx) {
        return isIt(ctx) ? "← Indietro" : "← Back";
    }

    public static String diagTitle(Context ctx) {
        return isIt(ctx) ? "Log Diagnostico Pro" : "Diagnostic Log Pro";
    }

    public static String diagCopySafe(Context ctx) {
        return isIt(ctx) ? "🔒 Copia Sanitizzato" : "🔒 Copy Sanitized";
    }

    public static String diagCopyFull(Context ctx) {
        return isIt(ctx) ? "📄 Copia Intero" : "📄 Copy Full";
    }

    public static String diagClear(Context ctx) {
        return isIt(ctx) ? "🗑️ Svuota" : "🗑️ Clear";
    }

    public static String diagClearedToast(Context ctx) {
        return isIt(ctx) ? "Log eliminato." : "Log cleared.";
    }

    public static String diagCopiedToast(Context ctx) {
        return isIt(ctx) ? "Log copiato negli appunti!" : "Log copied to clipboard!";
    }
}
