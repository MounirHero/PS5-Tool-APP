package com.matrix.ps5tool;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSDP discovery for DLNA MediaRenderer devices.
 * Sends M-SEARCH to 239.255.255.250:1900, collects LOCATION headers,
 * then fetches each device description XML to get friendlyName and
 * the AVTransport control URL.
 */
public class DlnaDiscovery {

    public static class Renderer {
        public String name;
        public String location;
        public String controlUrl;
        public String host;
    }

    public interface Callback {
        void onFound(Renderer r);
        void onDone(List<Renderer> all);
    }

    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;

    private volatile boolean running;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public void search(final Context ctx, final Callback cb) {
        running = true;
        new Thread(() -> {
            Map<String, Renderer> found = new LinkedHashMap<>();
            DatagramSocket sock = null;
            try {
                String req = "M-SEARCH * HTTP/1.1\r\n"
                        + "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n"
                        + "MAN: \"ssdp:discover\"\r\n"
                        + "MX: 2\r\n"
                        + "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n"
                        + "\r\n";
                byte[] data = req.getBytes("UTF-8");

                sock = new DatagramSocket(null);
                sock.setReuseAddress(true);
                sock.bind(new InetSocketAddress(0));
                sock.setSoTimeout(1000);
                InetAddress group = InetAddress.getByName(SSDP_ADDR);

                for (int i = 0; i < 3; i++) {
                    sock.send(new DatagramPacket(data, data.length, group, SSDP_PORT));
                    Thread.sleep(150);
                }

                long end = System.currentTimeMillis() + 6000;
                byte[] buf = new byte[2048];
                while (running && System.currentTimeMillis() < end) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        sock.receive(p);
                        String resp = new String(p.getData(), 0, p.getLength(), "UTF-8");
                        String loc = header(resp, "LOCATION");
                        if (loc != null && !found.containsKey(loc)) {
                            Renderer r = new Renderer();
                            r.location = loc;
                            r.host = p.getAddress().getHostAddress();
                            found.put(loc, r);
                            resolve(r);
                            if (r.name == null) r.name = r.host;
                            final Renderer fr = r;
                            ui.post(() -> cb.onFound(fr));
                        }
                    } catch (java.net.SocketTimeoutException ignored) {
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (sock != null) try { sock.close(); } catch (Exception ignored) {}
            }
            List<Renderer> list = new ArrayList<>(found.values());
            ui.post(() -> cb.onDone(list));
        }, "ssdp").start();
    }

    public void stop() { running = false; }

    private static String header(String resp, String name) {
        for (String line : resp.split("\r\n")) {
            int i = line.indexOf(':');
            if (i > 0 && line.substring(0, i).trim().equalsIgnoreCase(name))
                return line.substring(i + 1).trim();
        }
        return null;
    }

    private void resolve(Renderer r) {
        try {
            String xml = Http.get(r.location, 4000);
            if (xml == null) return;
            r.name = tag(xml, "friendlyName");
            int idx = xml.indexOf("AVTransport");
            if (idx >= 0) {
                int svcStart = xml.lastIndexOf("<service>", idx);
                int svcEnd = xml.indexOf("</service>", idx);
                if (svcStart >= 0 && svcEnd > svcStart) {
                    String svc = xml.substring(svcStart, svcEnd);
                    String ctrl = tag(svc, "controlURL");
                    if (ctrl != null) {
                        if (ctrl.startsWith("http")) r.controlUrl = ctrl;
                        else {
                            java.net.URL u = new java.net.URL(r.location);
                            if (!ctrl.startsWith("/")) ctrl = "/" + ctrl;
                            r.controlUrl = u.getProtocol() + "://" + u.getHost()
                                    + (u.getPort() > 0 ? ":" + u.getPort() : "") + ctrl;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    static String tag(String xml, String t) {
        String o = "<" + t + ">", c = "</" + t + ">";
        int a = xml.indexOf(o), b = xml.indexOf(c);
        if (a < 0 || b < a) return null;
        return xml.substring(a + o.length(), b).trim();
    }

    static class Http {
        static String get(String url, int timeoutMs) {
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(timeoutMs);
                c.setReadTimeout(timeoutMs);
                c.setRequestProperty("User-Agent", "PS5Tool/5.0 UPnP/1.1");
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                java.io.InputStream in = c.getInputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                in.close();
                return out.toString("UTF-8");
            } catch (Exception e) {
                return null;
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }
}
