package com.richelieu.pins.phone.binder;


import android.util.Log;
import com.richelieu.pins.phone.binder.model.PhoneModel;
import com.richelieu.pins.phone.binder.presenter.PhonePresenter;
import com.richelieu.pins.phone.binder.view.PhoneView;

/**
 * phone block
 *
 * @author richelieu  09.21 2018
 */
public class PhoneBlock {

    private static final String TAG = "PhoneBlock";

    public static String getContent() {
        return "This is pins example.";
    }

    public static boolean initialize(PhoneView phoneView, PhonePresenter phonePresenter, PhoneModel phoneModel) {
        Log.d(TAG, "initialize with: phoneView = " + phoneView + ", phonePresenter = " + phonePresenter + ", phoneModel = " + phoneModel + "");
        phoneView.displayIncomingCall("10010", "10010");
        return true;
    }
}
