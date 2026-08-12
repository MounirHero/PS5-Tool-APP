package com.matrix.ps5tool;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Settings: spaced category titles, theme + accent, all ports and per-function prefs. */
public class SettingsActivity extends BaseActivity {

    private LinearLayout container;
    private float d;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        setupHeader("Settings");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("configure ps5 tool");

        d = getResources().getDisplayMetrics().density;
        container = findViewById(R.id.settings_container);
        build();
    }

    private void build() {
        container.removeAllViews();

        category("Connection");
        editRow("Manual IP override", Prefs.getManualIp(this),
                "Manual IP (overrides scanning)", InputType.TYPE_CLASS_TEXT,
                v -> Prefs.setManualIp(this, v));
        editRow("ELF loader port", String.valueOf(Prefs.getElfPort(this)),
                "Payload/ELF send port", InputType.TYPE_CLASS_NUMBER,
                v -> Prefs.setElfPort(this, parseInt(v, 9021)));
        editRow("FTP port", String.valueOf(Prefs.getFtpPort(this)),
                "Console FTP server port", InputType.TYPE_CLASS_NUMBER,
                v -> Prefs.setFtpPort(this, parseInt(v, 2121)));
        editRow("FTP user", Prefs.getFtpUser(this), "FTP user",
                InputType.TYPE_CLASS_TEXT, v -> Prefs.setFtpUser(this, v));
        editRow("FTP password", Prefs.getFtpPass(this), "FTP password",
                InputType.TYPE_CLASS_TEXT, v -> Prefs.setFtpPass(this, v));

        category("Theme");
        switchRow("Light theme", Prefs.isLight(this), on -> {
            Prefs.setLight(this, on);
            recreate();
        });
        accentRow();

        category("Haptics");
        switchRow("Vibration on touch", Prefs.isHapticsOn(this),
                on -> Prefs.setHapticsOn(this, on));
        hapticDurationRow();

        category("Payload");
        editRow("Autoloader delay (s)", String.valueOf(Prefs.getAutoloaderDelay(this)),
                "Default delay between payloads", InputType.TYPE_CLASS_NUMBER,
                v -> Prefs.setAutoloaderDelay(this, parseInt(v, 5)));

        category("Screen Cast");
        editRow("Cast ELF", Prefs.getCastElf(this), "Player ELF sent to the console",
                InputType.TYPE_CLASS_TEXT, v -> Prefs.setCastElf(this, v));
        editRow("Cast port", String.valueOf(Prefs.getCastPort(this)),
                "Cast server port", InputType.TYPE_CLASS_NUMBER,
                v -> Prefs.setCastPort(this, parseInt(v, 8080)));

        category("Games Manager");
        editRow("Game paths", Prefs.getGamePaths(this),
                "Scan paths (comma separated)", InputType.TYPE_CLASS_TEXT,
                v -> Prefs.setGamePaths(this, v));
        editRow("Upload dir", Prefs.getGameUploadDir(this),
                "Game upload directory", InputType.TYPE_CLASS_TEXT,
                v -> Prefs.setGameUploadDir(this, v));
        editRow("Game API port", String.valueOf(Prefs.getGameApiPort(this)),
                "Console game-launch API port", InputType.TYPE_CLASS_NUMBER,
                v -> Prefs.setGameApiPort(this, parseInt(v, 8080)));
        editRow("ShadowMount ELF", Prefs.getShadowmountElf(this),
                "ELF to mount games from usb0/usb1", InputType.TYPE_CLASS_TEXT,
                v -> Prefs.setShadowmountElf(this, v));

        category("Linux");
        editRow("Loader ELF", Prefs.getLinuxLoaderElf(this), "Linux boot ELF",
                InputType.TYPE_CLASS_TEXT, v -> Prefs.setLinuxLoaderElf(this, v));

        category("Remote Play");
        editRow("Chiaki package", Prefs.getChiakiPkg(this), "Chiaki client package name",
                InputType.TYPE_CLASS_TEXT, v -> Prefs.setChiakiPkg(this, v));

        category("Web Interfaces");
        editRow("Default port", String.valueOf(Prefs.getWebDefaultPort(this)),
                "Default port for new interfaces", InputType.TYPE_CLASS_NUMBER,
                v -> Prefs.setWebDefaultPort(this, parseInt(v, 8080)));

        category("System");
        batteryRow();

        category("Info");
        textRow("PS5 Tool v5.5", "Credits: InsideMatrix");
    }

    // ---------- row builders ----------

    private void category(String title) {
        TextView t = new TextView(this);
        t.setText(title.toUpperCase());
        t.setTextSize(13);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(Theme.accent(this));
        int top = (int) (container.getChildCount() == 0 ? 10 * d : 26 * d);
        t.setPadding((int) (4 * d), top, 0, (int) (8 * d));
        container.addView(t);
    }

    private interface Saver { void save(String v); }
    private interface BoolCb { void on(boolean b); }

    private void editRow(String label, String value, String hint, int inputType, Saver saver) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding((int) (4 * d), (int) (6 * d), (int) (4 * d), (int) (6 * d));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(15);
        l.setTextColor(Theme.textPrimary(this));
        row.addView(l);

        TextView h = new TextView(this);
        h.setText(hint);
        h.setTextSize(11);
        h.setTextColor(Theme.textSecondary(this));
        row.addView(h);

        EditText e = new EditText(this);
        e.setText(value);
        e.setInputType(inputType | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setTextColor(Theme.textPrimary(this));
        e.setHintTextColor(Theme.textHint(this));
        e.setTextSize(14);
        row.addView(e);
        e.setOnFocusChangeListener((v, has) -> {
            if (!has) saver.save(e.getText().toString().trim());
        });

        container.addView(row);
    }

    private void switchRow(String label, boolean on, BoolCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (4 * d), (int) (8 * d), (int) (4 * d), (int) (8 * d));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(15);
        l.setTextColor(Theme.textPrimary(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(l, lp);

        Switch s = new Switch(this);
        s.setChecked(on);
        s.setOnCheckedChangeListener((btn, checked) -> cb.on(checked));
        row.addView(s);

        container.addView(row);
    }

    private void accentRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (4 * d), (int) (8 * d), (int) (4 * d), (int) (8 * d));

        TextView l = new TextView(this);
        l.setText("Accent color");
        l.setTextSize(15);
        l.setTextColor(Theme.textPrimary(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(l, lp);

        int[] accents = Prefs.getAccents();
        for (int i = 0; i < accents.length; i++) {
            View dot = new View(this);
            int size = (int) (26 * d);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(size, size);
            dp.setMargins((int) (5 * d), 0, (int) (5 * d), 0);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(accents[i]);
            if (i == Prefs.getAccentIndex(this)) g.setStroke((int) (2.5f * d), Theme.textPrimary(this));
            dot.setBackground(g);
            final int idx = i;
            dot.setOnClickListener(v -> {
                Prefs.setAccentIndex(this, idx);
                recreate();
            });
            row.addView(dot, dp);
        }
        container.addView(row);
    }

    private void textRow(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding((int) (4 * d), (int) (6 * d), (int) (4 * d), (int) (6 * d));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(Theme.textPrimary(this));
        row.addView(t);
        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextSize(11);
        s.setTextColor(Theme.textSecondary(this));
        row.addView(s);
        container.addView(row);
    }

    /** Vibration duration row: tap opens a SeekBar dialog (5–150 ms, step 5). */
    private void hapticDurationRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (4 * d), (int) (8 * d), (int) (4 * d), (int) (8 * d));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(this);
        l.setText("Vibration duration");
        l.setTextSize(15);
        l.setTextColor(Theme.textPrimary(this));
        texts.addView(l);
        TextView h = new TextView(this);
        h.setText("How long each tap vibrates");
        h.setTextSize(11);
        h.setTextColor(Theme.textSecondary(this));
        texts.addView(h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(texts, lp);

        TextView value = new TextView(this);
        value.setText(Prefs.getHapticMs(this) + " ms");
        value.setTextSize(14);
        value.setTypeface(null, android.graphics.Typeface.BOLD);
        value.setTextColor(Theme.accent(this));
        value.setPadding((int) (12 * d), (int) (8 * d), (int) (12 * d), (int) (8 * d));
        row.addView(value);

        row.setOnClickListener(v -> hapticDurationDialog());
        container.addView(row);
    }

    private void hapticDurationDialog() {
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * d);
        lay.setPadding(pad, (int) (12 * d), pad, 0);

        TextView cur = new TextView(this);
        cur.setTextSize(15);
        cur.setTextColor(Theme.textPrimary(this));
        lay.addView(cur);

        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(29); // (progress + 1) * 5 -> 5..150 ms
        sb.setProgress(Math.min(29, Math.max(0, Prefs.getHapticMs(this) / 5 - 1)));
        lay.addView(sb);

        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) {
                cur.setText(((p + 1) * 5) + " ms");
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
        cur.setText(((sb.getProgress() + 1) * 5) + " ms");

        new AlertDialog.Builder(this)
                .setTitle("Vibration duration")
                .setView(lay)
                .setPositiveButton("OK", (dlg, w) -> {
                    Prefs.setHapticMs(this, (sb.getProgress() + 1) * 5);
                    Haptics.tap(this); // feel the new duration right away
                    build();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Battery optimization whitelist: keeps the cast service alive in background. */
    private void batteryRow() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (4 * d), (int) (8 * d), (int) (4 * d), (int) (8 * d));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(this);
        l.setText("Battery optimization");
        l.setTextSize(15);
        l.setTextColor(Theme.textPrimary(this));
        texts.addView(l);
        TextView h = new TextView(this);
        h.setText(ignoring ? "Disabled (app stays alive in background)" : "Enabled (tap to disable for background casting)");
        h.setTextSize(11);
        h.setTextColor(Theme.textSecondary(this));
        texts.addView(h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(texts, lp);

        if (!ignoring) {
            TextView btn = new TextView(this);
            btn.setText("DISABLE");
            btn.setTextSize(13);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            btn.setTextColor(Theme.accent(this));
            btn.setPadding((int) (12 * d), (int) (8 * d), (int) (12 * d), (int) (8 * d));
            btn.setOnClickListener(v -> {
                try {
                    startActivity(new android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new android.content.Intent(
                            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            });
            row.addView(btn);
        }
        container.addView(row);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(); // refresh battery row state
    }

    private static int parseInt(String v, int def) {
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
    }
}
