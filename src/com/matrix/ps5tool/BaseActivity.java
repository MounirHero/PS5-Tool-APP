package com.matrix.ps5tool;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/** Base: slide animations, shared header wiring, themed background + logos. */
public abstract class BaseActivity extends android.app.Activity {

    private String headerTitle;
    private int containerId = -1;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        Haptics.dispatch(this, ev); // global tap vibration (Settings > Haptics)
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // re-apply theme chrome so theme changes propagate to stacked screens
        if (headerTitle != null) setupHeader(headerTitle);
        if (containerId != -1) styleContainer(containerId);
    }

    protected void open(Intent i) {
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    protected void openTop(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    /**
     * Wires the shared chrome: PS logo (page top-left) = back, gear (page
     * top-right, outside the container) = settings, PS5 text logo in the
     * container header, themed background image and logo tints.
     */
    protected void setupHeader(String title) {
        headerTitle = title;
        View root = findViewById(android.R.id.content);
        if (root != null) root.setBackgroundColor(Theme.bg(this));

        ImageView bg = findViewById(R.id.bg_image);
        if (bg != null) bg.setImageResource(Theme.bgRes(this));

        ImageView logo = findViewById(R.id.header_logo);
        if (logo != null) {
            Theme.tintLogo(this, logo);
            logo.setOnClickListener(v -> onBackPressed());
        }
        ImageView ps5 = findViewById(R.id.header_ps5logo);
        if (ps5 != null) Theme.tintLogo(this, ps5);

        TextView t = findViewById(R.id.header_title);
        if (t != null && title != null) {
            t.setText(title);
            t.setTextColor(Theme.textPrimary(this));
        }
        TextView sub = findViewById(R.id.header_subtitle);
        if (sub != null) sub.setTextColor(Theme.textSecondary(this));

        ImageView gear = findViewById(R.id.header_settings);
        if (gear != null) {
            Theme.tintAccent(this, gear);
            gear.setOnClickListener(v -> open(new Intent(this, SettingsActivity.class)));
        }
    }

    protected void styleContainer(int id) {
        containerId = id;
        View v = findViewById(id);
        if (v != null) Theme.styleContainer(this, v);
    }
}
