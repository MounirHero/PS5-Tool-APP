package com.matrix.ps5tool;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Payloads: tap = send, long-press = menu, FAB = add, pinned Autoloader entry on top. */
public class PayloadActivity extends BaseActivity {

    private static final int REQ_PICK = 41;

    private ListView list;
    private TextView empty, log;
    private ProgressBar progress;
    private ScrollView logScroll;
    private final List<PayloadManager.Payload> payloads = new ArrayList<>();
    private BaseAdapter adapter;
    private boolean sending;
    private boolean autoloaderConfigured;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_payload);
        setupHeader("Payloads Manager");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("tap to send \u2022 long-press for options");

        list = findViewById(R.id.payloads_list);
        empty = findViewById(R.id.payloads_empty);
        log = findViewById(R.id.payload_log);
        progress = findViewById(R.id.send_progress);
        logScroll = (ScrollView) log.getParent();
        empty.setTextColor(Theme.textHint(this));

        ImageView fab = findViewById(R.id.fab_add);
        fab.setBackground(Theme.fab(this));
        fab.setOnClickListener(v -> pickFile());

        Button setup = findViewById(R.id.btn_autoloader_setup);
        Theme.styleButton(this, setup);
        setup.setOnClickListener(v -> openAutoloaderSetup());

        adapter = new BaseAdapter() {
            @Override public int getCount() { return payloads.size() + (autoloaderConfigured ? 1 : 0); }
            @Override public Object getItem(int i) {
                return (autoloaderConfigured && i == 0) ? null : payloads.get(i - (autoloaderConfigured ? 1 : 0));
            }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(PayloadActivity.this)
                        .inflate(R.layout.item_payload, parent, false);
                TextView name = v.findViewById(R.id.payload_name);
                TextView info = v.findViewById(R.id.payload_info);
                View root = v.findViewById(R.id.item_root);
                name.setTextColor(Theme.textPrimary(PayloadActivity.this));
                info.setTextColor(Theme.textSecondary(PayloadActivity.this));
                if (autoloaderConfigured && i == 0) {
                    Theme.stylePinned(PayloadActivity.this, root);
                    name.setText("\u26A1 " + Prefs.getAutoloaderName(PayloadActivity.this));
                    name.setTextColor(Theme.accent(PayloadActivity.this));
                    info.setText("Autoloader sequence \u2022 tap to run");
                } else {
                    Theme.styleItem(PayloadActivity.this, root);
                    PayloadManager.Payload pl = (PayloadManager.Payload) getItem(i);
                    name.setText(pl.name);
                    name.setSelected(true); // marquee
                    info.setText(PayloadManager.formatSize(pl.size) + " \u2022 "
                            + new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                                    .format(new Date(pl.time)));
                }
                return v;
            }
        };
        list.setAdapter(adapter);

        list.setOnItemClickListener((p, v, pos, id) -> {
            if (sending) return;
            if (autoloaderConfigured && pos == 0) runAutoloader();
            else sendPayload(payloads.get(pos - (autoloaderConfigured ? 1 : 0)));
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (autoloaderConfigured && pos == 0) autoloaderMenu();
            else payloadMenu(payloads.get(pos - (autoloaderConfigured ? 1 : 0)));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        payloads.clear();
        payloads.addAll(PayloadManager.getPayloads(this));
        autoloaderConfigured = false;
        try {
            JSONArray seq = new JSONArray(Prefs.getAutoloaderSeq(this));
            autoloaderConfigured = seq.length() > 0;
        } catch (Exception ignored) {}
        adapter.notifyDataSetChanged();
        empty.setVisibility(payloads.isEmpty() && !autoloaderConfigured ? View.VISIBLE : View.GONE);
    }

    private void logLine(String s) {
        log.append(s + "\n");
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------- add ----------

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                String name = queryName(uri);
                InputStream in = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                PayloadManager.addPayload(this, name, out.toByteArray());
                logLine("+ added " + name);
                refresh();
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private String queryName(Uri uri) {
        String name = null;
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        if (name == null) {
            name = uri.getLastPathSegment();
            if (name == null) name = "payload.bin";
        }
        return name;
    }

    // ---------- send ----------

    private void sendPayload(PayloadManager.Payload pl) {
        String ip = Prefs.getActiveIp(this);
        if (ip == null || ip.isEmpty()) {
            Toast.makeText(this, "No console selected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] data = PayloadManager.readPayload(this, pl);
            sending = true;
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(0);
            logLine("> sending " + pl.name + " to " + ip + ":" + Prefs.getElfPort(this));
            TcpPayloadSender.send(ip, Prefs.getElfPort(this), data, new TcpPayloadSender.Callback() {
                @Override public void onProgress(int percent) { progress.setProgress(percent); }
                @Override public void onSuccess() {
                    sending = false;
                    progress.setProgress(100);
                    logLine("< sent OK (" + PayloadManager.formatSize(data.length) + ")");
                    progress.postDelayed(() -> progress.setVisibility(View.GONE), 1200);
                }
                @Override public void onError(String message) {
                    sending = false;
                    progress.setVisibility(View.GONE);
                    logLine("! error: " + message);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Read error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- menus ----------

    private void payloadMenu(PayloadManager.Payload pl) {
        String[] items = {"Rename", "Delete", "Add to autoloader"};
        new AlertDialog.Builder(this)
                .setTitle(pl.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) renameDialog(pl);
                    else if (which == 1) {
                        PayloadManager.deletePayload(this, pl);
                        logLine("- deleted " + pl.name);
                        refresh();
                    } else addToAutoloader(pl);
                })
                .show();
    }

    private void renameDialog(PayloadManager.Payload pl) {
        EditText input = new EditText(this);
        input.setText(pl.name);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Rename")
                .setView(wrap)
                .setPositiveButton("OK", (d, w) -> {
                    String nn = input.getText().toString().trim();
                    if (!nn.isEmpty()) {
                        PayloadManager.renamePayload(this, pl, nn);
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addToAutoloader(PayloadManager.Payload pl) {
        EditText input = new EditText(this);
        input.setHint("Delay after send (seconds)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(Prefs.getAutoloaderDelay(this)));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Add to autoloader")
                .setMessage(pl.name)
                .setView(wrap)
                .setPositiveButton("Add", (d, w) -> {
                    int delay = Prefs.getAutoloaderDelay(this);
                    try { delay = Integer.parseInt(input.getText().toString().trim()); } catch (Exception ignored) {}
                    try {
                        JSONArray seq = new JSONArray(Prefs.getAutoloaderSeq(this));
                        JSONObject step = new JSONObject();
                        step.put("path", pl.path);
                        step.put("name", pl.name);
                        step.put("delay", delay);
                        seq.put(step);
                        Prefs.setAutoloaderSeq(this, seq.toString());
                        logLine("+ autoloader step: " + pl.name + " (+" + delay + "s)");
                        refresh();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- autoloader ----------

    private void autoloaderMenu() {
        String[] items = {"Rename", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(Prefs.getAutoloaderName(this))
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        EditText input = new EditText(this);
                        input.setText(Prefs.getAutoloaderName(this));
                        int pad = (int) (20 * getResources().getDisplayMetrics().density);
                        LinearLayout wrap = new LinearLayout(this);
                        wrap.setPadding(pad, pad / 2, pad, 0);
                        wrap.addView(input);
                        new AlertDialog.Builder(this)
                                .setTitle("Rename autoloader")
                                .setView(wrap)
                                .setPositiveButton("OK", (dd, w) -> {
                                    String nn = input.getText().toString().trim();
                                    if (!nn.isEmpty()) {
                                        Prefs.setAutoloaderName(this, nn);
                                        refresh();
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        Prefs.setAutoloaderSeq(this, "[]");
                        logLine("- autoloader cleared");
                        refresh();
                    }
                })
                .show();
    }

    private void openAutoloaderSetup() {
        try {
            JSONArray seq = new JSONArray(Prefs.getAutoloaderSeq(this));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < seq.length(); i++) {
                JSONObject s = seq.getJSONObject(i);
                sb.append(i + 1).append(". ").append(s.getString("name"))
                        .append("  \u2192  delay ").append(s.getInt("delay")).append("s\n");
            }
            if (seq.length() == 0) sb.append("(empty sequence)\n");
            sb.append("\nLong-press a payload \u2192 \"Add to autoloader\"\nto add payloads with custom delays.");
            new AlertDialog.Builder(this)
                    .setTitle("Setup Autoloader")
                    .setMessage(sb.toString())
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Clear sequence", (d, w) -> {
                        Prefs.setAutoloaderSeq(this, "[]");
                        refresh();
                    })
                    .show();
        } catch (Exception ignored) {}
    }

    private void runAutoloader() {
        String ip = Prefs.getActiveIp(this);
        if (ip == null || ip.isEmpty()) {
            Toast.makeText(this, "No console selected", Toast.LENGTH_SHORT).show();
            return;
        }
        final JSONArray seq;
        try {
            seq = new JSONArray(Prefs.getAutoloaderSeq(this));
        } catch (Exception e) { return; }
        if (seq.length() == 0) return;
        sending = true;
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        logLine("> running " + Prefs.getAutoloaderName(this) + " (" + seq.length() + " steps)");
        new Thread(() -> {
            try {
                for (int i = 0; i < seq.length(); i++) {
                    JSONObject s = seq.getJSONObject(i);
                    PayloadManager.Payload pl = PayloadManager.findByPath(this, s.getString("path"));
                    if (pl == null) { logLine("! step " + (i + 1) + " missing, skipped"); continue; }
                    final int step = i + 1;
                    runOnUiThread(() -> logLine("> step " + step + ": " + pl.name));
                    byte[] data = PayloadManager.readPayload(this, pl);
                    final boolean[] done = {false};
                    final boolean[] ok = {false};
                    TcpPayloadSender.send(ip, Prefs.getElfPort(this), data, new TcpPayloadSender.Callback() {
                        @Override public void onProgress(int percent) {
                            int overall = (int) (((step - 1 + percent / 100f) / seq.length()) * 100);
                            progress.setProgress(overall);
                        }
                        @Override public void onSuccess() { synchronized (done) { ok[0] = true; done[0] = true; done.notify(); } }
                        @Override public void onError(String m) { synchronized (done) { done[0] = true; done.notify(); logLine("! " + m); } }
                    });
                    synchronized (done) { while (!done[0]) done.wait(); }
                    if (ok[0]) logLine("< step " + step + " sent");
                    int delay = s.optInt("delay", 0);
                    if (delay > 0 && i < seq.length() - 1) {
                        logLine("  waiting " + delay + "s...");
                        Thread.sleep(delay * 1000L);
                    }
                }
                runOnUiThread(() -> {
                    sending = false;
                    progress.setProgress(100);
                    logLine("< autoloader complete");
                    progress.postDelayed(() -> progress.setVisibility(View.GONE), 1500);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    sending = false;
                    progress.setVisibility(View.GONE);
                    logLine("! autoloader error: " + e.getMessage());
                });
            }
        }, "autoloader").start();
    }
}
