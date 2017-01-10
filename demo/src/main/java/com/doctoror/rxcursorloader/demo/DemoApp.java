package com.doctoror.rxcursorloader.demo;

import android.app.Application;
import android.os.StrictMode;

/**
 * Created by Yaroslav Mytkalyk on 10.01.17.
 */

public final class DemoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
    }
}
