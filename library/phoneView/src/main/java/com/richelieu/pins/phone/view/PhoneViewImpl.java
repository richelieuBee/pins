package com.richelieu.pins.phone.view;


import android.content.Context;
import android.util.Log;
import com.richelieu.pins.phone.binder.view.PhoneView;

/**
 * phone view.
 *
 * @author richelieu  09.21 2018
 */
public class PhoneViewImpl implements PhoneView {

    private static final String TAG = "PhoneViewImpl";

    private Context context;

    public PhoneViewImpl(Context context) {
        this.context = context;
    }

    @Override
    public boolean displayIncomingCall(String name, String number) {
        Log.d(TAG, "displayIncomingCall with: name = " + name + ", number = " + number + "");
        Log.d(TAG, "Text: " + context.getString(R.string.show_text));

        return false;
    }
}
