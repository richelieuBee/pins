package com.richelieu.pins.phone.model;


import android.util.Log;
import com.richelieu.pins.phone.binder.model.PhoneModel;

/**
 * phone model
 *
 * @author richelieu  09.21 2018
 */
public class PhoneModelImpl implements PhoneModel {

    private static final String TAG = "PhoneModelImpl";

    @Override
    public String getNameByNumber(String number) {
        Log.d(TAG, "getNameByNumber with: number = " + number + "");
        return "richelieu";
    }
}
