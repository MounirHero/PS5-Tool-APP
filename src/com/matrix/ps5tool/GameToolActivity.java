package com.matrix.ps5tool;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Games manager: FTP game scan with icon0.png icons, upload to usb0, long-press actions. */
public class GameToolActivity extends BaseActivity {

    private static final int REQ_FILE = 43;
    private static final int REQ_TREE = 44;

    private ListView list;
    private TextView empty, status;
    private ProgressBar progress;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<GameScanner.GameInfo> games = new ArrayList<>();
    private BaseAdapter adapter;
    private boolean scanning;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_gametool);
        setupHeader("Games Manager");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("installed games \u2022 long-press for actions");

        list = findViewById(R.id.games_list);
        empty = findViewById(R.id.games_empty);
        status = findViewById(R.id.games_status);
        progress = findViewById(R.id.games_progress);
        empty.setTextColor(Theme.textHint(this));
        status.setTextColor(Theme.textSecondary(this));

        ImageView upload = findViewById(R.id.games_upload);
        ImageView refresh = findViewById(R.id.games_refresh);
        Theme.styleIconButton(this, upload);
        Theme.styleIconButton(this, refresh);
        Theme.tintAccent(this, upload);
        Theme.tintAccent(this, refresh);

        upload.setOnClickListener(v -> uploadDialog());
        refresh.setOnClickListener(v -> scan());

        adapter = new BaseAdapter() {
            @Override public int getCount() { return games.size(); }
            @Override public Object getItem(int i) { return games.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(GameToolActivity.this)
                        .inflate(R.layout.item_game, parent, false);
                GameScanner.GameInfo g = games.get(i);
                Theme.styleItem(GameToolActivity.this, v.findViewById(R.id.item_root));
                ImageView icon = v.findViewById(R.id.game_icon);
                if (g.iconPath != null && new java.io.File(g.iconPath).exists()) {
                    icon.setImageBitmap(BitmapFactory.decodeFile(g.iconPath));
                    icon.clearColorFilter();
                } else {
                    icon.setImageResource(R.drawable.ic_gamepad);
                    Theme.tintAccent(GameToolActivity.this, icon);
                }
                TextView title = v.findViewById(R.id.game_title);
                TextView id = v.findViewById(R.id.game_id);
                title.setText(g.title == null ? g.titleId : g.title);
                title.setSelected(true);
                title.setTextColor(Theme.textPrimary(GameToolActivity.this));
                id.setText(g.titleId + (g.size > 0 ? "  \u2022  " + PayloadManager.formatSize(g.size) : ""));
                id.setTextColor(Theme.textSecondary(GameToolActivity.this));
                return v;
            }
        };
        list.setAdapter(adapter);

        list.setOnItemLongClickListener((p, v, pos, id) -> {
            gameMenu(games.get(pos));
            return true;
        });
        list.setOnItemClickListener((p, v, pos, id) -> gameMenu(games.get(pos)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (games.isEmpty() && !scanning) scan();
    }

    private void scan() {
        String ip = Prefs.getActiveIp(this);
        if (ip == null || ip.isEmpty()) {
            empty.setText("No console selected. Go back to the Scan page.");
            return;
        }
        if (scanning) return;
        scanning = true;
        empty.setVisibility(View.VISIBLE);
        empty.setText("Scanning games…");
        GameScanner.scan(this, new GameScanner.Callback() {
            @Override public void onProgress(String s) {
                ui.post(() -> status.setText(s));
            }
            @Override public void onResult(List<GameScanner.GameInfo> res) {
                scanning = false;
                games.clear();
                games.addAll(res);
                adapter.notifyDataSetChanged();
                status.setText(res.size() + " games found");
                empty.setVisibility(res.isEmpty() ? View.VISIBLE : View.GONE);
                empty.setText("No games found.\nCheck the paths in Settings.");
            }
            @Override public void onError(String message) {
                scanning = false;
                status.setText("");
                empty.setVisibility(View.VISIBLE);
                empty.setText("Error: " + message + "\nStart the FTP server on the PS5.");
            }
        });
    }

    // ---------- actions ----------

    private void gameMenu(GameScanner.GameInfo g) {
        String[] items = {"Launch game", "Game info", "Mount with ShadowMount"};
        new AlertDialog.Builder(this)
                .setTitle(g.title == null ? g.titleId : g.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) launchGame(g);
                    else if (which == 1) showInfo(g);
                    else sendShadowMount();
                })
                .show();
    }

    /** Send the ShadowMount payload: mounts games uploaded to usb0/usb1. */
    private void sendShadowMount() {
        String ip = Prefs.getActiveIp(this);
        if (ip == null || ip.isEmpty()) {
            Toast.makeText(this, "No console selected", Toast.LENGTH_SHORT).show();
            return;
        }
        String elf = Prefs.getShadowmountElf(this);
        try {
            java.io.InputStream in = getAssets().openFd(elf).createInputStream();
            long size = getAssets().openFd(elf).getLength();
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(0);
            status.setText("Sending " + elf + "…");
            TcpPayloadSender.sendStream(ip, Prefs.getElfPort(this), in, size, new TcpPayloadSender.Callback() {
                @Override public void onProgress(int percent) { progress.setProgress(percent); }
                @Override public void onSuccess() {
                    progress.setVisibility(View.GONE);
                    status.setText("ShadowMount started — usb0/usb1 games mounted");
                    Toast.makeText(GameToolActivity.this, "ShadowMount inviato", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String message) {
                    progress.setVisibility(View.GONE);
                    status.setText("Error: " + message);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "ELF missing in assets: " + elf, Toast.LENGTH_LONG).show();
        }
    }

    private void launchGame(GameScanner.GameInfo g) {
        // Best effort: console-side game API (itemzflow/etaHEN style) on the configured port.
        String ip = Prefs.getActiveIp(this);
        int port = Prefs.getGameApiPort(this);
        String url = "http://" + ip + ":" + port + "/launch/" + g.titleId;
        new Thread(() -> {
            String r = DlnaDiscovery.Http.get(url, 4000);
            ui.post(() -> {
                if (r != null) Toast.makeText(this, "Avvio richiesto: " + g.titleId, Toast.LENGTH_SHORT).show();
                else Toast.makeText(this,
                        "No response from the game API (" + ip + ":" + port + ").\n" +
                        "Configure the API port in Settings.",
                        Toast.LENGTH_LONG).show();
            });
        }, "game-launch").start();
    }

    private void showInfo(GameScanner.GameInfo g) {
        new AlertDialog.Builder(this)
                .setTitle(g.title == null ? g.titleId : g.title)
                .setMessage("Title ID: " + g.titleId
                        + "\nPath: " + g.path
                        + (g.size > 0 ? "\nSize: " + PayloadManager.formatSize(g.size) : ""))
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------- upload ----------

    private void uploadDialog() {
        String[] items = {"Upload file", "Upload folder"};
        new AlertDialog.Builder(this)
                .setTitle("Upload to console")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        startActivityForResult(i, REQ_FILE);
                    } else {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        startActivityForResult(i, REQ_TREE);
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_FILE) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++)
                    uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) uris.add(data.getData());
            if (!uris.isEmpty()) destDialog(uris, null);
        } else if (req == REQ_TREE && data.getData() != null) {
            destDialog(null, data.getData());
        }
    }

    private void destDialog(List<Uri> files, Uri tree) {
        EditText input = new EditText(this);
        input.setText(Prefs.getGameUploadDir(this));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Destination directory")
                .setView(wrap)
                .setPositiveButton("Upload", (d, w) -> {
                    String dest = input.getText().toString().trim();
                    if (dest.isEmpty()) dest = "/mnt/usb0";
                    Prefs.setGameUploadDir(this, dest);
                    if (files != null) uploadFiles(files, dest);
                    else uploadTree(tree, dest);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void uploadFiles(List<Uri> uris, String dest) {
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        new Thread(() -> {
            FtpClient ftp = new FtpClient();
            try {
                ftp.connect(Prefs.getActiveIp(this), Prefs.getFtpPort(this),
                        Prefs.getFtpUser(this), Prefs.getFtpPass(this));
                int done = 0;
                for (Uri uri : uris) {
                    String name = queryName(uri);
                    final String un = name;
                    ui.post(() -> status.setText("Uploading " + un));
                    InputStream in = getContentResolver().openInputStream(uri);
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close();
                    byte[] all = out.toByteArray();
                    ftp.stor(dest + "/" + name, all, (sent, total) ->
                            ui.post(() -> progress.setProgress((int) (sent * 100 / Math.max(1, total)))));
                    done++;
                    int fd = done, tot = uris.size();
                    ui.post(() -> status.setText("Uploaded " + fd + "/" + tot));
                }
                ftp.quit();
                ui.post(() -> {
                    Toast.makeText(this, "Upload complete to " + dest, Toast.LENGTH_SHORT).show();
                    progress.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    Toast.makeText(this, "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    progress.setVisibility(View.GONE);
                });
            } finally {
                try { ftp.quit(); } catch (Exception ignored) {}
            }
        }, "game-up").start();
    }

    private void uploadTree(Uri tree, String dest) {
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        new Thread(() -> {
            FtpClient ftp = new FtpClient();
            try {
                ftp.connect(Prefs.getActiveIp(this), Prefs.getFtpPort(this),
                        Prefs.getFtpUser(this), Prefs.getFtpPass(this));
                String rootName = queryName(tree);
                uploadDirRecursive(ftp, tree,
                        android.provider.DocumentsContract.getTreeDocumentId(tree),
                        dest + "/" + safe(rootName));
                ui.post(() -> {
                    Toast.makeText(this, "Folder uploaded to " + dest, Toast.LENGTH_SHORT).show();
                    progress.setVisibility(View.GONE);
                    progress.setIndeterminate(false);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    Toast.makeText(this, "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    progress.setVisibility(View.GONE);
                    progress.setIndeterminate(false);
                });
            } finally {
                try { ftp.quit(); } catch (Exception ignored) {}
            }
        }, "game-tree").start();
    }

    private void uploadDirRecursive(FtpClient ftp, Uri treeUri, String parentId, String remoteDir) throws Exception {
        try { ftp.mkd(remoteDir); } catch (Exception ignored) {}
        Uri childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        List<String[]> docs = new ArrayList<>(); // [docId, name, mime]
        try (android.database.Cursor c = getContentResolver().query(childrenUri,
                new String[]{
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null)) {
            while (c != null && c.moveToNext()) docs.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
        }
        for (String[] doc : docs) {
            Uri docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, doc[0]);
            boolean isDir = android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(doc[2]);
            if (isDir) {
                uploadDirRecursive(ftp, treeUri, doc[0], remoteDir + "/" + safe(doc[1]));
            } else {
                final String dn = doc[1];
                ui.post(() -> status.setText("Uploading " + dn));
                InputStream in = getContentResolver().openInputStream(docUri);
                if (in == null) continue;
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                ftp.stor(remoteDir + "/" + safe(doc[1]), out.toByteArray(), null);
            }
        }
    }

    private static String safe(String name) {
        return name == null ? "unnamed" : name.replace("/", "_");
    }

    private String queryName(Uri uri) {
        String name = null;
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return name == null ? "file" : name;
    }
}
