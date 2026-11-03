package com.valerio.aamotomic.pro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Main Activity for AAMotoMic Pro (v10.0-Pro).
 * Coexists side-by-side with the original com.tillekesoft.aamotomic package
 * and includes a built-in coexistence detector & shortcut.
 */
public class MainActivity extends Activity {
    private static final String ORIGINAL_PKG = "com.tillekesoft.aamotomic";
    private static final int REQ_PERMISSIONS = 101;
    private static final Pattern MAC_REGEX =
            Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");

    private LinearLayout statusCard;
    private TextView txtStatusTitle;
    private TextView txtStatusSubtitle;
    private LinearLayout coexistenceCard;
    private TextView txtCoexistenceInfo;

    private EditText edtMac;
    private TextView txtTargetName;
    private Spinner spnInterval;
    private Spinner spnTimeout;

    private CheckBox chkGeminiLive;
    private CheckBox chkAggressive;
    private CheckBox chkAutoStart;
    private CheckBox chkDiagLog;

    private Button btnStartStop;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable uiRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshStatusUi();
            uiHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagLogger.ensureServerStarted(this);
        buildUi();
        requestRequiredPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCoexistenceBanner();
        refreshStatusUi();
        uiHandler.post(uiRefreshRunnable);
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(uiRefreshRunnable);
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0D1117"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, dp(20), pad, dp(28));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(headerRow);

        LinearLayout headerTextCol = new LinearLayout(this);
        headerTextCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(headerTextCol, colLp);

        TextView title = new TextView(this);
        title.setText(I18n.title());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        headerTextCol.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(I18n.subtitle(this));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setTextColor(Color.parseColor("#8B949E"));
        headerTextCol.addView(subtitle);

        // Language toggle button: default EN, tap to switch to IT and vice-versa
        Button btnLang = makeButton(I18n.langToggleLabel(this), "#21262D", Color.parseColor("#3FB950"), 12);
        btnLang.setPadding(dp(8), dp(6), dp(8), dp(6));
        btnLang.setOnClickListener(v -> {
            String cur = AppPrefs.getLanguage(MainActivity.this);
            AppPrefs.setLanguage(MainActivity.this, "it".equalsIgnoreCase(cur) ? "en" : "it");
            saveSettingsFromUi();
            recreate();
        });
        LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        langLp.rightMargin = dp(6);
        headerRow.addView(btnLang, langLp);

        Button btnInfo = makeButton(I18n.btnHelp(this), "#21262D", Color.parseColor("#58A6FF"), 12);
        btnInfo.setPadding(dp(8), dp(6), dp(8), dp(6));
        btnInfo.setOnClickListener(v -> showHowItWorksDialog());
        headerRow.addView(btnInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(14));
        View headerSep = new View(this);
        root.addView(headerSep, sepLp);

        // Status Card
        statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusCard.setBackground(roundedRect("#161B22", "#30363D", 14));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.bottomMargin = dp(12);
        root.addView(statusCard, statusLp);

        txtStatusTitle = new TextView(this);
        txtStatusTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        txtStatusTitle.setTypeface(Typeface.DEFAULT_BOLD);
        txtStatusTitle.setGravity(Gravity.CENTER);
        statusCard.addView(txtStatusTitle);

        txtStatusSubtitle = new TextView(this);
        txtStatusSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        txtStatusSubtitle.setTextColor(Color.parseColor("#C9D1D9"));
        txtStatusSubtitle.setGravity(Gravity.CENTER);
        statusCard.addView(txtStatusSubtitle);

        // Coexistence Card (shown if original com.tillekesoft.aamotomic is installed)
        coexistenceCard = new LinearLayout(this);
        coexistenceCard.setOrientation(LinearLayout.VERTICAL);
        coexistenceCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        coexistenceCard.setBackground(roundedRect("#2A1E0F", "#D29922", 12));
        LinearLayout.LayoutParams coexLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        coexLp.bottomMargin = dp(14);
        root.addView(coexistenceCard, coexLp);

        txtCoexistenceInfo = new TextView(this);
        txtCoexistenceInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        txtCoexistenceInfo.setTextColor(Color.parseColor("#F0C674"));
        coexistenceCard.addView(txtCoexistenceInfo);

