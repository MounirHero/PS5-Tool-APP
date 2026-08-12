package com.matrix.ps5tool;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

/**
 * Global touch vibration: every tap that lands on a clickable view vibrates.
 * Duration and on/off are configurable from Settings.
 */
public class Haptics {

    private static long lastTap;

    /** Call from Activity.dispatchTouchEvent: vibrates when the touch targets a clickable view. */
    public static void dispatch(Activity a, MotionEvent ev) {
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) return;
        if (!Prefs.isHapticsOn(a)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastTap < 150) return;
        View root = a.findViewById(android.R.id.content);
        if (root == null) return;
        if (findClickable(root, ev.getRawX(), ev.getRawY()) != null) {
            lastTap = now;
            tap(a);
        }
    }

    private static View findClickable(View v, float x, float y) {
        if (v.getVisibility() != View.VISIBLE || !v.isShown()) return null;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        if (x < loc[0] || x >= loc[0] + v.getWidth()
                || y < loc[1] || y >= loc[1] + v.getHeight()) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View hit = findClickable(g.getChildAt(i), x, y);
                if (hit != null) return hit;
            }
        }
        if (v instanceof WebView) return null; // page scrolls/zooms shouldn't vibrate
        return v.isClickable() ? v : null;
    }

    public static void tap(Context c) {
        try {
            Vibrator vib;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vib = vm == null ? null : vm.getDefaultVibrator();
            } else {
                vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vib == null || !vib.hasVibrator()) return;
            int ms = Prefs.getHapticMs(c);
            if (ms <= 0) return;
            if (Build.VERSION.SDK_INT >= 26)
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else vib.vibrate(ms);
        } catch (Exception ignored) {}
    }
}
