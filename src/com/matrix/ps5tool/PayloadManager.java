package com.matrix.ps5tool;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Payload storage: files in <files>/payloads + JSON index in prefs. */
public class PayloadManager {

    public static class Payload {
        public String name, path;
        public long size, time;
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("payloads", Context.MODE_PRIVATE);
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "payloads");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static List<Payload> getPayloads(Context c) {
        List<Payload> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(p(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Payload pl = new Payload();
                pl.name = o.getString("name");
                pl.path = o.getString("path");
                pl.size = o.optLong("size");
                pl.time = o.optLong("time");
                if (new File(pl.path).exists()) out.add(pl);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static void addPayload(Context c, String name, byte[] data) throws Exception {
        File f = new File(dir(c), System.currentTimeMillis() + "_" + name);
        try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(data); }
        JSONArray arr = new JSONArray(p(c).getString("list", "[]"));
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("path", f.getAbsolutePath());
        o.put("size", data.length);
        o.put("time", System.currentTimeMillis());
        arr.put(o);
        p(c).edit().putString("list", arr.toString()).apply();
    }

    public static void renamePayload(Context c, Payload pl, String newName) {
        try {
            JSONArray arr = new JSONArray(p(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.getString("path").equals(pl.path)) { o.put("name", newName); break; }
            }
            p(c).edit().putString("list", arr.toString()).apply();
            pl.name = newName;
        } catch (Exception ignored) { }
    }

    public static void deletePayload(Context c, Payload pl) {
        new File(pl.path).delete();
        try {
            JSONArray arr = new JSONArray(p(c).getString("list", "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!o.getString("path").equals(pl.path)) keep.put(o);
            }
            p(c).edit().putString("list", keep.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static byte[] readPayload(Context c, Payload pl) throws Exception {
        File f = new File(pl.path);
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int off = 0, r;
            while (off < b.length && (r = fis.read(b, off, b.length - off)) != -1) off += r;
        }
        return b;
    }

    public static Payload findByPath(Context c, String path) {
        for (Payload pl : getPayloads(c)) if (pl.path.equals(path)) return pl;
        return null;
    }

    public static String formatSize(long s) {
        if (s >= 1048576) return String.format("%.1f MB", s / 1048576.0);
        if (s >= 1024) return String.format("%.1f KB", s / 1024.0);
        return s + " B";
    }
}
