package com.matrix.ps5tool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Foreground service that keeps the DLNA cast alive in background.
 * The media notification shows live playback progress and opens the OSD remote.
 */
public class CastService extends Service {

    private static final String CHANNEL = "cast_channel";
    private static final int NOTIF_ID = 42;
    private static final int FOREGROUND_TYPE_MEDIA = 2; // FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK

    public static final String ACTION_START = "com.matrix.ps5tool.CAST_START";
    public static final String ACTION_PLAY_PAUSE = "com.matrix.ps5tool.CAST_PLAY_PAUSE";
    public static final String ACTION_STOP = "com.matrix.ps5tool.CAST_STOP";

    private MediaSession session;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int lastRel = -1, lastDur = -1;
    private int failCount;
    private boolean warned;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (CastState.controlUrl != null) {
                DlnaController ctl = CastState.controller();
                if (ctl != null) ctl.getPosition((rel, dur) -> {
                    if (rel >= 0 || dur > 0) {
                        if (rel >= 0) lastRel = rel;
                        if (dur > 0) lastDur = dur;
                        failCount = 0;
                        warned = false;
                        notifyNow();
                    } else {
                        failCount++;
                        if (failCount >= 6 && !warned) {
                            warned = true;
                            CastState.log("! receiver not responding — check that PSPlay is running");
                        }
                    }
                });
            }
            handler.postDelayed(this, CastState.playing ? 1000 : 2500);
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, CastService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, CastService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Cast",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Media cast controls");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        session = new MediaSession(this, "ps5tool_cast");
        session.setActive(true);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { toggle(true); }
            @Override public void onPause() { toggle(false); }
            @Override public void onStop() { stopCast(); }
        });
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String action = i == null ? ACTION_START : i.getAction();
        if (ACTION_PLAY_PAUSE.equals(action)) {
            toggle(!CastState.playing);
        } else if (ACTION_STOP.equals(action)) {
            stopCast();
        } else {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIF_ID, buildNotif(), FOREGROUND_TYPE_MEDIA);
            else startForeground(NOTIF_ID, buildNotif());
        }
        return START_STICKY;
    }

    private void toggle(boolean play) {
        DlnaController ctl = CastState.controller();
        if (ctl == null) { stopCast(); return; }
        DlnaController.Result silent = new DlnaController.Result() {
            @Override public void onOk() {}
            @Override public void onError(String m) { CastState.log("! " + m); }
        };
        if (play) ctl.resume(silent); else ctl.pause(silent);
        CastState.playing = play;
        notifyNow();
    }

    private void stopCast() {
        DlnaController ctl = CastState.controller();
        if (ctl != null) ctl.stop(new DlnaController.Result() {
            @Override public void onOk() {}
            @Override public void onError(String m) {}
        });
        CastState.log("< cast stopped");
        CastState.clear();
        handler.removeCallbacks(tick);
        stopForeground(true);
        stopSelf();
    }

    private void notifyNow() {
        updateSession();
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotif());
    }

    private void updateSession() {
        if (session == null) return;
        try {
            MediaMetadata md = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE,
                            CastState.mediaTitle == null ? "Casting" : CastState.mediaTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST,
                            CastState.rendererName == null ? "PS5" : CastState.rendererName)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION,
                            lastDur > 0 ? lastDur * 1000L : -1)
                    .build();
            session.setMetadata(md);
            PlaybackState st = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY
                            | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP)
                    .setState(CastState.playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                            lastRel >= 0 ? lastRel * 1000L : 0, 1.0f)
                    .build();
            session.setPlaybackState(st);
        } catch (Exception ignored) {}
    }

    private Notification buildNotif() {
        // opens the OSD on top of the current task — back returns to the last casting screen
        Intent open = new Intent(this, CastOsdActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, open, piFlags());

        PendingIntent ppPi = PendingIntent.getService(this, 1,
                new Intent(this, CastService.class).setAction(ACTION_PLAY_PAUSE), piFlags());
        PendingIntent stopPi = PendingIntent.getService(this, 2,
                new Intent(this, CastService.class).setAction(ACTION_STOP), piFlags());

        String renderer = CastState.rendererName == null ? "" : CastState.rendererName;
        String line2 = renderer;
        if (lastRel >= 0 && lastDur > 0)
            line2 = fmt(lastRel) + " / " + fmt(lastDur) + (renderer.isEmpty() ? "" : "  ·  " + renderer);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle(CastState.mediaTitle == null ? "Casting" : CastState.mediaTitle)
                .setContentText(line2)
                .setSmallIcon(R.drawable.ic_cast)
                .setContentIntent(contentPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null,
                        CastState.playing ? "Pause" : "Play", ppPi).build())
                .addAction(new Notification.Action.Builder(null, "Stop", stopPi).build());
        if (lastDur > 0 && lastRel >= 0) b.setProgress(lastDur, lastRel, false);
        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1);
        b.setStyle(style);
        return b.build();
    }

    private static String fmt(int s) {
        if (s < 0) return "--:--";
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static int piFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (session != null) { session.setActive(false); session.release(); }
        super.onDestroy();
    }
}
