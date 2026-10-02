package com.scanguard.infowedge;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Status screen: starts the guard, offers a manual restart and shows the last events. */
public class MainActivity extends Activity {

    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
        }
        GuardService.start(this, null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("InfoWedge Scan Guard is running.\nIt restarts the scanner after the camera has been used.");
        title.setTextSize(18);
        root.addView(title);

        Button restart = new Button(this);
        restart.setText("Restart scanner now");
        restart.setOnClickListener(v -> {
            GuardService.start(this, GuardService.ACTION_RESTART);
            v.postDelayed(this::showLog, 1600);
        });
        root.addView(restart);

        Button refresh = new Button(this);
        refresh.setText("Refresh log");
        refresh.setOnClickListener(v -> showLog());
        root.addView(refresh);

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(13);
        logView.setTextIsSelectable(true);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showLog();
    }

    private void showLog() {
        String lines = getSharedPreferences("log", MODE_PRIVATE).getString("lines", "(no events yet)");
        logView.setText(lines);
    }
}
