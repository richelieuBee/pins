package com.richelieu.pins.phone.presenter;


import android.util.Log;
import com.richelieu.pins.phone.binder.presenter.PhonePresenter;

/**
 * phone presenter
 *
 * @author richelieu  09.21 2018
 */
public class PhonePresenterImpl implements PhonePresenter {

    private static final String TAG = "PhonePresenterImpl";

    @Override
    public boolean dial(String name, String number) {
        Log.d(TAG, "dial with: name = " + name + ", number = " + number + "");
        return true;
    }
}
