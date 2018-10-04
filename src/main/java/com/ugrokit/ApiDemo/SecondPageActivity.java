package com.ugrokit.ApiDemo;

import android.graphics.Color;
import android.os.Bundle;

import com.ugrokit.api.*;

public class SecondPageActivity extends UgiUiActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_second_page);

    this.setThemeColor(Color.parseColor("#F58026"));
    this.configureTitleViewNavigation();
    getTitleView().setBatteryStatusIndicatorDisplayVersionInfoOnTouch(true);
    getTitleView().setTheTitle("second page");
    getTitleView().setRightButton(R.drawable.btn_second_page_right, UgiUiUtil.NULL_COLOR,
                                  0, UgiUiUtil.NULL_COLOR,
                                  () -> System.out.println("right"));
    getFooterView().setCenter("back", SecondPageActivity.this::goBack);
  }

}
