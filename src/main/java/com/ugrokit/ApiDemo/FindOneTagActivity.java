package com.ugrokit.ApiDemo;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.ugrokit.api.*;

public class FindOneTagActivity extends UgiUiActivity implements UgiInventoryDelegate, UgiInventoryDelegate.InventoryTagFoundListener {

  private UgiTag tagFound = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_find_one_tag);
    Ugi.getSingleton().startInventory(this, UgiRfidConfiguration.forInventoryType(UgiRfidConfiguration.InventoryTypes.INVENTORY_DISTANCE));

    this.setDisplayDialogIfDisconnected(true);
    this.configureTitleViewNavigation();
    getTitleView().setUseBackgroundBasedOnUiColor(true);
    getTitleView().setDisplayWaveAnimationWhileScanning(true);
    getTitleView().setTheTitle("find one tag");
  }

  @Override
  public void disconnectedDialogCancelled() {
    stopScanningThenGoBack();
  }

  //////////////////

  @Override public void inventoryTagFound(UgiTag tag,
                                          UgiInventory.DetailedPerReadData[] detailedPerReadData) {
    //Ugi.log("inventoryTagFound: " + tag);
    if (tagFound == null) {
      tagFound = tag;
      TextView tv = findViewById(R.id.textView);
      tv.setText(tag.getEpc().toString());
      stopScanningThenGoBack();
    }
  }

  public void doCancel(@SuppressWarnings("UnusedParameters") View view) {
    stopScanningThenGoBack();
  }

  private void stopScanningThenGoBack() {
    UgiUiUtil.stopInventoryWithCompletionShowWaiting(this, () -> FindOneTagActivity.this.goBackWithData(tagFound != null ? tagFound.getEpc() : null));
  }

}
