package com.richelieu.pins.phone.presenter;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Test receiver for AndroidManifest.xml merge.
 *
 * @author richelieu  09.26 2018
 */
public class PresenterReceiver extends BroadcastReceiver{

    private static final String TAG = "PhoneReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive with: context = " + context + ", intent = " + intent + "");
    }
}
