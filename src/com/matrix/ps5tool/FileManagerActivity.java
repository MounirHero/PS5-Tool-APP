package com.matrix.ps5tool;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** FTP file manager: material icon buttons, create file/folder, upload, refresh. */
public class FileManagerActivity extends BaseActivity {

    private static final int REQ_PICK = 42;

    private ListView list;
    private TextView empty, pathView;
    private ProgressBar progress;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FtpClient ftp;
    private String cwd = "/";
    private String clipboardPath, clipboardName;
    private boolean clipboardIsDir;
    private final List<FtpClient.FtpEntry> entries = new ArrayList<>();
    private BaseAdapter adapter;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_filemanager);
        setupHeader("File Manager");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("browse console files via FTP");

        list = findViewById(R.id.fm_list);
        empty = findViewById(R.id.fm_empty);
        pathView = findViewById(R.id.fm_path);
        progress = findViewById(R.id.fm_progress);
        empty.setTextColor(Theme.textHint(this));
        pathView.setTextColor(Theme.textPrimary(this));
        pathView.setOnLongClickListener(v -> {
            if (clipboardPath == null) {
                Toast.makeText(this, "Nothing to paste — long-press a file and choose Copy/Move first",
                        Toast.LENGTH_SHORT).show();
            } else pasteDialog(cwd);
            return true;
        });

        ImageView up = findViewById(R.id.fm_up);
        ImageView refresh = findViewById(R.id.fm_refresh);
        ImageView upload = findViewById(R.id.fm_upload);
        ImageView create = findViewById(R.id.fm_create);
        Theme.styleIconButton(this, up);
        Theme.styleIconButton(this, refresh);
        Theme.styleIconButton(this, upload);
        Theme.styleIconButton(this, create);
        Theme.tintAccent(this, up);
        Theme.tintAccent(this, refresh);
        Theme.tintAccent(this, upload);
        Theme.tintAccent(this, create);

        up.setOnClickListener(v -> goUp());
        refresh.setOnClickListener(v -> load(cwd));
        upload.setOnClickListener(v -> pickUpload());
        create.setOnClickListener(v -> createDialog());

        adapter = new BaseAdapter() {
            @Override public int getCount() { return entries.size(); }
            @Override public Object getItem(int i) { return entries.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(FileManagerActivity.this)
                        .inflate(R.layout.item_file, parent, false);
                FtpClient.FtpEntry e = entries.get(i);
                Theme.styleItem(FileManagerActivity.this, v.findViewById(R.id.item_root));
                ImageView icon = v.findViewById(R.id.file_icon);
                icon.setImageResource(e.isDir ? R.drawable.ic_folder : R.drawable.ic_file);
                Theme.tintAccent(FileManagerActivity.this, icon);
                TextView name = v.findViewById(R.id.file_name);
                TextView size = v.findViewById(R.id.file_size);
                name.setText(e.name);
                name.setTextColor(Theme.textPrimary(FileManagerActivity.this));
                size.setText(e.isDir ? "" : PayloadManager.formatSize(e.size));
                size.setTextColor(Theme.textSecondary(FileManagerActivity.this));
                return v;
            }
        };
        list.setAdapter(adapter);

        list.setOnItemClickListener((p, v, pos, id) -> {
            FtpClient.FtpEntry e = entries.get(pos);
            if (busy) return;
            if (e.isDir) load(join(cwd, e.name));
            else fileMenu(e);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            fileMenu(entries.get(pos));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ftp == null) connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ftp != null) ftp.quit();
    }

    private void connect() {
        String ip = Prefs.getActiveIp(this);
        if (ip == null || ip.isEmpty()) {
            empty.setText("No console selected. Go back to the Scan page.");
            return;
        }
        busy = true;
        empty.setText("Connecting FTP to " + ip + "…");
        new Thread(() -> {
            try {
                ftp = new FtpClient();
                ftp.connect(ip, Prefs.getFtpPort(this), Prefs.getFtpUser(this), Prefs.getFtpPass(this));
                ui.post(() -> { busy = false; load("/"); });
            } catch (Exception e) {
                ftp = null;
                ui.post(() -> {
                    empty.setText("FTP unreachable.\nStart the FTP server on the PS5 (e.g. ftpsrv).");
                    busy = false;
                });
            }
        }, "ftp-connect").start();
    }

    private void load(String path) {
        if (ftp == null || busy) return;
        busy = true;
        empty.setText("Loading…");
        new Thread(() -> {
            try {
                List<FtpClient.FtpEntry> res = ftp.list(path);
                cwd = path;
                ui.post(() -> {
                    entries.clear();
                    entries.addAll(res);
                    adapter.notifyDataSetChanged();
                    pathView.setText(cwd);
                    empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
                    empty.setText("(empty folder)");
                    busy = false;
                });
            } catch (Exception e) {
                ui.post(() -> {
                    empty.setVisibility(View.VISIBLE);
                    empty.setText("Error: " + e.getMessage());
                    busy = false;
                });
            }
        }, "ftp-list").start();
    }

    private void goUp() {
        if (busy || "/".equals(cwd)) return;
        String up = cwd.substring(0, cwd.lastIndexOf('/'));
        if (up.isEmpty()) up = "/";
        load(up);
    }

    private static String join(String dir, String name) {
        return ("/".equals(dir) ? "" : dir) + "/" + name;
    }

    // ---------- file actions ----------

    private void fileMenu(FtpClient.FtpEntry e) {
        java.util.List<String> opts = new java.util.ArrayList<>();
        if (e.isDir) opts.add("Open"); else opts.add("Download");
        opts.add("Copy");
        opts.add("Move");
        if (clipboardPath != null && e.isDir) opts.add("Paste / Move in this folder");
        opts.add("Rename");
        opts.add("Delete");
        String[] items = opts.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(e.name)
                .setItems(items, (d, which) -> {
                    String sel = items[which];
                    if ("Open".equals(sel)) load(join(cwd, e.name));
                    else if ("Download".equals(sel)) download(e);
                    else if ("Copy".equals(sel) || "Move".equals(sel)) {
                        clipboardPath = join(cwd, e.name);
                        clipboardName = e.name;
                        clipboardIsDir = e.isDir;
                        Toast.makeText(this, sel + " ready — long-press the path bar or a folder to paste",
                                Toast.LENGTH_LONG).show();
                    }
                    else if ("Paste / Move in this folder".equals(sel)) pasteDialog(join(cwd, e.name));
                    else if ("Rename".equals(sel)) renameDialog(e);
                    else delete(e);
                })
                .show();
    }

    // ---------- copy / move ----------

    private void pasteDialog(String destDir) {
        String[] items = {"Paste (copy) here", "Move in this folder"};
        new AlertDialog.Builder(this)
                .setTitle(clipboardName + "  \u2192  " + destDir)
                .setItems(items, (d, which) -> doPaste(destDir, which == 1))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doPaste(String destDir, boolean move) {
        final String src = clipboardPath;
        final String dest = join(destDir, clipboardName);
        if (src.equals(dest)) { Toast.makeText(this, "Same location", Toast.LENGTH_SHORT).show(); return; }
        busy = true;
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        new Thread(() -> {
            try {
                if (move) {
                    ftp.rename(src, dest);
                    clipboardPath = null;
                } else {
                    copyRecursive(src, dest, clipboardIsDir);
                }
                ui.post(() -> {
                    Toast.makeText(this, move ? "Moved to " + destDir : "Copied to " + destDir,
                            Toast.LENGTH_SHORT).show();
                    progress.setVisibility(View.GONE);
                    progress.setIndeterminate(false);
                    busy = false;
                    load(cwd);
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    progress.setVisibility(View.GONE);
                    progress.setIndeterminate(false);
                    busy = false;
                });
            }
        }, "ftp-paste").start();
    }

    /** FTP has no server-side copy: recursive download + re-upload. */
    private void copyRecursive(String src, String dest, boolean isDir) throws Exception {
        if (!isDir) {
            byte[] data = ftp.retr(src);
            ftp.stor(dest, data, null);
            return;
        }
        try { ftp.mkd(dest); } catch (Exception ignored) {}
        for (FtpClient.FtpEntry e : ftp.list(src))
            copyRecursive(src + "/" + e.name, dest + "/" + e.name, e.isDir);
    }

    private void download(FtpClient.FtpEntry e) {
        if (busy) return;
        busy = true;
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        new Thread(() -> {
            try {
                byte[] data = ftp.retr(join(cwd, e.name));
                File out = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), e.name);
                try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(data); }
                ui.post(() -> {
                    Toast.makeText(this, "Saved to Download/" + e.name, Toast.LENGTH_LONG).show();
                    progress.setVisibility(View.GONE);
                    progress.setIndeterminate(false);
                    busy = false;
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    progress.setVisibility(View.GONE);
                    progress.setIndeterminate(false);
                    busy = false;
                });
            }
        }, "ftp-dl").start();
    }

    private void renameDialog(FtpClient.FtpEntry e) {
        EditText input = new EditText(this);
        input.setText(e.name);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Rename")
                .setView(wrap)
                .setPositiveButton("OK", (d, w) -> {
                    String nn = input.getText().toString().trim();
                    if (nn.isEmpty() || nn.equals(e.name)) return;
                    new Thread(() -> {
                        try {
                            ftp.rename(join(cwd, e.name), join(cwd, nn));
                            ui.post(() -> load(cwd));
                        } catch (Exception ex) {
                            ui.post(() -> Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }, "ftp-ren").start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void delete(FtpClient.FtpEntry e) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + e.name + "?")
                .setPositiveButton("Delete", (d, w) -> new Thread(() -> {
                    try {
                        if (e.isDir) ftp.rmd(join(cwd, e.name));
                        else ftp.dele(join(cwd, e.name));
                        ui.post(() -> load(cwd));
                    } catch (Exception ex) {
                        ui.post(() -> Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }, "ftp-del").start())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- upload ----------

    private void pickUpload() {
        if (ftp == null) { Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null && ftp != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            String name = "upload.bin";
            try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }
            } catch (Exception ignored) {}
            final String fname = name;
            busy = true;
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(0);
            new Thread(() -> {
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close();
                    byte[] all = out.toByteArray();
                    ftp.stor(join(cwd, fname), all, (sent, total) ->
                            ui.post(() -> progress.setProgress((int) (sent * 100 / Math.max(1, total)))));
                    ui.post(() -> {
                        Toast.makeText(this, "Upload complete: " + fname, Toast.LENGTH_SHORT).show();
                        progress.setVisibility(View.GONE);
                        busy = false;
                        load(cwd);
                    });
                } catch (Exception ex) {
                    ui.post(() -> {
                        Toast.makeText(this, "Upload error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        progress.setVisibility(View.GONE);
                        busy = false;
                    });
                }
            }, "ftp-up").start();
        }
    }

    // ---------- create ----------

    private void createDialog() {
        if (ftp == null) { Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show(); return; }
        String[] items = {"Create file", "Create folder"};
        new AlertDialog.Builder(this)
                .setTitle("Create")
                .setItems(items, (d, which) -> nameDialog(which == 1))
                .show();
    }

    private void nameDialog(boolean isDir) {
        EditText input = new EditText(this);
        input.setHint(isDir ? "Folder name" : "File name");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(isDir ? "Create folder" : "Create file")
                .setView(wrap)
                .setPositiveButton("Create", (d, w) -> {
                    String nn = input.getText().toString().trim();
                    if (nn.isEmpty()) return;
                    new Thread(() -> {
                        try {
                            if (isDir) ftp.mkd(join(cwd, nn));
                            else ftp.stor(join(cwd, nn), new byte[0], null);
                            ui.post(() -> load(cwd));
                        } catch (Exception ex) {
                            ui.post(() -> Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }, "ftp-mkd").start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
