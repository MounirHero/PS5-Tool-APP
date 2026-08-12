package com.matrix.ps5tool;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saved web interfaces: name + ip + port. Blank IP = follow active console. */
public class WebInterfaceManager {

    public static class WebInterface { public String name, ip; public int port; }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("web_interfaces", Context.MODE_PRIVATE);
    }

    public static List<WebInterface> getAll(Context c) {
        List<WebInterface> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(p(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                WebInterface w = new WebInterface();
                w.name = o.getString("name");
                w.ip = o.optString("ip", "");
                w.port = o.optInt("port", Prefs.getWebDefaultPort(c));
                out.add(w);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static void add(Context c, String name, String ip, int port) {
        try {
            JSONArray arr = new JSONArray(p(c).getString("list", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("ip", ip == null ? "" : ip);
            o.put("port", port);
            arr.put(o);
            p(c).edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static void delete(Context c, int index) {
        try {
            JSONArray arr = new JSONArray(p(c).getString("list", "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < arr.length(); i++) if (i != index) keep.put(arr.get(i));
            p(c).edit().putString("list", keep.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static String url(Context c, WebInterface w) {
        String ip = (w.ip == null || w.ip.isEmpty()) ? Prefs.getActiveIp(c) : w.ip;
        return "http://" + ip + ":" + w.port;
    }
}
