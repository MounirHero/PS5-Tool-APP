package com.matrix.ps5tool;

import java.nio.charset.Charset;

/** Parses PS4/PS5 param.sfo: extracts TITLE and TITLE_ID. */
public class SfoParser {

    public static class Sfo { public String title, titleId; }

    public static Sfo parse(byte[] b) {
        try {
            if (b == null || b.length < 20) return null;
            if (b[0] != 0x00 || b[1] != 0x50 || b[2] != 0x53 || b[3] != 0x46) return null;
            int keyTable = le32(b, 8), dataTable = le32(b, 12), entries = le32(b, 16);
            Sfo out = new Sfo();
            for (int i = 0; i < entries; i++) {
                int base = 20 + i * 16;
                if (base + 16 > b.length) break;
                int keyOff = keyTable + le16(b, base);
                int len = le32(b, base + 4);
                int dataOff = dataTable + le32(b, base + 12);
                String key = cstr(b, keyOff);
                if (dataOff >= b.length) continue;
                if ("TITLE".equals(key)) out.title = decode(b, dataOff, len);
                else if ("TITLE_ID".equals(key)) out.titleId = decode(b, dataOff, len);
            }
            return out;
        } catch (Exception e) { return null; }
    }

    private static String decode(byte[] b, int off, int len) {
        int end = off + Math.max(0, Math.min(len, b.length - off));
        while (end > off && b[end - 1] == 0) end--;
        return new String(b, off, end - off, Charset.forName("UTF-8"));
    }

    private static String cstr(byte[] b, int off) {
        int end = off;
        while (end < b.length && b[end] != 0) end++;
        return new String(b, off, end - off, Charset.forName("US-ASCII"));
    }

    private static int le16(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8); }
    private static int le32(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16) | ((b[o + 3] & 0xff) << 24);
    }
}
