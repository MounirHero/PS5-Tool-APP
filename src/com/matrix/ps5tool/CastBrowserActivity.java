package com.matrix.ps5tool;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Cast browser: URL bar, page progress, history, media sniffer with format labels, direct cast. */
public class CastBrowserActivity extends BaseActivity {

    private WebView web;
    private View home;
    private EditText urlBar;
    private ProgressBar progress;
    private ListView historyList, mediaList;
    private TextView mediaLabel;

    private static final int REQ_FILE_CHOOSER = 52;
    private ValueCallback<Uri[]> fileCallback;

    private final List<String[]> history = new ArrayList<>(); // [title, url]
    private final List<String[]> media = new ArrayList<>();   // [format, url]
    private BaseAdapter historyAdapter, mediaAdapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_cast_browser);
        setupHeader("Cast Browser");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("find videos and cast them");

        web = findViewById(R.id.browser_webview);
        home = findViewById(R.id.browser_home);
        web.setVisibility(View.GONE);
        home.setVisibility(View.VISIBLE);
        urlBar = findViewById(R.id.browser_url);
        progress = findViewById(R.id.browser_progress);
        historyList = findViewById(R.id.history_list);
        mediaList = findViewById(R.id.media_list);
        mediaLabel = findViewById(R.id.media_label);
        ImageView go = findViewById(R.id.browser_go);
        TextView historyLabel = findViewById(R.id.history_label);
        historyLabel.setTextColor(Theme.textSecondary(this));
        mediaLabel.setTextColor(Theme.textSecondary(this));

        urlBar.setTextColor(Theme.textPrimary(this));
        urlBar.setHintTextColor(Theme.textHint(this));
        Theme.styleItem(this, urlBar);
        Theme.styleIconButton(this, go);
        Theme.tintAccent(this, go);

        // --- history ---
        loadHistory();
        historyAdapter = new BaseAdapter() {
            @Override public int getCount() { return history.size(); }
            @Override public Object getItem(int i) { return history.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(CastBrowserActivity.this)
                        .inflate(R.layout.item_history, parent, false);
                String[] h = history.get(i);
                Theme.styleItem(CastBrowserActivity.this, v.findViewById(R.id.item_root));
                TextView t = v.findViewById(R.id.history_title);
                TextView u = v.findViewById(R.id.history_url);
                t.setText(h[0]);
                t.setTextColor(Theme.textPrimary(CastBrowserActivity.this));
                u.setText(h[1]);
                u.setTextColor(Theme.textSecondary(CastBrowserActivity.this));
                return v;
            }
        };
        historyList.setAdapter(historyAdapter);
        historyList.setOnItemClickListener((p, v, pos, id) -> navigate(history.get(pos)[1]));

        // --- media list ---
        mediaAdapter = new BaseAdapter() {
            @Override public int getCount() { return media.size(); }
            @Override public Object getItem(int i) { return media.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(CastBrowserActivity.this)
                        .inflate(R.layout.item_media, parent, false);
                String[] m = media.get(i);
                Theme.styleItem(CastBrowserActivity.this, v.findViewById(R.id.item_root));
                TextView fmt = v.findViewById(R.id.media_format);
                TextView url = v.findViewById(R.id.media_url);
                fmt.setText(m[0]);
                fmt.setTextColor(Theme.accent(CastBrowserActivity.this));
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setCornerRadius(6 * getResources().getDisplayMetrics().density);
                g.setColor(android.graphics.Color.TRANSPARENT);
                g.setStroke((int) (1 * getResources().getDisplayMetrics().density), Theme.accent(CastBrowserActivity.this));
                fmt.setBackground(g);
                url.setText(m[1]);
                url.setTextColor(Theme.textPrimary(CastBrowserActivity.this));
                ImageView cast = v.findViewById(R.id.media_cast);
                Theme.styleIconButton(CastBrowserActivity.this, cast);
                Theme.tintAccent(CastBrowserActivity.this, cast);
                cast.setOnClickListener(x -> castMedia(m));
                return v;
            }
        };
        mediaList.setAdapter(mediaAdapter);
        mediaList.setOnItemClickListener((p, v, pos, id) -> castMedia(media.get(pos)));

        // --- webview ---
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                urlBar.setText(url);
                CastState.log("> visited " + url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                addHistory(view.getTitle() == null ? url : view.getTitle(), url);
            }
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                String fmt = detectFormat(u);
                if (fmt != null && !alreadyHave(u)) {
                    CastState.log("< media found [" + fmt + "] " + u);
                    runOnUiThread(() -> {
                        media.add(0, new String[]{fmt, u});
                        mediaAdapter.notifyDataSetChanged();
                        mediaLabel.setVisibility(View.VISIBLE);
                        mediaList.setVisibility(View.VISIBLE);
                    });
                }
                return super.shouldInterceptRequest(view, req);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i;
                try {
                    i = params.createIntent();
                } catch (Exception e) {
                    i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                }
                try {
                    startActivityForResult(i, REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        go.setOnClickListener(v -> navigate(urlBar.getText().toString().trim()));
        urlBar.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate(urlBar.getText().toString().trim());
                return true;
            }
            return false;
        });

        // FAB (shared with every casting screen): opens the OSD remote
        View fab = findViewById(R.id.fab_osd);
        if (fab != null) {
            fab.setBackground(Theme.fab(this));
            fab.setOnClickListener(v -> open(new Intent(this, CastOsdActivity.class)));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE_CHOOSER && fileCallback != null) {
            Uri[] results = null;
            if (res == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++)
                        results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    private boolean alreadyHave(String u) {
        for (String[] m : media) if (m[1].equals(u)) return true;
        return false;
    }

    static String detectFormat(String u) {
        String l = u.toLowerCase();
        int q = l.indexOf('?');
        if (q > 0) l = l.substring(0, q);
        if (l.endsWith(".m3u8")) return "HLS";
        if (l.endsWith(".mpd")) return "DASH";
        if (l.endsWith(".mp4")) return "MP4";
        if (l.endsWith(".webm")) return "WEBM";
        if (l.endsWith(".mkv")) return "MKV";
        if (l.endsWith(".avi")) return "AVI";
        if (l.endsWith(".mov")) return "MOV";
        if (l.endsWith(".mp3")) return "MP3";
        if (l.endsWith(".aac")) return "AAC";
        if (l.endsWith(".ogg") || l.endsWith(".oga")) return "OGG";
        if (l.endsWith(".flac")) return "FLAC";
        if (l.endsWith(".wav")) return "WAV";
        return null;
    }

    private void navigate(String input) {
        if (input.isEmpty()) return;
        String url = input;
        if (!input.contains("://")) {
            if (input.contains(".") && !input.contains(" ")) url = "https://" + input;
            else url = "https://www.google.com/search?q=" + Uri.encode(input);
        }
        home.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        media.clear();
        mediaAdapter.notifyDataSetChanged();
        mediaLabel.setVisibility(View.GONE);
        mediaList.setVisibility(View.GONE);
        web.loadUrl(url);
    }

    // --- direct cast: pick renderer if needed, then play + close browser -> OSD ---

    private void castMedia(String[] m) {
        String control = Prefs.getCastRendererControl(this);
        if (control.isEmpty()) {
            Toast.makeText(this, "No cast device: configure Cast Setup in Screen Cast",
                    Toast.LENGTH_LONG).show();
            return;
        }
        CastState.rendererName = Prefs.getCastRendererName(this);
        CastState.controlUrl = control;
        CastState.mediaTitle = titleFrom(m[1]);
        CastState.mediaUrl = m[1];
        CastState.log("> casting [" + m[0] + "] " + CastState.mediaTitle);
        new DlnaController(control).play(m[1], CastState.mediaTitle, new DlnaController.Result() {
            @Override public void onOk() {
                CastState.playing = true;
                CastService.start(CastBrowserActivity.this);
                CastState.log("< playing on " + CastState.rendererName);
                // open the OSD remote on top — back returns to this browser screen
                open(new Intent(CastBrowserActivity.this, CastOsdActivity.class));
            }
            @Override public void onError(String msg) {
                CastState.log("! playback error: " + msg);
                Toast.makeText(CastBrowserActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String titleFrom(String url) {
        try {
            String path = new java.net.URL(url).getPath();
            int i = path.lastIndexOf('/');
            String n = i >= 0 ? path.substring(i + 1) : path;
            return n.isEmpty() ? "Web media" : java.net.URLDecoder.decode(n, "UTF-8");
        } catch (Exception e) {
            return "Web media";
        }
    }

    // --- history persistence ---

    private void loadHistory() {
        history.clear();
        try {
            JSONArray arr = new JSONArray(Prefs.getBrowserHistory(this));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                history.add(new String[]{o.getString("title"), o.getString("url")});
            }
        } catch (Exception ignored) {}
    }

    private void addHistory(String title, String url) {
        if (url == null || url.startsWith("about:")) return;
        for (int i = history.size() - 1; i >= 0; i--)
            if (history.get(i)[1].equals(url)) history.remove(i);
        history.add(0, new String[]{title, url});
        while (history.size() > 20) history.remove(history.size() - 1);
        historyAdapter.notifyDataSetChanged();
        try {
            JSONArray arr = new JSONArray();
            for (String[] h : history) {
                JSONObject o = new JSONObject();
                o.put("title", h[0]);
                o.put("url", h[1]);
                arr.put(o);
            }
            Prefs.setBrowserHistory(this, arr.toString());
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (web.getVisibility() == View.VISIBLE && web.canGoBack()) web.goBack();
        else if (web.getVisibility() == View.VISIBLE) {
            web.stopLoading();
            web.setVisibility(View.GONE);
            home.setVisibility(View.VISIBLE);
        } else super.onBackPressed();
    }
}
