package com.ugrokit.ApiDemo;

import com.ugrokit.api.*;
import android.app.Application;

public class ApiDemoApp extends Application {

  @Override public void onCreate() {
    super.onCreate();
    Ugi.createSingleton(this);
    UgiUiUtil.setUseUGrokItStyleAlerts(true);
    //
    // to control Internet use
    //
    //Ugi.getSingleton().getConfigurationDelegate().setSendGrokkerSerialNumber(false);
    //Ugi.getSingleton().getConfigurationDelegate().setCheckServerForUnknownDevices(false);
    //Ugi.getSingleton().getConfigurationDelegate().setSendFirstConnectionAndAutomaticConfigurationReports(false);
    //Ugi.getSingleton().getConfigurationDelegate().setDoAutomaticFirmwareUpdate(false);
    //
    // to set additional logging
    //
    //Ugi.getSingleton().setLoggingStatus(Ugi.LoggingTypes.STATE | Ugi.LoggingTypes.INVENTORY);
    //
    // to capture logging
    //
    //Ugi.setLoggingCallback(new Ugi.LoggingCallback() {
    //  @Override
    //  public void log(String s) {
    //    System.out.println("API DEMO: " + s);
    //  }
    //});


    Ugi.getSingleton().openConnection();
  }

  //
  // Android never calls onTerminate(), it just kills the process to kill the app.
  // For symmetry, this is the code that would be called on onTerminate() if Android
  // did close applications.
  /*
  @Override public void onTerminate() {
    Ugi.getSingleton().closeConnection();
    Ugi.getSingleton().release();
    super.onTerminate();
  }
  */

}