        Button btnOpenOriginalSettings = makeButton(
                I18n.btnOpenOrig(this),
                "#8A5700", Color.WHITE, 12);
        LinearLayout.LayoutParams btnOrigLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        btnOrigLp.topMargin = dp(8);
        btnOpenOriginalSettings.setOnClickListener(v -> openOriginalAppInfo());
        coexistenceCard.addView(btnOpenOriginalSettings, btnOrigLp);

        // Target Device Section
        LinearLayout targetRow = new LinearLayout(this);
        targetRow.setOrientation(LinearLayout.HORIZONTAL);
        targetRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(targetRow);

        TextView lblTarget = makeSectionLabel(I18n.secTargetDevice(this));
        LinearLayout.LayoutParams lblTargetLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        targetRow.addView(lblTarget, lblTargetLp);

        Button btnScan = makeButton(I18n.btnScanBt(this), "#1F6FEB", Color.WHITE, 13);
        btnScan.setOnClickListener(v -> scanPairedDevices());
        targetRow.addView(btnScan, new LinearLayout.LayoutParams(dp(110), dp(36)));

        txtTargetName = new TextView(this);
        txtTargetName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        txtTargetName.setTextColor(Color.parseColor("#58A6FF"));
        String savedName = AppPrefs.getTargetName(this);
        txtTargetName.setText(savedName.isEmpty() ? I18n.targetNone(this) : I18n.targetSelected(this, savedName));
        root.addView(txtTargetName);

        edtMac = new EditText(this);
        edtMac.setHint("XX:XX:XX:XX:XX:XX");
        edtMac.setHintTextColor(Color.parseColor("#6E7681"));
        edtMac.setTextColor(Color.WHITE);
        edtMac.setSingleLine(true);
        edtMac.setText(AppPrefs.getTargetMac(this));
        edtMac.setPadding(dp(14), dp(10), dp(14), dp(10));
        edtMac.setBackground(roundedRect("#161B22", "#30363D", 10));
        LinearLayout.LayoutParams macLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        macLp.topMargin = dp(6);
        macLp.bottomMargin = dp(14);
        root.addView(edtMac, macLp);

        // Pro Improvements Panel
        TextView lblProFeatures = makeSectionLabel(I18n.secProFeatures(this));
        root.addView(lblProFeatures);

        LinearLayout proPanel = new LinearLayout(this);
        proPanel.setOrientation(LinearLayout.VERTICAL);
        proPanel.setPadding(dp(12), dp(10), dp(12), dp(10));
        proPanel.setBackground(roundedRect("#161B22", "#30363D", 12));
        LinearLayout.LayoutParams proLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        proLp.topMargin = dp(6);
        proLp.bottomMargin = dp(14);
        root.addView(proPanel, proLp);

        chkGeminiLive = makeCheckBox(
                I18n.chkGeminiLive(this),
                AppPrefs.isGeminiLiveMode(this));
        proPanel.addView(chkGeminiLive);

        chkAggressive = makeCheckBox(
                I18n.chkAggressive(this),
                AppPrefs.isAggressiveMode(this));
        proPanel.addView(chkAggressive);

        chkAutoStart = makeCheckBox(
                I18n.chkAutoStart(this),
                AppPrefs.isAutoStartEnabled(this));
        proPanel.addView(chkAutoStart);

        chkDiagLog = makeCheckBox(
                I18n.chkDiagLog(this),
                AppPrefs.isDiagLoggingEnabled(this));
        proPanel.addView(chkDiagLog);

        // Check Interval Spinner
        root.addView(makeSectionLabel(I18n.secWatchdog(this)));
        spnInterval = new Spinner(this);
        spnInterval.setAdapter(makeSpinnerAdapter(I18n.intervals(this)));
        spnInterval.setSelection(AppPrefs.getCheckIntervalIndex(this));
        spnInterval.setBackground(roundedRect("#161B22", "#30363D", 10));
        LinearLayout.LayoutParams spn1Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        spn1Lp.topMargin = dp(6);
        spn1Lp.bottomMargin = dp(12);
        root.addView(spnInterval, spn1Lp);

