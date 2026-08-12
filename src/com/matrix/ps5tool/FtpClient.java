package com.matrix.ps5tool;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/** Minimal pure-Java FTP client (passive): LIST/NLST/RETR/STOR/DELE/RNFR/RNTO/MKD/CWD/PWD/SIZE. */
public class FtpClient {

    public static class FtpEntry {
        public String name;
        public boolean isDir;
        public long size;
        public FtpEntry(String n, boolean d, long s) { name = n; isDir = d; size = s; }
    }

    private Socket ctrl;
    private BufferedReader in;
    private OutputStream out;
    private String host;

    public synchronized void connect(String host, int port, String user, String pass) throws Exception {
        this.host = host;
        ctrl = new Socket();
        ctrl.connect(new InetSocketAddress(host, port), 6000);
        ctrl.setSoTimeout(15000);
        in = new BufferedReader(new InputStreamReader(ctrl.getInputStream()));
        out = ctrl.getOutputStream();
        expect(220);
        cmd("USER " + (user == null || user.isEmpty() ? "anonymous" : user));
        int code = readCode();
        if (code == 331) { cmd("PASS " + (pass == null ? "" : pass)); code = readCode(); }
        if (code / 100 != 2) throw new Exception("FTP login failed: " + code);
        cmd("TYPE I");
        readCode();
    }

    public synchronized void quit() {
        try { cmd("QUIT"); } catch (Exception ignored) { }
        try { ctrl.close(); } catch (Exception ignored) { }
    }

    private void cmd(String c) throws Exception {
        out.write((c + "\r\n").getBytes("UTF-8"));
        out.flush();
    }

    private String readReply() throws Exception {
        StringBuilder sb = new StringBuilder();
        String line = in.readLine();
        if (line == null) throw new Exception("FTP connection closed");
        sb.append(line);
        if (line.length() >= 4 && line.charAt(3) == '-') {
            String prefix = line.substring(0, 3) + " ";
            String l;
            while ((l = in.readLine()) != null) { sb.append('\n').append(l); if (l.startsWith(prefix)) break; }
        }
        return sb.toString();
    }

    private int readCode() throws Exception {
        String r = readReply();
        try { return Integer.parseInt(r.substring(0, 3)); }
        catch (Exception e) { throw new Exception("Bad FTP reply: " + r); }
    }

    private int expect(int want) throws Exception {
        int code = readCode();
        if (code != want) throw new Exception("FTP error " + code + " (wanted " + want + ")");
        return code;
    }

    private Socket data() throws Exception {
        cmd("PASV");
        String r = readReply();
        int code = Integer.parseInt(r.substring(0, 3));
        if (code / 100 != 2) throw new Exception("PASV failed: " + r);
        int a = r.indexOf('('), b = r.indexOf(')', a);
        if (a < 0 || b < 0) throw new Exception("Bad PASV reply: " + r);
        String[] parts = r.substring(a + 1, b).split(",");
        if (parts.length < 6) throw new Exception("Bad PASV reply: " + r);
        String ip = parts[0].trim() + "." + parts[1].trim() + "." + parts[2].trim() + "." + parts[3].trim();
        int port = (Integer.parseInt(parts[4].trim()) << 8) + Integer.parseInt(parts[5].trim());
        if (ip.startsWith("0.")) ip = host;
        Socket s = new Socket();
        s.connect(new InetSocketAddress(ip, port), 6000);
        s.setSoTimeout(30000);
        return s;
    }

    public synchronized String pwd() throws Exception {
        cmd("PWD");
        String r = readReply();
        int a = r.indexOf('"'), b = r.lastIndexOf('"');
        return (a >= 0 && b > a) ? r.substring(a + 1, b) : "/";
    }

    public synchronized void cwd(String path) throws Exception {
        cmd("CWD " + path);
        int code = readCode();
        if (code / 100 != 2) throw new Exception("CWD failed: " + code);
    }

