package com.matrix.ps5tool;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Modern remote OSD: D-pad, volume, seek bar and transport controls
 * for the active DLNA cast. Back returns to the previous casting screen.
 */
public class CastOsdActivity extends Activity {

    private TextView osdTitle, osdDevice, timeCur, timeDur;
    private SeekBar osdSeek;
    private ImageButton btnPower, btnOk, btnPause;
    private int lastPos, lastDur = -1;
    private boolean tracking;
    private boolean playing;
    private boolean stopped;
    private final Handler h = new Handler();
    private DlnaController ctl;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (ctl != null) ctl.getPosition((rel, dur) -> {
                if (rel >= 0) lastPos = rel;
                if (dur > 0) { lastDur = dur; osdSeek.setMax(dur); }
                if (!tracking && rel >= 0) {
                    osdSeek.setProgress(rel);
                    timeCur.setText(fmt(rel));
                }
                if (dur >= 0) timeDur.setText(fmt(dur));
            });
            h.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_cast_osd);

        osdTitle = findViewById(R.id.osd_title);
        osdDevice = findViewById(R.id.osd_device);
        timeCur = findViewById(R.id.osd_time_cur);
        timeDur = findViewById(R.id.osd_time_dur);
        osdSeek = findViewById(R.id.osd_seek);
        btnPower = findViewById(R.id.osd_power);
        btnOk = findViewById(R.id.osd_ok);
        btnPause = findViewById(R.id.osd_pause);
        ImageButton btnPlay = findViewById(R.id.osd_play);
        ImageButton btnStop = findViewById(R.id.osd_stop);
        ImageButton btnRw = findViewById(R.id.osd_rw);
        ImageButton btnFf = findViewById(R.id.osd_ff);
        ImageButton btnUp = findViewById(R.id.osd_up);
        ImageButton btnDown = findViewById(R.id.osd_down);
        ImageButton btnLeft = findViewById(R.id.osd_left);
        ImageButton btnRight = findViewById(R.id.osd_right);
        ImageButton btnVolUp = findViewById(R.id.osd_vol_up);
        ImageButton btnVolDown = findViewById(R.id.osd_vol_down);

        osdTitle.setText(CastState.mediaTitle == null ? "Nothing playing" : CastState.mediaTitle);
        osdTitle.setSelected(true);
        osdDevice.setText(CastState.rendererName == null ? "" : CastState.rendererName);

        ctl = CastState.controller();
        playing = CastState.playing;
        updateToggleIcons();

        if (ctl != null && CastState.volume < 0)
            ctl.getVolume(v -> { if (v >= 0) CastState.volume = v; });

        DlnaController.Result silent = new DlnaController.Result() {
            @Override public void onOk() {}
            @Override public void onError(String m) {
                Toast.makeText(CastOsdActivity.this, m, Toast.LENGTH_SHORT).show();
                CastState.log("! " + m);
            }
        };

        // ---- top corner: power / disconnect ----
        btnPower.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Disconnect")
                .setMessage("Stop casting and disconnect from "
                        + (CastState.rendererName == null ? "the receiver" : CastState.rendererName) + "?")
                .setPositiveButton("Disconnect", (d, w) -> {
                    CastState.log("> disconnecting from receiver");
                    CastService.stop(this);
                    CastState.clear();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show());

        // ---- D-pad ----
        btnOk.setOnClickListener(v -> togglePlayPause(silent));
        btnLeft.setOnClickListener(v -> seekBy(-10, silent));
        btnRight.setOnClickListener(v -> seekBy(10, silent));
        btnUp.setOnClickListener(v -> seekBy(30, silent));
        btnDown.setOnClickListener(v -> seekBy(-30, silent));

        // ---- volume pill ----
        btnVolUp.setOnClickListener(v -> volumeBy(5, silent));
        btnVolDown.setOnClickListener(v -> volumeBy(-5, silent));

        // ---- transport ----
        btnPlay.setOnClickListener(v -> {
            if (ctl == null) return;
            if (stopped) replay(silent);
            else ctl.resume(silent);
            playing = true;
            stopped = false;
            CastState.playing = true;
            updateToggleIcons();
        });
        btnPause.setOnClickListener(v -> {
            if (ctl == null) return;
            ctl.pause(silent);
            playing = false;
            CastState.playing = false;
            updateToggleIcons();
        });
        btnStop.setOnClickListener(v -> {
            if (ctl == null) return;
            ctl.stop(silent);
            playing = false;
            stopped = true;
            CastState.playing = false;
            CastState.log("< playback stopped");
            updateToggleIcons();
        });
        btnRw.setOnClickListener(v -> seekBy(-30, silent));
        btnFf.setOnClickListener(v -> seekBy(30, silent));

        osdSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) timeCur.setText(fmt(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { tracking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                tracking = false;
                if (ctl != null) ctl.seek(s.getProgress(), silent);
            }
        });
    }

    private void togglePlayPause(DlnaController.Result silent) {
        if (ctl == null) return;
        if (stopped) { replay(silent); playing = true; stopped = false; }
        else if (playing) { ctl.pause(silent); playing = false; }
        else { ctl.resume(silent); playing = true; }
        CastState.playing = playing;
        updateToggleIcons();
    }

    /** After Stop the renderer needs the URI again: re-send it and seek back. */
    private void replay(DlnaController.Result silent) {
        if (CastState.mediaUrl == null) { ctl.resume(silent); return; }
        int resumePos = lastPos;
        ctl.play(CastState.mediaUrl, CastState.mediaTitle,
                CastState.subtitlesOn ? CastState.subtitleUrl : null,
                new DlnaController.Result() {
                    @Override public void onOk() {
                        if (resumePos > 2) ctl.seek(resumePos, silent);
                    }
                    @Override public void onError(String m) {
                        Toast.makeText(CastOsdActivity.this, m, Toast.LENGTH_SHORT).show();
                        CastState.log("! " + m);
                    }
                });
    }

    private void seekBy(int delta, DlnaController.Result silent) {
        if (ctl == null) return;
        int target = lastPos + delta;
        if (target < 0) target = 0;
        if (lastDur > 0 && target > lastDur - 1) target = lastDur - 1;
        lastPos = target;
        osdSeek.setProgress(target);
        timeCur.setText(fmt(target));
        ctl.seek(target, silent);
    }

    private void volumeBy(int delta, DlnaController.Result silent) {
        if (ctl == null) return;
        int v = CastState.volume < 0 ? 50 : CastState.volume;
        v += delta;
        if (v < 0) v = 0;
        if (v > 100) v = 100;
        CastState.volume = v;
        ctl.setVolume(v, silent);
    }

    private void updateToggleIcons() {
        btnOk.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_ok);
        btnPause.setAlpha(playing ? 1.0f : 0.45f);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        Haptics.dispatch(this, ev); // global tap vibration (Settings > Haptics)
        return super.dispatchTouchEvent(ev);
    }

    private static String fmt(int s) {
        if (s < 0) return "--:--";
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @Override protected void onResume() { super.onResume(); h.post(poll); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(poll); }
}
