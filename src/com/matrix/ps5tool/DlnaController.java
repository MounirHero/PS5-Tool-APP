package com.matrix.ps5tool;

import android.os.Handler;
import android.os.Looper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minimal DLNA AVTransport SOAP controller: SetAVTransportURI + Play/Pause/Stop/Seek.
 */
public class DlnaController {

    public interface Result {
        void onOk();
        void onError(String msg);
    }

    private static final String AVT = "urn:schemas-upnp-org:service:AVTransport:1";
    private static final String RCS = "urn:schemas-upnp-org:service:RenderingControl:1";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final String controlUrl;

    /** RenderingControl endpoint derived from the AVTransport one. */
    private String rcUrl() {
        if (controlUrl == null) return null;
        String u = controlUrl.replace("AVTransport", "RenderingControl")
                             .replace("avtransport", "renderingcontrol");
        if (u.equals(controlUrl)) {
            int i = u.lastIndexOf('/');
            if (i > 8) u = u.substring(0, i + 1) + "RenderingControl";
        }
        return u;
    }

    public DlnaController(String controlUrl) {
        this.controlUrl = controlUrl;
    }

    public void play(String uri, String title, Result res) {
        play(uri, title, null, res);
    }

    /** Play with an optional external subtitle URL (.srt/.ass/.vtt served over HTTP). */
    public void play(String uri, String title, String subUri, Result res) {
        String sub = "";
        if (subUri != null) {
            // generic extra <res> + Samsung CaptionInfoEx + PVSubtitle (broad renderer support)
            sub = "&lt;res protocolInfo=\"http-get:*:text/srt:*\"&gt;" + esc(subUri) + "&lt;/res&gt;"
                + "&lt;sec:CaptionInfoEx sec:type=\"srt\"&gt;" + esc(subUri) + "&lt;/sec:CaptionInfoEx&gt;"
                + "&lt;res protocolInfo=\"http-get:*:smi/caption:*\"&gt;" + esc(subUri) + "&lt;/res&gt;";
        }
        String meta = "&lt;DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" "
                + "xmlns:sec=\"http://www.sec.co.kr/\"&gt;"
                + "&lt;item id=\"0\" parentID=\"-1\" restricted=\"1\"&gt;"
                + "&lt;dc:title&gt;" + esc(title == null ? "PS5Tool Media" : title) + "&lt;/dc:title&gt;"
                + "&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;"
                + "&lt;res protocolInfo=\"http-get:*:video/mp4:*\"&gt;" + esc(uri) + "&lt;/res&gt;"
                + sub
                + "&lt;/item&gt;&lt;/DIDL-Lite&gt;";
        String body = "<u:SetAVTransportURI xmlns:u=\"" + AVT + "\">"
                + "<InstanceID>0</InstanceID>"
                + "<CurrentURI>" + esc(uri) + "</CurrentURI>"
                + "<CurrentURIMetaData>" + meta + "</CurrentURIMetaData>"
                + "</u:SetAVTransportURI>";
        soap("SetAVTransportURI", body, new Result() {
            @Override public void onOk() { sendPlay(res); }
            @Override public void onError(String msg) { res.onError(msg); }
        });
    }

    private void sendPlay(Result res) {
        String body = "<u:Play xmlns:u=\"" + AVT + "\">"
                + "<InstanceID>0</InstanceID><Speed>1</Speed></u:Play>";
        soap("Play", body, res);
    }

    public void resume(Result res) { sendPlay(res); }

    public void pause(Result res) {
        soap("Pause", "<u:Pause xmlns:u=\"" + AVT + "\"><InstanceID>0</InstanceID></u:Pause>", res);
    }

    public void stop(Result res) {
        soap("Stop", "<u:Stop xmlns:u=\"" + AVT + "\"><InstanceID>0</InstanceID></u:Stop>", res);
    }

    /** Seek to position, REL_TIME format hh:mm:ss */
    public void seek(int seconds, Result res) {
        String t = String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        soap("Seek", "<u:Seek xmlns:u=\"" + AVT + "\"><InstanceID>0</InstanceID>"
                + "<Unit>REL_TIME</Unit><Target>" + t + "</Target></u:Seek>", res);
    }

    public interface PositionCb { void onPosition(int relSeconds, int durSeconds); }
    public void getPosition(PositionCb cb) {
        new Thread(() -> {
            String resp = soapSync("GetPositionInfo",
                    "<u:GetPositionInfo xmlns:u=\"" + AVT + "\"><InstanceID>0</InstanceID></u:GetPositionInfo>");
            int rel = -1, dur = -1;
            if (resp != null) {
                rel = parseTime(DlnaDiscovery.tag(resp, "RelTime"));
                dur = parseTime(DlnaDiscovery.tag(resp, "TrackDuration"));
            }
            final int fr = rel, fd = dur;
            ui.post(() -> cb.onPosition(fr, fd));
        }, "dlna-pos").start();
    }

    // ---------- volume (RenderingControl) ----------

    public interface IntCb { void onValue(int v); }

    public void getVolume(IntCb cb) {
        new Thread(() -> {
            String resp = soapSync(rcUrl(), RCS, "GetVolume",
                    "<u:GetVolume xmlns:u=\"" + RCS + "\"><InstanceID>0</InstanceID>"
                            + "<Channel>Master</Channel></u:GetVolume>");
            int v = -1;
            if (resp != null) {
                try { v = Integer.parseInt(DlnaDiscovery.tag(resp, "CurrentVolume").trim()); }
                catch (Exception ignored) {}
            }
            final int fv = v;
            ui.post(() -> cb.onValue(fv));
        }, "dlna-vol").start();
    }

    public void setVolume(int v, Result res) {
        new Thread(() -> {
            String resp = soapSync(rcUrl(), RCS, "SetVolume",
                    "<u:SetVolume xmlns:u=\"" + RCS + "\"><InstanceID>0</InstanceID>"
                            + "<Channel>Master</Channel><DesiredVolume>" + v
                            + "</DesiredVolume></u:SetVolume>");
            if (resp != null) ui.post(res::onOk);
            else ui.post(() -> res.onError("Volume control not supported by the renderer"));
        }, "dlna-vol").start();
    }

    private static int parseTime(String t) {
        if (t == null) return -1;
        try {
            String[] p = t.split(":");
            if (p.length == 3)
                return Integer.parseInt(p[0]) * 3600 + Integer.parseInt(p[1]) * 60 + Integer.parseInt(p[2]);
        } catch (Exception ignored) {}
        return -1;
    }

    private void soap(String action, String body, Result res) {
        new Thread(() -> {
            String resp = soapSync(controlUrl, AVT, action, body);
            if (resp != null) ui.post(res::onOk);
            else ui.post(() -> res.onError("SOAP " + action + " failed"));
        }, "dlna-soap").start();
    }

    private String soapSync(String action, String body) {
        return soapSync(controlUrl, AVT, action, body);
    }

    private String soapSync(String url, String service, String action, String body) {
        HttpURLConnection c = null;
        try {
            String env = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                    + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                    + "<s:Body>" + body + "</s:Body></s:Envelope>";
            byte[] data = env.getBytes("UTF-8");
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            c.setRequestProperty("SOAPAction", "\"" + service + "#" + action + "\"");
            c.setRequestProperty("Content-Length", String.valueOf(data.length));
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
            int code = c.getResponseCode();
            java.io.InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            if (in != null) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                in.close();
            }
            String r = out.toString("UTF-8");
            return code < 400 ? (r.isEmpty() ? "OK" : r) : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
