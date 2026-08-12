package com.matrix.ps5tool;

import android.content.Context;
import android.content.SharedPreferences;

/** Central settings: manual IP has highest priority, then selected console. */
public class Prefs {
    private static final String NAME = "ps5tool_prefs";

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static void setManualIp(Context c, String ip) { p(c).edit().putString("manual_ip", ip == null ? "" : ip.trim()).apply(); }
    public static String getManualIp(Context c) { return p(c).getString("manual_ip", ""); }
    public static void setSelectedIp(Context c, String ip) { p(c).edit().putString("selected_ip", ip == null ? "" : ip).apply(); }
    public static String getSelectedIp(Context c) { return p(c).getString("selected_ip", ""); }

    public static String getActiveIp(Context c) {
        String m = getManualIp(c);
        if (m != null && !m.isEmpty()) return m;
        return getSelectedIp(c);
    }

    public static int getElfPort(Context c) { return p(c).getInt("elf_port", 9021); }
    public static void setElfPort(Context c, int v) { p(c).edit().putInt("elf_port", v).apply(); }
    public static int getFtpPort(Context c) { return p(c).getInt("ftp_port", 2121); }
    public static void setFtpPort(Context c, int v) { p(c).edit().putInt("ftp_port", v).apply(); }
    public static String getFtpUser(Context c) { return p(c).getString("ftp_user", "anonymous"); }
    public static void setFtpUser(Context c, String v) { p(c).edit().putString("ftp_user", v).apply(); }
    public static String getFtpPass(Context c) { return p(c).getString("ftp_pass", ""); }
    public static void setFtpPass(Context c, String v) { p(c).edit().putString("ftp_pass", v).apply(); }
    public static int getLinuxPort(Context c) { return p(c).getInt("linux_port", 9080); }
    public static void setLinuxPort(Context c, int v) { p(c).edit().putInt("linux_port", v).apply(); }
    public static int getCastPort(Context c) { return p(c).getInt("cast_port", 8080); }
    public static void setCastPort(Context c, int v) { p(c).edit().putInt("cast_port", v).apply(); }
    public static int getGameApiPort(Context c) { return p(c).getInt("game_api_port", 8080); }
    public static void setGameApiPort(Context c, int v) { p(c).edit().putInt("game_api_port", v).apply(); }

    public static boolean isLight(Context c) { return "light".equals(p(c).getString("theme", "dark")); }
    public static void setLight(Context c, boolean light) { p(c).edit().putString("theme", light ? "light" : "dark").apply(); }

    private static final int[] ACCENTS = {
        0xff0070d1, 0xffff0000, 0xff00c853, 0xff9b59b6, 0xffff6600, 0xff00e5ff, 0xffff1493
    };
    private static final String[] ACCENT_NAMES = {"PS Blue", "Red", "Green", "Purple", "Orange", "Cyan", "Pink"};

    public static int getAccentIndex(Context c) { return p(c).getInt("accent_color", 0); }
    public static void setAccentIndex(Context c, int i) { p(c).edit().putInt("accent_color", i).apply(); }
    public static int getAccent(Context c) {
        int i = getAccentIndex(c);
        return ACCENTS[(i >= 0 && i < ACCENTS.length) ? i : 0];
    }
    public static int[] getAccents() { return ACCENTS; }
    public static String[] getAccentNames() { return ACCENT_NAMES; }

    public static String getCastElf(Context c) { return p(c).getString("cast_elf", "PSPlay-1.5.elf"); }
    public static void setCastElf(Context c, String v) { p(c).edit().putString("cast_elf", v).apply(); }
    public static String getChiakiPkg(Context c) { return p(c).getString("chiaki_pkg", "com.metallic.chiaki"); }
    public static void setChiakiPkg(Context c, String v) { p(c).edit().putString("chiaki_pkg", v).apply(); }
    public static String getGamePaths(Context c) { return p(c).getString("game_paths", "/user/app,/system_ex/app"); }
    public static void setGamePaths(Context c, String v) { p(c).edit().putString("game_paths", v).apply(); }
    public static String getGameUploadDir(Context c) { return p(c).getString("game_upload_dir", "/mnt/usb0"); }
    public static void setGameUploadDir(Context c, String v) { p(c).edit().putString("game_upload_dir", v).apply(); }
    public static int getWebDefaultPort(Context c) { return p(c).getInt("web_default_port", 8080); }
    public static void setWebDefaultPort(Context c, int v) { p(c).edit().putInt("web_default_port", v).apply(); }
    public static int getAutoloaderDelay(Context c) { return p(c).getInt("autoloader_delay", 5); }
    public static void setAutoloaderDelay(Context c, int v) { p(c).edit().putInt("autoloader_delay", v).apply(); }
    public static String getLinuxLoaderElf(Context c) { return p(c).getString("linux_loader_elf", "ps5-linux-loader_2.4.elf"); }
    public static void setLinuxLoaderElf(Context c, String v) { p(c).edit().putString("linux_loader_elf", v).apply(); }
    public static String getLinuxManagerElf(Context c) { return p(c).getString("linux_manager_elf", "ps5-linux-manager_1.2.elf"); }
    public static void setLinuxManagerElf(Context c, String v) { p(c).edit().putString("linux_manager_elf", v).apply(); }
    public static String getFtpSrvElf(Context c) { return p(c).getString("ftpsrv_elf", "ftpsrv-ps5.elf"); }
    public static void setFtpSrvElf(Context c, String v) { p(c).edit().putString("ftpsrv_elf", v).apply(); }
    public static String getShadowmountElf(Context c) { return p(c).getString("shadowmount_elf", "shadowmountplus.elf"); }
    public static void setShadowmountElf(Context c, String v) { p(c).edit().putString("shadowmount_elf", v).apply(); }

    // --- Autoloader ---
    public static String getAutoloaderName(Context c) { return p(c).getString("autoloader_name", "Autoloader"); }
    public static void setAutoloaderName(Context c, String v) { p(c).edit().putString("autoloader_name", v).apply(); }
    public static String getAutoloaderSeq(Context c) { return p(c).getString("autoloader_seq", "[]"); }
    public static void setAutoloaderSeq(Context c, String json) { p(c).edit().putString("autoloader_seq", json).apply(); }

    // --- DLNA cast target ---
    public static String getCastRendererName(Context c) { return p(c).getString("cast_renderer_name", ""); }
    public static String getCastRendererLocation(Context c) { return p(c).getString("cast_renderer_location", ""); }
    public static String getCastRendererControl(Context c) { return p(c).getString("cast_renderer_control", ""); }
    public static void setCastRenderer(Context c, String name, String location, String control) {
        p(c).edit().putString("cast_renderer_name", name)
            .putString("cast_renderer_location", location)
            .putString("cast_renderer_control", control).apply();
    }

    // --- Haptics (touch vibration) ---
    public static boolean isHapticsOn(Context c) { return p(c).getBoolean("haptics_on", true); }
    public static void setHapticsOn(Context c, boolean on) { p(c).edit().putBoolean("haptics_on", on).apply(); }
    public static int getHapticMs(Context c) { return p(c).getInt("haptic_ms", 25); }
    public static void setHapticMs(Context c, int ms) { p(c).edit().putInt("haptic_ms", ms).apply(); }

    // --- Browser history (JSON array of {title,url}) ---
    public static String getBrowserHistory(Context c) { return p(c).getString("browser_history", "[]"); }
    public static void setBrowserHistory(Context c, String json) { p(c).edit().putString("browser_history", json).apply(); }
}
