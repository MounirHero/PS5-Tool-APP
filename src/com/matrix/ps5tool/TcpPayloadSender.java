package com.matrix.ps5tool;

import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** TCP ELF sender with real 0-100% progress reporting. */
public class TcpPayloadSender {

    public interface Callback {
        void onProgress(int percent);
        void onSuccess();
        void onError(String message);
    }

    public static void send(String ip, int port, byte[] data, Callback cb) {
        Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, port), 5000);
                s.setSoTimeout(15000);
                OutputStream out = s.getOutputStream();
                int total = data.length, sent = 0, chunk = 32768, lastPct = -1;
                while (sent < total) {
                    int n = Math.min(chunk, total - sent);
                    out.write(data, sent, n);
                    sent += n;
                    int pct = (int) ((sent * 100L) / total);
                    if (pct != lastPct) { lastPct = pct; int p = pct; ui.post(() -> cb.onProgress(p)); }
                }
                out.flush();
                ui.post(cb::onSuccess);
            } catch (Exception e) {
                ui.post(() -> cb.onError(e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        }).start();
    }

    public static void sendStream(String ip, int port, InputStream in, long size, Callback cb) {
        Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try (Socket s = new Socket(); InputStream src = in) {
                s.connect(new InetSocketAddress(ip, port), 5000);
                s.setSoTimeout(15000);
                OutputStream out = s.getOutputStream();
                byte[] buf = new byte[32768];
                long sent = 0;
                int r, lastPct = -1;
                while ((r = src.read(buf)) != -1) {
                    out.write(buf, 0, r);
                    sent += r;
                    int pct = size > 0 ? (int) ((sent * 100L) / size) : 0;
                    if (pct > 100) pct = 100;
                    if (pct != lastPct) { lastPct = pct; int p = pct; ui.post(() -> cb.onProgress(p)); }
                }
                out.flush();
                ui.post(cb::onSuccess);
            } catch (Exception e) {
                ui.post(() -> cb.onError(e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        }).start();
    }
}
