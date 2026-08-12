package com.matrix.ps5tool;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Screen Cast: PSPlay ELF inject + DLNA receiver selection + media casting + live log. */
public class ScreenCastActivity extends BaseActivity {

    private static final int REQ_MEDIA = 45;
    private static final int REQ_SUBS = 46;

    private Uri pendingVideo;

    private TextView deviceView, log;
    private ScrollView logScroll;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_screencast);
        setupHeader("Screen Cast");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("cast media to your console");

        deviceView = findViewById(R.id.cast_device);
        log = findViewById(R.id.cast_log);
        logScroll = findViewById(R.id.cast_log_scroll);
        progress = findViewById(R.id.cast_progress);

        View elf = findViewById(R.id.btn_cast_elf);
        View receiver = findViewById(R.id.btn_cast_receiver);
        Button browser = findViewById(R.id.btn_cast_browser);
        Button media = findViewById(R.id.btn_cast_media);
        Theme.styleButton(this, elf);
        Theme.styleButton(this, receiver);
        Theme.styleButton(this, browser);
        Theme.styleButton(this, media);
        ((android.widget.ImageView) findViewById(R.id.elf_icon))
                .setColorFilter(Theme.textPrimary(this));
        ((android.widget.ImageView) findViewById(R.id.receiver_icon))
                .setColorFilter(Theme.textPrimary(this));

        elf.setOnClickListener(v -> sendCastElf());
        receiver.setOnClickListener(v -> discoverRenderers());
        browser.setOnClickListener(v -> open(new Intent(this, CastBrowserActivity.class)));
        media.setOnClickListener(v -> pickMedia());

        setupFab();
    }

    /** FAB (shared with every casting screen): opens the OSD remote. */
    private void setupFab() {
        View fab = findViewById(R.id.fab_osd);
        if (fab == null) return;
        fab.setBackground(Theme.fab(this));
        fab.setOnClickListener(v -> open(new Intent(this, CastOsdActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        String name = Prefs.getCastRendererName(this);
        deviceView.setText(name.isEmpty() ? "No cast device selected" : "\u25CF " + name);
        deviceView.setTextColor(name.isEmpty()
                ? Theme.textSecondary(this) : Theme.accent(this));
        refreshLog();
        CastState.setLogListener(this::refreshLog);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CastState.setLogListener(null);
    }

    private void refreshLog() {
        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder();
            synchronized (CastState.logs()) {
                for (String l : CastState.logs()) sb.append(l).append("\n");
            }
            log.setText(sb.toString());
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void logLine(String s) { CastState.log(s); }

    // ---------- inject PSPlay ELF ----------

    private void sendCastElf() {
        String ip = Prefs.getActiveIp(this);
        if (ip == null || ip.isEmpty()) {
            Toast.makeText(this, "No console selected", Toast.LENGTH_SHORT).show();
            return;
        }
        String elf = Prefs.getCastElf(this);
        try {
            InputStream in = getAssets().openFd(elf).createInputStream();
            long size = getAssets().openFd(elf).getLength();
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(0);
            logLine("> sending " + elf + " (" + PayloadManager.formatSize(size) + ") to " + ip);
            TcpPayloadSender.sendStream(ip, Prefs.getElfPort(this), in, size, new TcpPayloadSender.Callback() {
                @Override public void onProgress(int percent) { progress.setProgress(percent); }
                @Override public void onSuccess() {
                    progress.setVisibility(View.GONE);
                    logLine("< PSPlay ELF injected — player starting on the console");
                    Toast.makeText(ScreenCastActivity.this, "PSPlay sent", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String message) {
                    progress.setVisibility(View.GONE);
                    logLine("! inject error: " + message);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "ELF missing in assets: " + elf, Toast.LENGTH_LONG).show();
            logLine("! ELF missing in assets: " + elf);
        }
    }

    // ---------- DLNA receiver discovery ----------

    private void discoverRenderers() {
        logLine("> SSDP discovery…");
        DlnaDiscovery discovery = new DlnaDiscovery();
        List<DlnaDiscovery.Renderer> found = new ArrayList<>();
        List<String> names = new ArrayList<>();
        android.widget.ArrayAdapter<String> adp = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, names);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("DLNA receivers")
                .setAdapter(adp, (d, which) -> {
                    if (which >= found.size()) return;
                    DlnaDiscovery.Renderer sel = found.get(which);
                    Prefs.setCastRenderer(ScreenCastActivity.this, sel.name, sel.location, sel.controlUrl);
                    CastState.rendererName = sel.name;
                    CastState.controlUrl = sel.controlUrl;
                    discovery.stop();
                    onResume();
                    logLine("< receiver connected: " + sel.name + " (" + sel.host + ")");
                })
                .setNegativeButton("Close", (d, w) -> discovery.stop())
                .create();
        dialog.show();
        discovery.search(this, new DlnaDiscovery.Callback() {
            @Override public void onFound(DlnaDiscovery.Renderer r) {
                found.add(r);
                adp.add(r.name + "  (" + r.host + ")");
                adp.notifyDataSetChanged();
                logLine("< found: " + r.name + " (" + r.host + ")");
            }
            @Override public void onDone(List<DlnaDiscovery.Renderer> all) {
                if (all.isEmpty()) {
                    adp.add("(no receiver found — inject PS Play.elf first)");
                    logLine("! no DLNA receiver found on the network");
                }
            }
        });
    }

    // ---------- storage media: serve locally + cast directly ----------

    private void pickMedia() {
        if (Prefs.getCastRendererControl(this).isEmpty()) {
            Toast.makeText(this, "Select a receiver first", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        String[] mimes = {"video/*", "audio/*"};
        i.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        startActivityForResult(i, REQ_MEDIA);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_MEDIA) askSubtitles(data.getData());
        else if (req == REQ_SUBS && pendingVideo != null) castLocalMedia(pendingVideo, data.getData());
    }

    private void askSubtitles(Uri video) {
        pendingVideo = video;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Subtitles")
                .setMessage("Add an external subtitle file (.srt / .ass / .vtt)?")
                .setPositiveButton("Choose file", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_SUBS);
                })
                .setNegativeButton("No subtitles", (d, w) -> castLocalMedia(video, null))
                .setOnCancelListener(d -> castLocalMedia(video, null))
                .show();
    }

    private void castLocalMedia(Uri video, Uri subs) {
        try { getContentResolver().takePersistableUriPermission(video, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) {}
        if (subs != null) {
            try { getContentResolver().takePersistableUriPermission(subs, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) {}
        }

        String name = queryName(video);
        String subName = subs == null ? null : queryName(subs);
        String mime = getContentResolver().getType(video);
        if (mime == null) mime = "video/mp4";

        final String fMime = mime;
        final Uri fSubs = subs;
        try {
            CastState.server = new HttpFileServer();
            int port = CastState.server.start(0, path -> {
                if (path != null && path.startsWith("/subs") && fSubs != null)
                    return providerFor(fSubs, subMime(subName));
                return providerFor(video, fMime);
            });
            String phoneIp = localIp();
            if (phoneIp == null) {
                Toast.makeText(this, "Phone IP not found", Toast.LENGTH_LONG).show();
                logLine("! phone IP not found — check Wi-Fi");
                return;
            }
            String base = "http://" + phoneIp + ":" + port;
            String url = base + "/" + Uri.encode(name);
            String subUrl = subs == null ? null : base + "/subs/" + Uri.encode(subName);
            String control = Prefs.getCastRendererControl(this);
            CastState.rendererName = Prefs.getCastRendererName(this);
            CastState.controlUrl = control;
            CastState.mediaTitle = name;
            CastState.mediaUrl = url;
            CastState.subtitleUrl = subUrl;
            CastState.subtitlesOn = subUrl != null;
            logLine("> casting " + name + (subs != null ? " (+ subtitles)" : ""));
            new DlnaController(control).play(url, name, subUrl, new DlnaController.Result() {
                @Override public void onOk() {
                    CastState.playing = true;
                    CastService.start(ScreenCastActivity.this);
                    logLine("< playing on " + CastState.rendererName);
                    open(new Intent(ScreenCastActivity.this, CastOsdActivity.class));
                }
                @Override public void onError(String msg) {
                    logLine("! playback error: " + msg);
                    Toast.makeText(ScreenCastActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            logLine("! error: " + e.getMessage());
        }
    }

    private HttpFileServer.StreamProvider providerFor(Uri uri, String mime) {
        return new HttpFileServer.StreamProvider() {
            @Override public long length() {
                try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                        if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
                    }
                } catch (Exception ignored) {}
                return -1;
            }
            @Override public InputStream open() throws Exception {
                return getContentResolver().openInputStream(uri);
            }
            @Override public String mime() { return mime; }
            @Override public String fileName() { return queryName(uri); }
        };
    }

    private static String subMime(String name) {
        if (name == null) return "text/srt";
        String l = name.toLowerCase();
        if (l.endsWith(".ass") || l.endsWith(".ssa")) return "text/x-ssa";
        if (l.endsWith(".vtt")) return "text/vtt";
        return "text/srt";
    }

    static String localIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                java.net.NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a instanceof java.net.Inet4Address)
                        return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String queryName(Uri uri) {
        String name = null;
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return name == null ? "media" : name;
    }
}
