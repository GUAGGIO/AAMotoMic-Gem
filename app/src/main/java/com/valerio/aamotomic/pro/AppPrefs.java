package com.valerio.aamotomic.pro;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Clean SharedPreferences wrapper replacing the 6 separate .txt files of the B4A version.
 * Also fixes the 5000ms (Battery Saver) interval cap bug from v9.0-beta.
 */
public final class AppPrefs {
    private static final String PREFS_NAME = "aamotomic_pro_prefs";

    private static final String KEY_TARGET_MAC = "target_mac";
    private static final String KEY_TARGET_NAME = "target_name";
    private static final String KEY_CHECK_INTERVAL_INDEX = "check_interval_idx";
    private static final String KEY_RELEASE_DELAY_INDEX = "release_delay_idx";
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_AGGRESSIVE_MODE = "aggressive_mode";
    private static final String KEY_GEMINI_LIVE_MODE = "gemini_live_mode";
    private static final String KEY_DIAG_LOGGING = "diag_logging";
    private static final String KEY_LANGUAGE = "app_language"; // "en" (default) or "it"

    private AppPrefs() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getTargetMac(Context ctx) {
        return prefs(ctx).getString(KEY_TARGET_MAC, "").trim().toUpperCase();
    }

    public static void setTargetMac(Context ctx, String mac) {
        prefs(ctx).edit().putString(KEY_TARGET_MAC, mac == null ? "" : mac.trim().toUpperCase()).apply();
    }

    public static String getTargetName(Context ctx) {
        return prefs(ctx).getString(KEY_TARGET_NAME, "");
    }

    public static void setTargetName(Context ctx, String name) {
        prefs(ctx).edit().putString(KEY_TARGET_NAME, name == null ? "" : name.trim()).apply();
    }

    public static int getCheckIntervalIndex(Context ctx) {
        return prefs(ctx).getInt(KEY_CHECK_INTERVAL_INDEX, 1); // Default: 1000ms + Event-Driven
    }

    public static void setCheckIntervalIndex(Context ctx, int idx) {
        prefs(ctx).edit().putInt(KEY_CHECK_INTERVAL_INDEX, idx).apply();
    }

    /**
     * Returns idle watchdog polling interval in milliseconds.
     * Note: Primary detection in Pro is Event-Driven (0ms via AudioRecordingCallback / AudioPlaybackCallback).
     * Fixes the v9.0 bug where index 3 (5000ms) was capped to 2000ms.
     */
    public static long getCheckIntervalMs(Context ctx) {
        int idx = getCheckIntervalIndex(ctx);
        switch (idx) {
            case 0: return 500L;
            case 1: return 1000L;
            case 2: return 2000L;
            case 3: return 5000L; // Fixed! Was capped at 2000ms in original v9.0
            default: return 1000L;
        }
    }

    public static int getReleaseDelayIndex(Context ctx) {
        return prefs(ctx).getInt(KEY_RELEASE_DELAY_INDEX, 2); // Default: 5.0s
    }

    public static void setReleaseDelayIndex(Context ctx, int idx) {
        prefs(ctx).edit().putInt(KEY_RELEASE_DELAY_INDEX, idx).apply();
    }

    public static long getReleaseDelayMs(Context ctx) {
        int idx = getReleaseDelayIndex(ctx);
        switch (idx) {
            case 0: return 1500L;
            case 1: return 3000L;
            case 2: return 5000L;
            case 3: return 10000L;
            default: return 5000L;
        }
    }

    public static boolean isAutoStartEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_START, true);
    }

    public static void setAutoStartEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_START, enabled).apply();
    }

    public static boolean isAggressiveMode(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AGGRESSIVE_MODE, false);
    }

    public static void setAggressiveMode(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_AGGRESSIVE_MODE, enabled).apply();
    }

    /**
     * New in Pro: Extended multi-turn & Gemini Live mode.
     * Prevents the 30-second music shield timeout from cutting off continuous Gemini Live conversations.
     */
    public static boolean isGeminiLiveMode(Context ctx) {
        return prefs(ctx).getBoolean(KEY_GEMINI_LIVE_MODE, true);
    }

    public static void setGeminiLiveMode(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_GEMINI_LIVE_MODE, enabled).apply();
    }

    public static boolean isDiagLoggingEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_DIAG_LOGGING, true);
    }

    public static void setDiagLoggingEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_DIAG_LOGGING, enabled).apply();
    }

    /**
     * App language: "en" by default, "it" selectable.
     */
    public static String getLanguage(Context ctx) {
        return prefs(ctx).getString(KEY_LANGUAGE, "en");
    }

    public static void setLanguage(Context ctx, String lang) {
        prefs(ctx).edit().putString(KEY_LANGUAGE, lang == null ? "en" : lang).apply();
    }

    public static boolean isItalian(Context ctx) {
        return "it".equalsIgnoreCase(getLanguage(ctx));
    }
}