        // Release Delay Spinner
        root.addView(makeSectionLabel(I18n.secReleaseDelay(this)));
        spnTimeout = new Spinner(this);
        spnTimeout.setAdapter(makeSpinnerAdapter(I18n.releaseDelays(this)));
        spnTimeout.setSelection(AppPrefs.getReleaseDelayIndex(this));
        spnTimeout.setBackground(roundedRect("#161B22", "#30363D", 10));
        LinearLayout.LayoutParams spn2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        spn2Lp.topMargin = dp(6);
        spn2Lp.bottomMargin = dp(18);
        root.addView(spnTimeout, spn2Lp);

        // Save & Start/Stop Buttons Row
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(actionRow);

        Button btnSave = makeButton(I18n.btnSave(this), "#238636", Color.WHITE, 14);
        btnSave.setOnClickListener(v -> saveAndRestartService());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        saveLp.rightMargin = dp(8);
        actionRow.addView(btnSave, saveLp);

        btnStartStop = makeButton(I18n.btnStart(this), "#1F6FEB", Color.WHITE, 14);
        btnStartStop.setOnClickListener(v -> toggleService());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        actionRow.addView(btnStartStop, stopLp);

        // Diagnostic Log Viewer Button
        Button btnDiag = makeButton(I18n.btnDiag(this), "#21262D", Color.WHITE, 14);
        btnDiag.setMinHeight(dp(48));
        btnDiag.setOnClickListener(v -> startActivity(new Intent(this, DiagLogActivity.class)));
        LinearLayout.LayoutParams diagLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        diagLp.topMargin = dp(12);
        root.addView(btnDiag, diagLp);

