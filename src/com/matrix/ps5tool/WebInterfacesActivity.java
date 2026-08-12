package com.matrix.ps5tool;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/** Web interfaces: saved console web pages, FAB to add, tap to open. */
public class WebInterfacesActivity extends BaseActivity {

    private ListView list;
    private TextView empty;
    private List<WebInterfaceManager.WebInterface> items = new java.util.ArrayList<>();
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_webinterfaces);
        setupHeader("Web Interfaces");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("saved console web pages");

        list = findViewById(R.id.web_list);
        empty = findViewById(R.id.web_empty);
        empty.setTextColor(Theme.textHint(this));

        ImageView fab = findViewById(R.id.fab_add);
        fab.setBackground(Theme.fab(this));
        fab.setOnClickListener(v -> addDialog());

        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(WebInterfacesActivity.this)
                        .inflate(R.layout.item_webinterface, parent, false);
                WebInterfaceManager.WebInterface w = items.get(i);
                Theme.styleItem(WebInterfacesActivity.this, v.findViewById(R.id.item_root));
                TextView name = v.findViewById(R.id.web_name);
                TextView url = v.findViewById(R.id.web_url);
                name.setText(w.name);
                name.setSelected(true);
                name.setTextColor(Theme.textPrimary(WebInterfacesActivity.this));
                url.setText(WebInterfaceManager.url(WebInterfacesActivity.this, w));
                url.setTextColor(Theme.textSecondary(WebInterfacesActivity.this));
                ImageView del = v.findViewById(R.id.web_delete);
                Theme.tintAccent(WebInterfacesActivity.this, del);
                del.setOnClickListener(x -> {
                    WebInterfaceManager.delete(WebInterfacesActivity.this, i);
                    refresh();
                });
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            WebInterfaceManager.WebInterface w = items.get(pos);
            Intent i = new Intent(this, WebViewActivity.class);
            i.putExtra("url", WebInterfaceManager.url(this, w));
            i.putExtra("title", w.name);
            open(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        items = WebInterfaceManager.getAll(this);
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addDialog() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad / 2, pad, 0);

        EditText name = new EditText(this);
        name.setHint("Name (e.g. etaHEN)");
        wrap.addView(name);

        EditText port = new EditText(this);
        port.setHint("Port");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText(String.valueOf(Prefs.getWebDefaultPort(this)));
        wrap.addView(port);

        new AlertDialog.Builder(this)
                .setTitle("Add Web Interface")
                .setMessage("Console IP: " + Prefs.getActiveIp(this))
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    int pt = Prefs.getWebDefaultPort(this);
                    try { pt = Integer.parseInt(port.getText().toString().trim()); } catch (Exception ignored) {}
                    WebInterfaceManager.add(this, n, Prefs.getActiveIp(this), pt);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
