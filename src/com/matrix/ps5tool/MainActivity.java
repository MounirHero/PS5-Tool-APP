package com.matrix.ps5tool;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Functions menu: original untinted icons, transparent rows, outlined IP -> scan. */
public class MainActivity extends BaseActivity {

    private static class Func {
        String name, desc;
        int icon;
        Class<?> target;
        Func(String n, String d, int i, Class<?> t) { name = n; desc = d; icon = i; target = t; }
    }

    private final List<Func> funcs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        setupHeader("PS5 Tool");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("functions menu");

        String ip = Prefs.getActiveIp(this);
        TextView ipText = findViewById(R.id.ip_text);
        ipText.setText(ip == null || ip.isEmpty() ? "No console selected" : ip);
        ipText.setTextColor(Theme.textPrimary(this));
        // Accent outline around the IP: tap it to return to the scan page.
        GradientDrawable outline = new GradientDrawable();
        outline.setShape(GradientDrawable.RECTANGLE);
        outline.setCornerRadius(10 * getResources().getDisplayMetrics().density);
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke((int) (1.5f * getResources().getDisplayMetrics().density), Theme.accent(this));
        ipText.setBackground(outline);
        ipText.setOnClickListener(v -> openTop(new Intent(this, ScanActivity.class)));

        funcs.add(new Func("Payloads Manager", "Send and manage payloads", R.drawable.ic_payload, PayloadActivity.class));
        funcs.add(new Func("File Manager", "Browse console files via FTP", R.drawable.ic_filemanager, FileManagerActivity.class));
        funcs.add(new Func("Games Manager", "Installed games, icons and tools", R.drawable.ic_games, GameToolActivity.class));
        funcs.add(new Func("Screen Cast", "Cast media to your console (DLNA)", R.drawable.ic_screencaster, ScreenCastActivity.class));
        funcs.add(new Func("Web Interfaces", "Saved console web pages", R.drawable.ic_web, WebInterfacesActivity.class));
        funcs.add(new Func("Linux Manager", "Boot and manage Linux", R.drawable.ic_linux, LinuxManagerActivity.class));
        funcs.add(new Func("Remote Play", "Chiaki remote play", R.drawable.ic_remote, RemotePlayActivity.class));

        ListView list = findViewById(R.id.functions_list);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return funcs.size(); }
            @Override public Object getItem(int i) { return funcs.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_function, parent, false);
                Func f = funcs.get(i);
                // Transparent row background, original icon (no tint).
                v.findViewById(R.id.item_root).setBackgroundColor(Color.TRANSPARENT);
                ImageView icon = v.findViewById(R.id.function_icon);
                icon.setImageResource(f.icon);
                icon.clearColorFilter();
                TextView name = v.findViewById(R.id.function_name);
                TextView desc = v.findViewById(R.id.function_desc);
                name.setText(f.name);
                name.setTextColor(Theme.textPrimary(MainActivity.this));
                desc.setText(f.desc);
                desc.setTextColor(Theme.textSecondary(MainActivity.this));
                return v;
            }
        });
        list.setOnItemClickListener((p, v, pos, id) ->
                open(new Intent(this, funcs.get(pos).target)));
    }
}
