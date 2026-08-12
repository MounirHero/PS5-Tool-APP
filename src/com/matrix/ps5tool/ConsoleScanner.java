package com.matrix.ps5tool;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Continuous scanner: parallel port probes + Sony OUI ARP check + hostname. */
public class ConsoleScanner {

    public static class ConsoleInfo {
        public String name, ip, mac;
        public boolean elfOpen, ftpOpen;
        public ConsoleInfo(String name, String ip, String mac) { this.name = name; this.ip = ip; this.mac = mac; }
    }

    public interface Callback { void onResults(List<ConsoleInfo> consoles); }

    private static final String[] SONY_OUIS = {
        "00014A","001315","0019C5","001D0D","0024BE","04B167","08863B","0CFE45",
        "28E0F8","30F9ED","408805","4C3C16","58C5CB","5C96FE","64B853","70704C",
        "78843C","78C881","84D63F","904DC5","9494D7","A81010","AC9B0A","B0EE7B",
        "BC60A7","C80EE4","CC5DE8","D45D64","D8D43C","E8088B","F86FC1","FCF152"
    };

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public void start(Context ctx, Callback cb) {
        stop();
        running.set(true);
        Context app = ctx.getApplicationContext();
        thread = new Thread(() -> {
            while (running.get()) {
                try {
                    List<ConsoleInfo> found = scanOnce(app);
                    ui.post(() -> { if (running.get()) cb.onResults(found); });
                    Thread.sleep(2500);
                } catch (InterruptedException ie) { return; }
                  catch (Throwable ignored) { }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
        if (thread != null) thread.interrupt();
    }

    private List<ConsoleInfo> scanOnce(Context ctx) {
        String subnet = getSubnet();
        if (subnet == null) return Collections.emptyList();
        int elfPort = Prefs.getElfPort(ctx);
        int ftpPort = Prefs.getFtpPort(ctx);
        Map<String, String> arp = readArp();
        Map<String, ConsoleInfo> found = Collections.synchronizedMap(new LinkedHashMap<>());
        ExecutorService pool = Executors.newFixedThreadPool(64);
        for (int i = 1; i <= 254; i++) {
            final String ip = subnet + "." + i;
            pool.execute(() -> {
                boolean elf = probe(ip, elfPort, 220);
                boolean ftp = elf || probe(ip, ftpPort, 220);
                String mac = arp.get(ip);
                boolean sonyMac = mac != null && isSony(mac);
                String host = null;
                if (elf || ftp || sonyMac) {
                    try { host = InetAddress.getByName(ip).getHostName(); } catch (Exception ignored) { }
                }
                boolean nameMatch = host != null && !host.equals(ip) &&
                        (host.toLowerCase().contains("ps5") || host.toLowerCase().contains("playstation"));
                if (elf || ftp || sonyMac || nameMatch) {
                    ConsoleInfo ci = new ConsoleInfo("PS5", ip, mac);
                    ci.elfOpen = elf; ci.ftpOpen = ftp;
                    found.put(ip, ci);
                }
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(6, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        return new ArrayList<>(found.values());
    }

    private static boolean probe(String ip, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Exception e) { return false; }
    }

    private static boolean isSony(String mac) {
        String clean = mac.replace(":", "").replace("-", "").toUpperCase();
        for (String o : SONY_OUIS) if (clean.startsWith(o)) return true;
        return false;
    }

    private static Map<String, String> readArp() {
        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\\s+");
                if (f.length >= 4 && f[0].matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    if (!"00:00:00:00:00:00".equals(f[3])) map.put(f[0], f[3]);
                }
            }
        } catch (Exception ignored) { }
        return map;
    }

    static String getSubnet() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) {
                        String h = a.getHostAddress();
                        if (h != null && (h.startsWith("192.168.") || h.startsWith("10.") || h.startsWith("172.")))
                            return h.substring(0, h.lastIndexOf('.'));
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}
