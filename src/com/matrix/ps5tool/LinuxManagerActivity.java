package com.matrix.ps5tool;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/** Linux: big centered icon, Boot Linux with confirmation dialog. */
public class LinuxManagerActivity extends BaseActivity {

    private TextView log;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_linux);
        setupHeader("Linux");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("boot linux on your ps5");

        log = findViewById(R.id.linux_log);
        progress = findViewById(R.id.linux_progress);
        TextView desc = findViewById(R.id.linux_desc);
        desc.setTextColor(Theme.textSecondary(this));
        android.widget.ImageView icon = findViewById(R.id.linux_icon);
        Theme.tintLogo(this, icon);

        Button boot = findViewById(R.id.btn_boot_linux);
        Theme.styleButton(this, boot);
        boot.setOnClickListener(v -> confirmBoot());
    }

    private void logLine(String s) { log.append(s + "\n"); }

    private void confirmBoot() {
        String ip = Prefs.getActiveIp(this);
        if (ip == null || ip.isEmpty()) {
            Toast.makeText(this, "No console selected", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Boot Linux")
                .setMessage("Send ps5-linux-loader 2.4 to console " + ip + "?\n\n" +
                        "The console will enter rest mode: wait until the orange LED " +
                        "stops blinking, then press the power button to boot Linux.\n\n" +
                        "Disable etaHEN/kstuff before proceeding (incompatible).")
                .setPositiveButton("BOOT", (d, w) -> sendElf(Prefs.getLinuxLoaderElf(this)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendElf(String elf) {
        String ip = Prefs.getActiveIp(this);
        try {
            InputStream in = getAssets().openFd(elf).createInputStream();
            long size = getAssets().openFd(elf).getLength();
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(0);
            logLine("> sending " + elf + " (" + PayloadManager.formatSize(size) + ")");
            TcpPayloadSender.sendStream(ip, Prefs.getElfPort(this), in, size, new TcpPayloadSender.Callback() {
                @Override public void onProgress(int percent) { progress.setProgress(percent); }
                @Override public void onSuccess() {
                    progress.setVisibility(View.GONE);
                    logLine("< loader sent \u2014 the console is about to enter rest mode");
                    logLine("  wait for a steady orange LED, then press power");
                }
                @Override public void onError(String message) {
                    progress.setVisibility(View.GONE);
                    logLine("! error: " + message);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "ELF missing in assets: " + elf, Toast.LENGTH_LONG).show();
            logLine("! ELF missing in assets: " + elf);
        }
    }
}
