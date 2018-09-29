package com.richelieu.pins.demo.application;


import android.app.Activity;
import android.os.Bundle;
import com.richelieu.pins.phone.binder.PhoneBlock;
import com.richelieu.pins.phone.binder.model.PhoneModel;
import com.richelieu.pins.phone.binder.presenter.PhonePresenter;
import com.richelieu.pins.phone.binder.view.PhoneView;
import com.richelieu.pins.phone.model.PhoneModelImpl;
import com.richelieu.pins.phone.presenter.PhonePresenterImpl;
import com.richelieu.pins.phone.view.PhoneViewImpl;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PhoneView view = new PhoneViewImpl(this);
        PhonePresenter presenter = new PhonePresenterImpl();
        PhoneModel model = new PhoneModelImpl();
        PhoneBlock.initialize(view, presenter, model);
    }
}
