package com.matrix.ps5tool;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

/** Remote Play: launches Chiaki if installed, otherwise offers the store page. */
public class RemotePlayActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_remoteplay);
        setupHeader("Remote Play");
        styleContainer(R.id.main_container);

        TextView sub = findViewById(R.id.header_subtitle);
        sub.setText("chiaki remote play client");

        TextView status = findViewById(R.id.rp_status);
        status.setTextColor(Theme.textSecondary(this));

        Button launch = findViewById(R.id.btn_rp_launch);
        Button get = findViewById(R.id.btn_rp_get);
        Theme.styleButton(this, launch);
        Theme.styleButton(this, get);

        String pkg = Prefs.getChiakiPkg(this);
        boolean installed;
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }
        status.setText(installed
                ? "Chiaki installed (" + pkg + ")"
                : "Chiaki not installed. Install it to use Remote Play.");

        launch.setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(Prefs.getChiakiPkg(this));
            if (i != null) startActivity(i);
            else status.setText("Chiaki not found on this device.");
        });
        get.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://f-droid.org/packages/"
                                + Prefs.getChiakiPkg(this) + "/"))));
    }
}
