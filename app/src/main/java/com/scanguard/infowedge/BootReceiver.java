package com.scanguard.infowedge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the guard after the device boots. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            GuardService.start(context, null);
        }
    }
}
