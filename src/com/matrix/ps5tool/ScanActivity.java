package com.matrix.ps5tool;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Scan screen: continuous auto-scan + manual IP pinned at the container bottom. */
public class ScanActivity extends BaseActivity {

    private ListView list;
    private TextView empty;
    private EditText manualIp;
    private ConsoleScanner scanner;
    private final List<ConsoleScanner.ConsoleInfo> consoles = new ArrayList<>();
    private ConsoleAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_scan);
        setupHeader("PS5 Tool");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("scan for a ps5 to start");

        list = findViewById(R.id.consoles_list);
        empty = findViewById(R.id.scan_empty);
        manualIp = findViewById(R.id.manual_ip);
        Button connect = findViewById(R.id.btn_connect);
        TextView label = findViewById(R.id.scan_label);
        label.setTextColor(Theme.textSecondary(this));
        empty.setTextColor(Theme.textHint(this));

        Theme.styleButton(this, connect);
        manualIp.setTextColor(Theme.textPrimary(this));
        manualIp.setHintTextColor(Theme.textHint(this));
        Theme.styleItem(this, manualIp);

        String saved = Prefs.getManualIp(this);
        if (saved != null && !saved.isEmpty()) manualIp.setText(saved);

        // notification permission is needed for the cast media controls (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        adapter = new ConsoleAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            ConsoleScanner.ConsoleInfo c = consoles.get(pos);
            Prefs.setSelectedIp(this, c.ip);
            sendFtpSrv(c.ip);
            open(new Intent(this, MainActivity.class));
        });

        connect.setOnClickListener(v -> connectManual());
        manualIp.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { connectManual(); return true; }
            return false;
        });
    }

    /** Automatically push the FTP server payload right after connecting. */
    private void sendFtpSrv(String ip) {
        try {
            String elf = Prefs.getFtpSrvElf(this);
            java.io.InputStream in = getAssets().openFd(elf).createInputStream();
            long size = getAssets().openFd(elf).getLength();
            Toast.makeText(this, "Sending FTP server to the console…", Toast.LENGTH_SHORT).show();
            TcpPayloadSender.sendStream(ip, Prefs.getElfPort(this), in, size, new TcpPayloadSender.Callback() {
                @Override public void onProgress(int percent) {}
                @Override public void onSuccess() {
                    Toast.makeText(ScanActivity.this, "FTP server started on the console", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String message) {
                    Toast.makeText(ScanActivity.this, "FTP server: " + message, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "ftpsrv ELF missing: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void connectManual() {
        String ip = manualIp.getText().toString().trim();
        if (ip.isEmpty()) {
            Toast.makeText(this, "Enter an IP address", Toast.LENGTH_SHORT).show();
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(manualIp.getWindowToken(), 0);
        Prefs.setManualIp(this, ip);
        sendFtpSrv(ip);
        open(new Intent(this, MainActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanner = new ConsoleScanner();
        scanner.start(this, results -> {
            consoles.clear();
            consoles.addAll(results);
            adapter.notifyDataSetChanged();
            empty.setVisibility(consoles.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scanner != null) scanner.stop();
    }

    private class ConsoleAdapter extends BaseAdapter {
        @Override public int getCount() { return consoles.size(); }
        @Override public Object getItem(int i) { return consoles.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(ScanActivity.this)
                    .inflate(R.layout.item_console, parent, false);
            ConsoleScanner.ConsoleInfo c = consoles.get(i);
            View root = v.findViewById(R.id.item_root);
            Theme.styleItem(ScanActivity.this, root);
            TextView name = v.findViewById(R.id.console_name);
            TextView ip = v.findViewById(R.id.console_ip);
            ImageView icon = v.findViewById(R.id.console_icon);
            name.setText("PS5");
            name.setTextColor(Theme.textPrimary(ScanActivity.this));
            ip.setText(c.ip);
            ip.setTextColor(Theme.textSecondary(ScanActivity.this));
            Theme.tintAccent(ScanActivity.this, icon);
            return v;
        }
    }
}
