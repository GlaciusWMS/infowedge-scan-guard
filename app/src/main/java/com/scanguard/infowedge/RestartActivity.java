package com.scanguard.infowedge;

import android.app.Activity;
import android.os.Bundle;

/** Opened with the link scanguard://restart (e.g. Power Apps Launch("scanguard://restart")): asks the service to restart the scanner and closes at once. */
public class RestartActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GuardService.start(this, GuardService.ACTION_RESTART);
        finish();
    }
}
