package com.matrix.ps5tool;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Holds the active DLNA cast session so the OSD can control it from anywhere. */
public class CastState {
    public static String rendererName;
    public static String controlUrl;
    public static String mediaTitle;
    public static String mediaUrl;
    public static boolean playing;
    public static HttpFileServer server;
    public static String subtitleUrl;
    public static boolean subtitlesOn;
    public static int volume = -1;

    // ---- shared cast log (visible in the Screen Cast log box) ----
    public interface LogListener { void onNewLine(); }
    private static final ArrayList<String> logs = new ArrayList<>();
    private static LogListener logListener;

    public static void setLogListener(LogListener l) { logListener = l; }
    public static ArrayList<String> logs() { return logs; }

    public static void log(String msg) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + msg;
        synchronized (logs) {
            logs.add(line);
            while (logs.size() > 250) logs.remove(0);
        }
        if (logListener != null) logListener.onNewLine();
    }

    public static DlnaController controller() {
        return controlUrl == null ? null : new DlnaController(controlUrl);
    }

    public static void clear() {
        if (server != null) { server.stop(); server = null; }
        rendererName = null;
        controlUrl = null;
        mediaTitle = null;
        mediaUrl = null;
        playing = false;
        subtitleUrl = null;
        subtitlesOn = false;
        volume = -1;
    }
}
