package com.scanguard.infowedge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

// InfoWedge Scan Guard.
// On MUNBYN (Chainway-based) handhelds the InfoWedge scan engine can stay dead after another app has used the camera.
// Proven fix: broadcast ENABLE_INFOWEDGE false, then true (InfoWedgeAPI Programming Guide v1.4).
// This service sends that restart shortly after a camera goes from "in use" back to "free",
// and on request (RestartActivity / MainActivity).
// v1.1: no camera is ever ignored (v1.0 wrongly learned camera 0 as "own restart" when an app closed it during a restart);
// the restart is debounced to 0.8 s after the LAST camera release; a release during a restart triggers one more restart;
// at most 6 restarts per minute.
public class GuardService extends Service {

static final String TAG = "InfoWedgeScanGuard";
    static final String ACTION_RESTART = "com.scanguard.infowedge.RESTART";

static final String IW_ACTION = "com.symbol.infowedge.api.ACTION";
    static final String IW_ENABLE = "com.symbol.infowedge.api.ENABLE_INFOWEDGE";
    // The InfoWedge app is the documented receiver; ScannerService is sent to as well in case the receiver lives there.
static final String[] IW_PACKAGES = { "com.rscja.infowedge", "com.rscja.scanservice" };

static final long DELAY_AFTER_CAMERA_FREE_MS = 800;  // restart this long after the LAST camera release
static final long DISABLE_TO_ENABLE_MS = 700;        // gap between off and on
static final int MAX_RESTARTS_PER_MINUTE = 6;        // safety brake

private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "scanguard";

private CameraManager cameraManager;
    private Handler handler;
    private final Set<String> inUse = new HashSet<>();
    private final ArrayDeque<Long> recentRestarts = new ArrayDeque<>();
    private boolean restartRunning = false;
    private boolean restartAgain = false;

private final Runnable restartRunnable = this::restartInfoWedge;

private final CameraManager.AvailabilityCallback callback = new CameraManager.AvailabilityCallback() {
    @Override
    public void onCameraUnavailable(String cameraId) {
        if (inUse.add(cameraId)) {
            log("camera " + cameraId + " in use");
        }
    }

    @Override
    public void onCameraAvailable(String cameraId) {
        if (inUse.remove(cameraId)) {
            log("camera " + cameraId + " free -> restart scanner");
            requestRestart(DELAY_AFTER_CAMERA_FREE_MS);
        }
    }
};

@Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        startForeground(NOTIFICATION_ID, buildNotification());
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraManager.registerAvailabilityCallback(callback, handler);
            log("guard started (v1.1)");
        } catch (Exception e) {
            log("camera watch failed: " + e.getMessage());
        }
    }

@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RESTART.equals(intent.getAction())) {
            log("restart requested");
            requestRestart(0);
        }
        return START_STICKY;
    }

@Override
    public void onDestroy() {
        try {
            cameraManager.unregisterAvailabilityCallback(callback);
        } catch (Exception ignoredException) {
            // nothing to do
        }
        handler.removeCallbacks(restartRunnable);
        super.onDestroy();
    }

@Override
    public IBinder onBind(Intent intent) {
        return null;
    }

// Debounced: a new request moves the restart to delayMs from now. During a running restart, one more follows.
private void requestRestart(long delayMs) {
    if (restartRunning) {
        restartAgain = true;
        return;
    }
    handler.removeCallbacks(restartRunnable);
    handler.postDelayed(restartRunnable, delayMs);
}

private void restartInfoWedge() {
    long now = SystemClock.elapsedRealtime();
    while (!recentRestarts.isEmpty() && now - recentRestarts.peekFirst() > 60000) {
        recentRestarts.pollFirst();
    }
    if (recentRestarts.size() >= MAX_RESTARTS_PER_MINUTE) {
        log("restart skipped: more than " + MAX_RESTARTS_PER_MINUTE + " in a minute");
        return;
    }
    recentRestarts.addLast(now);
    restartRunning = true;
    sendEnable(false);
    handler.postDelayed(() -> {
        sendEnable(true);
        restartRunning = false;
        log("scanner restarted");
        if (restartAgain) {
            restartAgain = false;
            requestRestart(DELAY_AFTER_CAMERA_FREE_MS);
        }
    }, DISABLE_TO_ENABLE_MS);
}

private void sendEnable(boolean enable) {
    for (String pkg : IW_PACKAGES) {
        Intent i = new Intent(IW_ACTION);
        i.setPackage(pkg);
        i.putExtra(IW_ENABLE, enable);
        sendBroadcast(i);
    }
}

private Notification buildNotification() {
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Scan Guard", NotificationManager.IMPORTANCE_MIN);
    ch.setShowBadge(false);
    nm.createNotificationChannel(ch);
    Intent open = new Intent(this, MainActivity.class);
    PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("InfoWedge Scan Guard")
        .setContentText("Keeps the scanner working after photos")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentIntent(pi)
        .setOngoing(true)
        .build();
}

// Keeps the last 30 events for the status screen.
void log(String message) {
    Log.i(TAG, message);
    String stamp = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.ROOT).format(new Date());
    SharedPreferences sp = getSharedPreferences("log", MODE_PRIVATE);
    String old = sp.getString("lines", "");
    String lines = stamp + "  " + message + "\n" + old;
    String[] parts = lines.split("\n");
    StringBuilder sb = new StringBuilder();
    for (int k = 0; k < parts.length && k < 30; k++) {
        sb.append(parts[k]).append("\n");
    }
    sp.edit().putString("lines", sb.toString()).apply();
}

static void start(Context context, String action) {
    Intent i = new Intent(context, GuardService.class);
    if (action != null) i.setAction(action);
    context.startForegroundService(i);
}
}
