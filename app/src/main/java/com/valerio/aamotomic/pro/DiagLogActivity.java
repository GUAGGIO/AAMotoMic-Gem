package com.valerio.aamotomic.pro;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Diagnostic Log Viewer Activity with 1-tap Sanitized Copy, Full Copy, Refresh, and Clear.
 */
public class DiagLogActivity extends Activity {
    private TextView txtMeta;
    private TextView txtLogContent;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLog();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D1117"));
        int pad = dp(14);
        root.setPadding(pad, dp(16), pad, pad);

        // Top Bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header);

        Button btnBack = makeSmallButton(I18n.diagBack(this), "#21262D");
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(12), 0, 0, 0);
        header.addView(titleCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(I18n.diagTitle(this));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(title);

        txtMeta = new TextView(this);
        txtMeta.setTextColor(Color.parseColor("#8B949E"));
        txtMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleCol.addView(txtMeta);

        // Action Buttons Row
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actLp.topMargin = dp(12);
        actLp.bottomMargin = dp(10);
        root.addView(actions, actLp);

        Button btnCopySafe = makeSmallButton(I18n.diagCopySafe(this), "#238636");
        btnCopySafe.setOnClickListener(v -> copyLog(true));
        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f);
        b1Lp.rightMargin = dp(6);
        actions.addView(btnCopySafe, b1Lp);

        Button btnCopyFull = makeSmallButton(I18n.diagCopyFull(this), "#1F6FEB");
        btnCopyFull.setOnClickListener(v -> copyLog(false));
        LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        b2Lp.rightMargin = dp(6);
        actions.addView(btnCopyFull, b2Lp);

        Button btnClear = makeSmallButton(I18n.diagClear(this), "#DA3633");
        btnClear.setOnClickListener(v -> {
            DiagLogger.clear(this);
            refreshLog();
            Toast.makeText(this, I18n.diagClearedToast(this), Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams b3Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f);
        actions.addView(btnClear, b3Lp);

        // Scrollable Log Area (uses WRAP_CONTENT height inside ScrollView instead of fixed 9000dip!)
        scrollView = new ScrollView(this);
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.parseColor("#090C10"));
        box.setCornerRadius(dp(10));
        box.setStroke(dp(1), Color.parseColor("#30363D"));
        scrollView.setBackground(box);
        scrollView.setPadding(dp(12), dp(12), dp(12), dp(12));

        txtLogContent = new TextView(this);
        txtLogContent.setTypeface(Typeface.MONOSPACE);
        txtLogContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        txtLogContent.setTextColor(Color.parseColor("#3FB950"));
        txtLogContent.setTextIsSelectable(true);
        scrollView.addView(txtLogContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void refreshLog() {
        String content = DiagLogger.readAll(this);
        txtLogContent.setText(content);
        String status = AppPrefs.isDiagLoggingEnabled(this) ? "ON" : "OFF";
        String sizeLabel = I18n.isIt(this) ? "Dimensione: " : "Size: ";
        String maxLines = I18n.isIt(this) ? "max 600 righe" : "max 600 lines";
        txtMeta.setText("Logging: " + status + " | " + sizeLabel + DiagLogger.getSizeKb(this) + " KB (" + maxLines + ")");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void copyLog(boolean sanitize) {
        String raw = DiagLogger.readAll(this);
        String out = sanitize ? DiagLogger.sanitize(raw) : raw;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("AAMotoMic Pro Log", out));
            String msg = sanitize
                    ? (I18n.isIt(this) ? "Log sanitizzato copiato negli appunti!" : "Sanitized log copied to clipboard!")
                    : (I18n.isIt(this) ? "Log completo copiato negli appunti!" : "Full log copied to clipboard!");
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private Button makeSmallButton(String label, String hexColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), dp(8), dp(4), dp(8));
        b.setMinHeight(dp(44));
        b.setMinimumHeight(dp(44));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(hexColor));
        gd.setCornerRadius(dp(8));
        b.setBackground(gd);
        return b;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
