package com.matrix.ps5tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Locale;

/** Tiny multi-file HTTP server with Range support for streaming phone media. */
public class HttpFileServer {

    public interface StreamProvider {
        long length() throws Exception;
        InputStream open() throws Exception;
        String mime();
        String fileName();
    }

    /** Maps a request path ("/video.mp4") to a stream, or null for 404. */
    public interface Router {
        StreamProvider resolve(String path);
    }

    private ServerSocket server;
    private Thread thread;
    private volatile boolean running;

    public int start(int port, StreamProvider provider) throws IOException {
        return start(port, path -> provider);
    }

    public int start(int port, Router router) throws IOException {
        stop();
        server = new ServerSocket(port);
        running = true;
        thread = new Thread(() -> {
            while (running) {
                try { handle(server.accept(), router); }
                catch (SocketException se) { return; }
                catch (Exception ignored) { }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return server.getLocalPort();
    }

    private void handle(Socket s, Router router) {
        new Thread(() -> {
            try {
                s.setSoTimeout(15000);
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                String line = readLine(in);
                if (line == null) { s.close(); return; }
                // parse "GET /path HTTP/1.1" and resolve the stream
                String reqPath = "/";
                try {
                    String[] parts = line.split(" ");
                    if (parts.length >= 2) reqPath = java.net.URLDecoder.decode(parts[1], "UTF-8");
                } catch (Exception ignored) { }
                StreamProvider p = router.resolve(reqPath);
                if (p == null) {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .getBytes("ISO-8859-1"));
                    s.close();
                    return;
                }
                String range = null, h;
                while ((h = readLine(in)) != null && !h.isEmpty())
                    if (h.toLowerCase(Locale.US).startsWith("range:")) range = h.substring(6).trim();
                long total = p.length();
                long start = 0, end = total - 1;
                boolean partial = false;
                if (range != null && range.startsWith("bytes=")) {
                    String[] ab = range.substring(6).split("-");
                    try {
                        if (!ab[0].isEmpty()) start = Long.parseLong(ab[0]);
                        if (ab.length > 1 && !ab[1].isEmpty()) end = Long.parseLong(ab[1]);
                        if (end >= total) end = total - 1;
                        partial = start > 0 || end < total - 1;
                    } catch (Exception ignored) { }
                }
                long len = end - start + 1;
                StringBuilder resp = new StringBuilder();
                resp.append(partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n");
                resp.append("Content-Type: ").append(p.mime()).append("\r\n");
                resp.append("Accept-Ranges: bytes\r\n");
                resp.append("Content-Length: ").append(len).append("\r\n");
                if (partial) resp.append("Content-Range: bytes ").append(start).append("-").append(end).append("/").append(total).append("\r\n");
                resp.append("Connection: close\r\n\r\n");
                out.write(resp.toString().getBytes("ISO-8859-1"));
                if (!line.startsWith("HEAD")) {
                    try (InputStream src = p.open()) {
                        skip(src, start);
                        byte[] buf = new byte[65536];
                        long left = len;
                        while (left > 0) {
                            int r = src.read(buf, 0, (int) Math.min(buf.length, left));
                            if (r == -1) break;
                            out.write(buf, 0, r);
                            left -= r;
                        }
                        out.flush();
                    }
                }
                s.close();
            } catch (Exception e) { try { s.close(); } catch (Exception ignored) { } }
        }).start();
    }

    private static void skip(InputStream in, long n) throws IOException {
        long left = n;
        while (left > 0) {
            long sk = in.skip(left);
            if (sk <= 0) { if (in.read() == -1) break; sk = 1; }
            left -= sk;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c, prev = -1;
        while ((c = in.read()) != -1) {
            if (c == '\n' && prev == '\r') {
                byte[] arr = b.toByteArray();
                return new String(arr, 0, arr.length - 1, "ISO-8859-1");
            }
            b.write(c);
            prev = c;
        }
        return b.size() == 0 ? null : b.toString("ISO-8859-1");
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
    }
}
