package com.matrix.ps5tool;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/** Runtime theme engine: AMOLED dark / light white, accent outlines, translucent panels. */
public class Theme {

    public static int bg(Context c)        { return Prefs.isLight(c) ? 0xfff2f2f2 : 0xff000000; }
    /** Slightly translucent panel so the themed background shows through. */
    public static int panel(Context c)     { return Prefs.isLight(c) ? 0xd9ffffff : 0xd9141414; }
    public static int item(Context c)      { return Prefs.isLight(c) ? 0xf0fafafa : 0xf01a1a1a; }
    public static int textPrimary(Context c)   { return Prefs.isLight(c) ? 0xff111111 : 0xffffffff; }
    public static int textSecondary(Context c) { return Prefs.isLight(c) ? 0xff555555 : 0xffaaaaaa; }
    public static int textHint(Context c)      { return Prefs.isLight(c) ? 0xff888888 : 0xff666666; }
    public static int divider(Context c)       { return Prefs.isLight(c) ? 0xffdddddd : 0xff2a2a2a; }
    public static int accent(Context c)    { return Prefs.getAccent(c); }
    public static int logoTint(Context c)  { return Prefs.isLight(c) ? Color.BLACK : Color.WHITE; }
    public static int bgRes(Context c)     { return Prefs.isLight(c) ? R.drawable.bg_theme_light : R.drawable.bg_theme_dark; }

    private static float d(Context c, float dp) { return dp * c.getResources().getDisplayMetrics().density; }

    public static void styleContainer(Context c, View v) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(d(c, 18));
        g.setColor(panel(c));
        g.setStroke((int) d(c, 2), accent(c));
        v.setBackground(g);
    }

    public static void styleItem(Context c, View v) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(d(c, 12));
        g.setColor(item(c));
        g.setStroke((int) d(c, 1), divider(c));
        v.setBackground(g);
    }

    /** Highlighted pinned item (accent outline + translucent accent fill). */
    public static void stylePinned(Context c, View v) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(d(c, 12));
        g.setColor((Prefs.getAccent(c) & 0x00ffffff) | 0x33000000);
        g.setStroke((int) d(c, 2), accent(c));
        v.setBackground(g);
    }

    public static void styleButton(Context c, View v) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(d(c, 12));
        g.setColor(Color.TRANSPARENT);
        g.setStroke((int) d(c, 1.5f), accent(c));
        v.setBackground(g);
    }

    /** Circular transparent icon button with accent tint (material icon button). */
    public static void styleIconButton(Context c, View v) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.TRANSPARENT);
        g.setStroke((int) d(c, 1.2f), accent(c));
        v.setBackground(g);
    }

    public static GradientDrawable fab(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(accent(c));
        return g;
    }

    public static void tint(ImageView iv, int color) { iv.setColorFilter(color); }
    public static void tintLogo(Context c, ImageView iv) { iv.setColorFilter(logoTint(c)); }
    public static void tintAccent(Context c, ImageView iv) { iv.setColorFilter(accent(c)); }
}
