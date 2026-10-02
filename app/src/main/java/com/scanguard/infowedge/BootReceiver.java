package com.scanguard.infowedge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Starts the guard by itself: after the device boots, and after the app has been updated.
// Note: Android only delivers these to an app that has been opened once since it was installed
// (or started once with: adb shell am start -n com.scanguard.infowedge/.MainActivity).
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
            || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            GuardService.start(context, null);
        }
    }
}