        setContentView(scroll);
    }

    private void refreshCoexistenceBanner() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(ORIGINAL_PKG, 0);
            coexistenceCard.setVisibility(View.VISIBLE);
            txtCoexistenceInfo.setText(I18n.coexBanner(this, info.versionName));
        } catch (PackageManager.NameNotFoundException e) {
            coexistenceCard.setVisibility(View.GONE);
        }
    }

    private void openOriginalAppInfo() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + ORIGINAL_PKG));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open settings.", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshStatusUi() {
        if (AudioRouterService.isRunning) {
            if (AudioRouterService.isSessionOpen) {
                statusCard.setBackground(roundedRect("#0F2D1E", "#3FB950", 14));
                txtStatusTitle.setText(I18n.statusVoiceActive(this));
                txtStatusTitle.setTextColor(Color.parseColor("#3FB950"));
            } else {
                statusCard.setBackground(roundedRect("#0D233A", "#388BFD", 14));
                txtStatusTitle.setText(I18n.statusRunning(this));
                txtStatusTitle.setTextColor(Color.parseColor("#58A6FF"));
            }
            txtStatusSubtitle.setText(AudioRouterService.activeDeviceSummary);
            btnStartStop.setText(I18n.btnStop(this));
            btnStartStop.setBackground(roundedRect("#DA3633", "#F85149", 12));
        } else {
            statusCard.setBackground(roundedRect("#161B22", "#30363D", 14));
            txtStatusTitle.setText(I18n.statusIdle(this));
            txtStatusTitle.setTextColor(Color.parseColor("#8B949E"));
            txtStatusSubtitle.setText(I18n.statusIdleSub(this));
            btnStartStop.setText(I18n.btnStart(this));
            btnStartStop.setBackground(roundedRect("#1F6FEB", "#388BFD", 12));
        }
    }

    private void showHowItWorksDialog() {
        ScrollView sv = new ScrollView(this);
        sv.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);

        TextView tv = new TextView(this);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Color.parseColor("#E6EDF3"));
        tv.setLineSpacing(dp(3), 1.15f);

        String text = 
            "🇮🇹 COME FUNZIONA L'APP (Italiano)\n\n"
            + "1. SCOPO & PROBLEMA RISOLTO:\n"
            + "Quando usi Android Auto in moto con un monitor esterno (es. TF807), Android Auto tenta di usare il microfono del monitor (che cattura il vento ed è pessimo). Questa app instrada automaticamente l'audio vocale e il microfono verso il tuo interfono da casco Bluetooth (Cardo, Sena, VR1, ecc.).\n\n"
            + "2. OTTIMIZZAZIONE GEMINI & RISPOSTE LUNGHE (NO CUT-OFF):\n"
            + "Su Android 14/15/16 (HyperOS / OneUI), il sistema chiudeva la comunicazione Bluetooth SCO esattamente dopo 6 secondi durante le risposte vocali lunghe di Google Assistant o Gemini. AAMotoMic Pro mantiene attivo il canale con una traccia keep-alive digitale silenziosa a livello di sistema.\n\n"
            + "3. INTERRUZIONE RISPOSTA DA TASTO INTERFONO:\n"
            + "Mentre l'assistente sta parlando una risposta lunga, premendo il pulsante dell'interfono l'app interrompe istantaneamente la risposta e sblocca subito la musica in stereo A2DP ad alta fedeltà.\n\n"
            + "4. REQUISITI & USO:\n"
            + "• Tocca 'SCAN BT' e scegli il tuo interfono.\n"
            + "• Premi 'SALVA E RIAVVIA'.\n"
            + "• Abilita 'Auto-Start' per far partire il servizio da solo quando sali in moto.\n\n"
            + "────────────────────────\n\n"
            + "🇬🇧 HOW IT WORKS (English)\n\n"
            + "1. PURPOSE & PROBLEM SOLVED:\n"
            + "When using Android Auto on a motorcycle with an external head unit/display, Android Auto routes microphone input to the head unit's noisy built-in mic. AAMotoMic Pro automatically routes voice commands and audio directly to your helmet Bluetooth intercom (Cardo, Sena, VR1, etc.).\n\n"
            + "2. GEMINI LIVE & LONG ANSWERS (NO CUT-OFF):\n"
            + "On Android 14/15/16, the system's AudioDeviceBroker kills Bluetooth SCO after exactly 6 seconds during long Google Assistant or Gemini answers. AAMotoMic Pro uses a system-level digital silence keep-alive track to keep the channel open flawlessly.\n\n"
            + "3. INTERCOM BUTTON INTERRUPT:\n"
            + "Pressing your helmet intercom button while the Assistant is speaking immediately terminates the speech response and instantly restores full-fidelity A2DP stereo music.\n\n"
            + "4. SETUP:\n"
            + "• Tap 'SCAN BT' and pick your intercom.\n"
            + "• Tap 'SAVE & RESTART'.\n"
            + "• Enable 'Auto-Start' for seamless hands-free operation.";

        tv.setText(text);
        content.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("ℹ️ AAMotoMic Pro • Info & Guida")
                .setView(sv)
                .setPositiveButton("CHIUDI / CLOSE", null)
                .show();
    }

    private void scanPairedDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestRequiredPermissions();
                Toast.makeText(this, "Concedi il permesso Bluetooth e riprova.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, I18n.isIt(this)
                    ? "Bluetooth non supportato su questo dispositivo."
                    : "Bluetooth not supported on this device.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.isEnabled()) {
            Toast.makeText(this, I18n.toastBtDisabled(this), Toast.LENGTH_LONG).show();
            return;
        }
        Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (SecurityException e) {
            Toast.makeText(this, I18n.toastBtGranted(this), Toast.LENGTH_SHORT).show();
            return;
        }
        if (bonded == null || bonded.isEmpty()) {
            Toast.makeText(this, I18n.toastNoPaired(this), Toast.LENGTH_SHORT).show();
            return;
        }

        final List<String> labels = new ArrayList<>();
        final List<String> macs = new ArrayList<>();
        final List<String> names = new ArrayList<>();

        for (BluetoothDevice dev : bonded) {
            String name = "Unknown";
            String mac = "";
            try {
                if (dev.getName() != null) name = dev.getName();
                if (dev.getAddress() != null) mac = dev.getAddress();
            } catch (SecurityException ignored) {
            }
            if (!mac.isEmpty()) {
                labels.add(name + "\n" + mac);
                macs.add(mac);
                names.add(name);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(I18n.dialogSelectTarget(this))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String selectedMac = macs.get(which);
                    String selectedName = names.get(which);
                    edtMac.setText(selectedMac);
                    txtTargetName.setText(I18n.targetSelected(this, selectedName));
                    AppPrefs.setTargetName(this, selectedName);
                    DiagLogger.log(this, "[UI] Target selected: name=" + selectedName + ", addr=" + selectedMac);
                })
                .setNegativeButton(I18n.isIt(this) ? "Annulla" : "Cancel", null)
                .show();
    }

    private void saveSettingsFromUi() {
        String mac = edtMac.getText().toString().trim().toUpperCase();
        AppPrefs.setTargetMac(this, mac);
        AppPrefs.setCheckIntervalIndex(this, spnInterval.getSelectedItemPosition());
        AppPrefs.setReleaseDelayIndex(this, spnTimeout.getSelectedItemPosition());
        AppPrefs.setGeminiLiveMode(this, chkGeminiLive.isChecked());
        AppPrefs.setAggressiveMode(this, chkAggressive.isChecked());
        AppPrefs.setAutoStartEnabled(this, chkAutoStart.isChecked());
        AppPrefs.setDiagLoggingEnabled(this, chkDiagLog.isChecked());
    }

    private void saveAndRestartService() {
        String mac = edtMac.getText().toString().trim().toUpperCase();
        if (!MAC_REGEX.matcher(mac).matches()) {
            new AlertDialog.Builder(this)
                    .setTitle("MAC non valido")
                    .setMessage("Inserisci un indirizzo MAC valido nel formato XX:XX:XX:XX:XX:XX oppure premi 'SCAN BT'.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        saveSettingsFromUi();
        DiagLogger.log(this, "[USER] Settings saved: target=" + mac
                + " | intervalIdx=" + spnInterval.getSelectedItemPosition()
                + " | timeoutIdx=" + spnTimeout.getSelectedItemPosition()
                + " | geminiLive=" + chkGeminiLive.isChecked()
                + " | aggressive=" + chkAggressive.isChecked()
                + " | autoStart=" + chkAutoStart.isChecked());

        stopService(new Intent(this, AudioRouterService.class));
        uiHandler.postDelayed(() -> {
            startRouterService();
            Toast.makeText(this, I18n.toastSaved(this), Toast.LENGTH_SHORT).show();
            refreshStatusUi();
        }, 300L);
    }

    private void toggleService() {
        if (AudioRouterService.isRunning) {
            String[] stopOptions = I18n.isIt(this)
                    ? new String[]{"Ferma solo il servizio", "Ferma e Disattiva Auto-Start", "Annulla"}
                    : new String[]{"Stop service only", "Stop service & Disable Auto-Start", "Cancel"};
            new AlertDialog.Builder(this)
                    .setTitle(I18n.isIt(this) ? "Ferma Servizio" : "Stop Service")
                    .setItems(stopOptions, (d, which) -> {
                        if (which == 0 || which == 1) {
                            if (which == 1) {
                                chkAutoStart.setChecked(false);
                                AppPrefs.setAutoStartEnabled(this, false);
                            }
                            stopService(new Intent(this, AudioRouterService.class));
                            uiHandler.postDelayed(this::refreshStatusUi, 200L);
                        }
                    })
                    .show();
        } else {
            String mac = edtMac.getText().toString().trim().toUpperCase();
            if (!MAC_REGEX.matcher(mac).matches()) {
                Toast.makeText(this, I18n.isIt(this)
                        ? "Seleziona prima un interfono con SCAN BT."
                        : "Select an intercom device with SCAN BT first.", Toast.LENGTH_LONG).show();
                return;
            }
            saveSettingsFromUi();
            startRouterService();
            uiHandler.postDelayed(this::refreshStatusUi, 200L);
        }
    }

    private void startRouterService() {
        Intent svc = new Intent(this, AudioRouterService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    private void requestRequiredPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        } else if (!AudioRouterService.isRunning && !AppPrefs.getTargetMac(this).isEmpty()) {
            startRouterService();
        }
    }

    private TextView makeSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#8B949E"));
        return tv;
    }

    private CheckBox makeCheckBox(String text, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextColor(Color.parseColor("#E6EDF3"));
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cb.setChecked(checked);
        cb.setPadding(dp(6), dp(8), dp(6), dp(8));
        return cb;
    }

    private Button makeButton(String text, String bgHex, int textColor, int spSize) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(roundedRect(bgHex, bgHex, 12));
        return b;
    }

    private ArrayAdapter<String> makeSpinnerAdapter(String[] items) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(Color.WHITE);
                    ((TextView) v).setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                v.setBackgroundColor(Color.parseColor("#161B22"));
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(Color.WHITE);
                    ((TextView) v).setPadding(dp(14), dp(12), dp(14), dp(12));
                }
                return v;
            }
        };
    }

    private GradientDrawable roundedRect(String fillHex, String strokeHex, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(dp(radiusDp));
        gd.setStroke(dp(1), Color.parseColor(strokeHex));
        return gd;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
