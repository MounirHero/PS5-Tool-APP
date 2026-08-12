package com.matrix.ps5tool;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** FTP game scan: app dirs + param.sfo titles + icon0.png caching. */
public class GameScanner {

    private static int rank(String root) {
        if (root.startsWith("/user/app")) return 0;
        if (root.startsWith("/system_ex/app")) return 1;
        return 2;
    }

    public static class GameInfo {
        public String title, titleId, path, iconPath;
        public long size = -1;
    }

    public interface Callback {
        void onProgress(String status);
        void onResult(List<GameInfo> games);
        void onError(String message);
    }

    public static void scan(Context ctx, Callback cb) {
        Handler ui = new Handler(Looper.getMainLooper());
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            List<GameInfo> games = new ArrayList<>();
            FtpClient ftp = new FtpClient();
            try {
                String ip = Prefs.getActiveIp(app);
                if (ip == null || ip.isEmpty()) throw new Exception("No PS5 IP set");
                ui.post(() -> cb.onProgress("Connecting FTP " + ip + ":" + Prefs.getFtpPort(app)));
                ftp.connect(ip, Prefs.getFtpPort(app), Prefs.getFtpUser(app), Prefs.getFtpPass(app));

                File iconDir = new File(app.getFilesDir(), "gameicons");
                if (!iconDir.exists()) iconDir.mkdirs();

                String[] roots = Prefs.getGamePaths(app).split(",");
                // scan /user/app first, then /system_ex/app, then any custom path
                Arrays.sort(roots, (a, b) -> rank(a.trim()) - rank(b.trim()));
                Set<String> seen = new HashSet<>();
                for (String r0 : roots) {
                    final String root = r0.trim();
                    if (root.isEmpty()) continue;
                    try {
                        ui.post(() -> cb.onProgress("Listing " + root));
                        List<FtpClient.FtpEntry> entries = ftp.list(root);
                        for (FtpClient.FtpEntry e : entries) {
                            if (!e.isDir) continue;
                            String dir = root + "/" + e.name;
                            GameInfo g = new GameInfo();
                            g.titleId = e.name;
                            g.path = dir;
                            try {
                                byte[] sfo = ftp.retr(dir + "/sce_sys/param.sfo");
                                SfoParser.Sfo s = SfoParser.parse(sfo);
                                if (s != null) {
                                    if (s.title != null && !s.title.isEmpty()) g.title = s.title;
                                    if (s.titleId != null && !s.titleId.isEmpty()) g.titleId = s.titleId;
                                }
                            } catch (Exception ignored) { }
                            if (g.title == null) g.title = e.name;
                            if (!seen.add(g.titleId)) continue; // skip duplicates
                            // cache icon0.png
                            File iconFile = new File(iconDir, g.titleId + ".png");
                            if (iconFile.exists()) {
                                g.iconPath = iconFile.getAbsolutePath();
                            } else {
                                try {
                                    byte[] icon = ftp.retr(dir + "/sce_sys/icon0.png");
                                    try (FileOutputStream fos = new FileOutputStream(iconFile)) { fos.write(icon); }
                                    g.iconPath = iconFile.getAbsolutePath();
                                } catch (Exception ignored) { }
                            }
                            games.add(g);
                        }
                    } catch (Exception ignored) { }
                }
                ftp.quit();
                if (games.isEmpty()) ui.post(() -> cb.onError("No games found. Is the FTP server running on the PS5?"));
                else ui.post(() -> cb.onResult(games));
            } catch (Exception e) {
                ui.post(() -> cb.onError(e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        }).start();
    }
}