    public synchronized List<FtpEntry> list(String path) throws Exception {
        List<FtpEntry> out2 = new ArrayList<>();
        Socket d = data();
        cmd("LIST" + (path == null || path.isEmpty() ? "" : " " + path));
        int code = readCode();
        if (code == 550) { closeQuiet(d); readCodeSafe(); return out2; }
        if (code / 100 != 1) { closeQuiet(d); throw new Exception("LIST failed: " + code); }
        try (BufferedReader dr = new BufferedReader(new InputStreamReader(d.getInputStream()))) {
            String line;
            while ((line = dr.readLine()) != null) {
                FtpEntry e = parseListLine(line);
                if (e != null && !e.name.equals(".") && !e.name.equals("..")) out2.add(e);
            }
        }
        readCodeSafe();
        return out2;
    }

    public synchronized List<FtpEntry> nlist(String path) throws Exception {
        List<FtpEntry> out2 = new ArrayList<>();
        Socket d = data();
        cmd("NLST" + (path == null || path.isEmpty() ? "" : " " + path));
        int code = readCode();
        if (code / 100 != 1) { closeQuiet(d); return out2; }
        try (BufferedReader dr = new BufferedReader(new InputStreamReader(d.getInputStream()))) {
            String line;
            while ((line = dr.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.equals(".") && !line.equals(".."))
                    out2.add(new FtpEntry(line, false, -1));
            }
        }
        readCodeSafe();
        return out2;
    }

    private void readCodeSafe() { try { readCode(); } catch (Exception ignored) { } }
    private static void closeQuiet(Socket s) { try { s.close(); } catch (Exception ignored) { } }

    private static FtpEntry parseListLine(String line) {
        try {
            if (line.isEmpty()) return null;
            boolean dir = line.charAt(0) == 'd';
            if (line.charAt(0) != 'd' && line.charAt(0) != '-' && line.charAt(0) != 'l') return null;
            String[] f = line.split("\\s+", 9);
            if (f.length < 9) return null;
            long size = -1;
            try { size = Long.parseLong(f[4]); } catch (Exception ignored) { }
            String name = f[8];
            int li = name.indexOf(" -> ");
            if (li > 0) name = name.substring(0, li);
            return new FtpEntry(name, dir, size);
        } catch (Exception e) { return null; }
    }

    public synchronized byte[] retr(String path) throws Exception {
        Socket d = data();
        cmd("RETR " + path);
        int code = readCode();
        if (code / 100 != 1) { closeQuiet(d); throw new Exception("RETR failed: " + code); }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream di = d.getInputStream()) {
            byte[] buf = new byte[32768];
            int r;
            while ((r = di.read(buf)) != -1) bos.write(buf, 0, r);
        }
        readCodeSafe();
        return bos.toByteArray();
    }

    public interface Progress { void onProgress(long sent, long total); }

    public synchronized void stor(String path, byte[] data, Progress pr) throws Exception {
        Socket d = data();
        cmd("STOR " + path);
        int code = readCode();
        if (code / 100 != 1) { closeQuiet(d); throw new Exception("STOR failed: " + code); }
        try (OutputStream dos = d.getOutputStream()) {
            int off = 0, chunk = 32768;
            while (off < data.length) {
                int n = Math.min(chunk, data.length - off);
                dos.write(data, off, n);
                off += n;
                if (pr != null) pr.onProgress(off, data.length);
            }
        }
        readCodeSafe();
    }

    public synchronized long size(String path) {
        try {
            cmd("SIZE " + path);
            String r = readReply();
            if (r.startsWith("213")) return Long.parseLong(r.substring(4).trim());
        } catch (Exception ignored) { }
        return -1;
    }

    public synchronized void dele(String path) throws Exception {
        cmd("DELE " + path);
        int code = readCode();
        if (code / 100 != 2) throw new Exception("DELE failed: " + code);
    }

    public synchronized void rmd(String path) throws Exception {
        cmd("RMD " + path);
        int code = readCode();
        if (code / 100 != 2) throw new Exception("RMD failed: " + code);
    }

    public synchronized void mkd(String path) throws Exception {
        cmd("MKD " + path);
        int code = readCode();
        if (code / 100 != 2) throw new Exception("MKD failed: " + code);
    }

    public synchronized void rename(String from, String to) throws Exception {
        cmd("RNFR " + from);
        int code = readCode();
        if (code != 350) throw new Exception("RNFR failed: " + code);
        cmd("RNTO " + to);
        code = readCode();
        if (code / 100 != 2) throw new Exception("RNTO failed: " + code);
    }
}
