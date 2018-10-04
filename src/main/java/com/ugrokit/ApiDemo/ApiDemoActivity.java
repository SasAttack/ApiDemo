package com.ugrokit.ApiDemo;

import java.util.*;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.view.*;
import android.widget.*;

import com.ugrokit.api.*;

public class ApiDemoActivity extends UgiUiActivity implements
        UgiInventoryDelegate,
        UgiInventoryDelegate.InventoryHistoryIntervalListener,
        UgiInventoryDelegate.InventoryDidStopListener,
        //UgiInventoryDelegate.InventoryDidStartListener,
        //UgiInventoryDelegate.InventoryTagChangedListener,
        //UgiInventoryDelegate.InventoryFilterListener,
        //UgiInventoryDelegate.InventoryFilterLowLevelListener,
        UgiInventoryDelegate.InventoryTagFoundListener,
        UgiInventoryDelegate.InventoryTagSubsequentFindsListener {

  private static final int SPECIAL_FUNCTION_NONE = 0;
  private static final int SPECIAL_FUNCTION_READ_USER_MEMORY = 1;
  private static final int SPECIAL_FUNCTION_READ_TID_MEMORY = 2;
  private static final int SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_SENSOR_CODE = 3;
  private static final int SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_TEMPERATURE = 4;

  private int specialFunction = SPECIAL_FUNCTION_NONE;

  private static final UgiRfMicron.MagnusModels RF_MICRON_MAGNUS_MODEL = UgiRfMicron.MagnusModels.Model402;
  private static final UgiRfMicron.RssiLimitTypes RF_MICRON_MAGNUS_LIMIT_TYPE = UgiRfMicron.RssiLimitTypes.LessThanOrEqual;
  private static final int RF_MICRON_MAGNUS_LIMIT_THRESHOLD = 31;

  //////////////////////////////

  private UgiRfidConfiguration.InventoryTypes inventoryType;

  private OurListAdapter listAdapter;

  private final List<UgiTag> displayedTags = new ArrayList<>();
  private final Map<UgiTag, StringBuilder> detailedData = new HashMap<>();


  /** Called when the activity is first created. */
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    this.setDisplayDialogIfDisconnected(true);
    this.configureTitleViewNavigation();
    getTitleView().setBatteryStatusIndicatorDisplayVersionInfoOnTouch(true);
    getTitleView().setUseBackgroundBasedOnUiColor(true);
    getTitleView().setDisplayWaveAnimationWhileScanning(true);
    getTitleView().setTheTitle(getString(R.string.main_title));

    inventoryType = UgiRfidConfiguration.InventoryTypes.LOCATE_DISTANCE;
    ListView tagListView = findViewById(R.id.tagList);
    listAdapter = new OurListAdapter();
    tagListView.setAdapter(listAdapter);
    tagListView.setOnItemClickListener((parent, view, position, id) -> {
      final UgiInventory inventory = Ugi.getSingleton().getActiveInventory();
      final UgiTag tag = displayedTags.get(position);
      if ((inventory != null)) {
        if (!inventory.isPaused()) inventory.pauseInventory();
        UgiUiUtil.showMenu(this, null,
                           inventory::resumeInventory,
                           new UgiUiUtil.MenuTitleAndHandler("commission (write EPC)", () -> this.doCommission(tag)),
                           new UgiUiUtil.MenuTitleAndHandler("read user memory", () -> this.doReadUserMemory(tag)),
                           new UgiUiUtil.MenuTitleAndHandler("write user memory", () -> this.doWriteUserMemory(tag)),
                           new UgiUiUtil.MenuTitleAndHandler("read then write user memory", () -> this.doReadThenWriteUserMemory(tag)),
                           new UgiUiUtil.MenuTitleAndHandler("custom command (read tag)", () -> this.doCustomCommand(tag)),
                           new UgiUiUtil.MenuTitleAndHandler("scan for this tag only", () -> this.doLocate(tag))
                          );
      } else {
        String message = "Touch a tag while scanning (or paused) to act on the tag";
        UgiUiUtil.showOk(this, "not scanning", message);
      }
    });
    updateUI();
  }

  // If you do not want UgiUiActivity (the superclass) to automatically rotate the screen
  // for devices with the audio jack on the top, comment in this bit of code
  /*
  @Override public boolean ugiShouldHandleRotation() {
    return false;
  }
  */

  ///////////////////////////////////////////////////////////////////////////////////////
  // UI
  ///////////////////////////////////////////////////////////////////////////////////////


  private void updateUI() {
    final UgiInventory inventory = Ugi.getSingleton().getActiveInventory();
    findViewById(R.id.actions_button).setEnabled(inventory == null);

    UgiFooterView footer = getFooterView();
    if (inventory != null) {
      //
      // Scanning
      //
      if (inventory.isPaused()) {
        footer.setLeft(getString(R.string.FooterResume), () -> {
          inventory.resumeInventory();
          this.updateUI();
        });
      } else {
        footer.setLeft(getString(R.string.FooterPause), () -> {
          inventory.pauseInventory();
          this.updateUI();
        });
      }
      footer.setCenter(getString(R.string.FooterStop), this::stopScanning);
      footer.setRight(null, null);
    } else {
      //
      // Not scanning
      //
      footer.setLeft(getString(R.string.FooterInfo), () -> UgiUiUtil.showVersionAlert(this, null, false));
      footer.setCenter(getString(R.string.FooterStart), this::startScanning);
      footer.setRight(getString(R.string.FooterConfigure), this::doConfigure);
    }
  }

  @SuppressLint("SetTextI18n")
  private void updateCountAndTime() {
    TextView tv = findViewById(R.id.count_value);
    tv.setText(Integer.toString(displayedTags.size()));

    UgiInventory inventory = Ugi.getSingleton().getActiveInventory();
    if (inventory != null) {
      int seconds = (int) ((System.currentTimeMillis() - inventory.getStartTime().getTime()) / 1000);
      int minutes = seconds / 60;
      seconds = seconds % 60;
      tv = findViewById(R.id.time_value);
      tv.setText(String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds));
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Inventory
  ///////////////////////////////////////////////////////////////////////////////////////

  private Handler updateTimerHandler = null;

  private void startScanning() {
    displayedTags.clear();
    detailedData.clear();
    updateTable();

    UgiRfidConfiguration config;
    switch (specialFunction) {
      case SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_SENSOR_CODE:
        config = UgiRfMicron.configToReadMagnusSensorValue(
                UgiRfidConfiguration.forInventoryType(UgiRfidConfiguration.InventoryTypes.LOCATE_DISTANCE),
                RF_MICRON_MAGNUS_MODEL,
                RF_MICRON_MAGNUS_LIMIT_TYPE,
                RF_MICRON_MAGNUS_LIMIT_THRESHOLD);
        break;
      case SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_TEMPERATURE:
        config = UgiRfMicron.configToReadMagnusTemperature(UgiRfidConfiguration.forInventoryType(UgiRfidConfiguration.InventoryTypes.LOCATE_DISTANCE));
        break;
      default:
        config = UgiRfidConfiguration.forInventoryType(inventoryType);
        if (specialFunction == SPECIAL_FUNCTION_READ_USER_MEMORY) {
          config.minUserBytes = 4;
          config.maxUserBytes = 128;
        } else if (specialFunction == SPECIAL_FUNCTION_READ_TID_MEMORY) {
          config.minTidBytes = 4;
          config.maxTidBytes = 128;
        }
        break;
    }
    Ugi.getSingleton().startInventory(this, config);
    updateUI();
    updateCountAndTime();
    if (!config.reportSubsequentFinds) {
      updateTimerHandler = new Handler();
      updateTimerHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
          if (updateTimerHandler != null) {
            updateCountAndTime();
            updateTimerHandler.postDelayed(this, 1000);
          }
        }
      }, 1000); // 1 second delay (takes millis)
    }
  }

  private void stopScanning() {
    updateTimerHandler = null;
    UgiUiUtil.stopInventoryWithCompletionShowWaiting(this, this::updateUI);
  }

  @Override
  public void disconnectedDialogCancelled() {
    stopScanning();
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Inventory Delegate
  ///////////////////////////////////////////////////////////////////////////////////////

  @Override public void inventoryDidStop(int result) {
    if ((result != UGI_INVENTORY_COMPLETED_LOST_CONNECTION) && (result != UGI_INVENTORY_COMPLETED_OK)) {
      //
      // Inventory error
      //
      UgiUiUtil.showInventoryError(this, result);
    }
    updateTimerHandler = null;
    updateUI();
  }

  @Override public void inventoryHistoryInterval() {
    updateTable();
  }

  @Override public void inventoryTagFound(UgiTag tag, UgiInventory.DetailedPerReadData[] detailedPerReadData) {
    displayedTags.add(tag);
    detailedData.put(tag, new StringBuilder());
    handlePerReads(tag, detailedPerReadData);
    updateTable();
  }

  @Override public void inventoryTagSubsequentFinds(UgiTag tag, int count,
                                                    UgiInventory.DetailedPerReadData[] detailedPerReadData) {
    handlePerReads(tag, detailedPerReadData);
  }

  private void handlePerReads(UgiTag tag,
                              UgiInventory.DetailedPerReadData[] detailedPerReadData) {
    if (specialFunction == SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_SENSOR_CODE) {
      for (UgiInventory.DetailedPerReadData p : detailedPerReadData) {
        //
        // get sensor code and add it to the string we display
        //
        int sensorCode = UgiRfMicron.getMagnusSensorCode(p);
        StringBuilder s = detailedData.get(tag);
        if (s.length() > 0) s.append(" ");
        s.append(sensorCode);
        if (RF_MICRON_MAGNUS_LIMIT_TYPE != UgiRfMicron.RssiLimitTypes.None) {
          //
          // get on-chip RSSI and add it to the string we display
          //
          int onChipRssi = UgiRfMicron.getMagnusOnChipRssi(p);
          s.append("/");
          s.append(onChipRssi);
        }
      }
    } else if (specialFunction == SPECIAL_FUNCTION_READ_RF_MICRON_MAGNUS_TEMPERATURE) {
      for (UgiInventory.DetailedPerReadData p : detailedPerReadData) {
        //
        // Get the temperature and add it to string we display
        //
        double temperatureC = UgiRfMicron.getMagnusTemperature(tag, p);
        StringBuilder s = detailedData.get(tag);
        if (s.length() > 0) s.append(" ");
        s.append(temperatureC);
      }
    }
  }

  /*
  @Override public void inventoryDidStart() {
    Log.i(TAG, "inventoryDidStart");
  }

  @Override public void inventoryTagChanged(UgiTag tag, boolean firstFind) {
    Log.i(TAG, "inventoryTagChanged: firstFind = " + firstFind + ": " + tag);
  }

  @Override public boolean inventoryFilter(UgiEpc epc) {
    Log.i(TAG, "inventoryFilter: " + epc);
    byte[] epcBytes = epc.toBytes();
    return (epcBytes[epcBytes.length-1] & 1) == 1 ? true : false;  // filter out tags with odd EPCs
  }

  @Override public boolean inventoryFilterLowLevel(byte[] epc) {
    Log.i(TAG, "inventoryFilterLowLevel: " + Util.byteArrayToString(epc));
    return (epc[epc.length-1] & 1) == 1 ? true : false;  // filter out tags with odd EPCs
  }
  */

  ///////////////////////////////////////////////////////////////////////////////////////
  // List View
  ///////////////////////////////////////////////////////////////////////////////////////

  private void updateTable() {
    listAdapter.notifyDataSetChanged();
    updateCountAndTime();
  }

  private class OurListAdapter extends BaseAdapter {

    @Override public boolean hasStableIds() {
      return true;
    }

    @Override public int getCount() {
      return displayedTags.size();
    }

    @Override public Object getItem(int position) {
      return position < displayedTags.size() ? displayedTags.get(position) : null;
    }

    @Override public long getItemId(int position) {
      return position < displayedTags.size() ? displayedTags.get(position).getEpc().hashCode() : 0;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
      UgiTagCell listItem = convertView != null ? (UgiTagCell)convertView : new UgiTagCell(getContext(), null);
      if (position < displayedTags.size()) {
        UgiTag tag = displayedTags.get(position);
        listItem.setDisplayTag(tag);
        listItem.setThemeColor(getThemeColor());
        listItem.setTitle(tag.getEpc().toString());
        String detailText = null;
        switch (specialFunction) {
          case SPECIAL_FUNCTION_READ_USER_MEMORY:
            detailText = "user: " + byteArrayToString(tag.getUserBytes());
            break;
          case SPECIAL_FUNCTION_READ_TID_MEMORY:
            detailText = "tid: " + byteArrayToString(tag.getTidBytes());
            break;
          default:
            StringBuilder s = detailedData.get(tag);
            if (s.length() > 0) {
              detailText = s.toString();
            }
            break;
        }
        listItem.setDetail(detailText);
        listItem.updateHistoryView();
      }
      return listItem;
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Tag actions
  ///////////////////////////////////////////////////////////////////////////////////////

  // All tag actions called with inventory paused

  private void doCommission(UgiTag tag) {
    final UgiEpc oldEpc = tag.getEpc();
    UgiUiUtil.showTextInput(
            this,
            "commission tag", "EPC:", "commission", oldEpc.toString(),
            UgiUiUtil.DEFAULT_INPUT_TYPE,
            null, false,
            (value, switchValue) -> {
              UgiEpc newEpc = new UgiEpc(value);
              Ugi.getSingleton().getActiveInventory().resumeInventory();
              this.updateUI();
              UgiUiUtil.showWaiting(this, "commissioning");
              Ugi.getSingleton().getActiveInventory().programTag(
                      oldEpc, newEpc, UgiInventory.NO_PASSWORD,
                      (tag1, result) -> {
                        UgiUiUtil.hideWaiting();
                        if (result == UgiInventory.TagAccessReturnValues.OK) {
                          this.updateTable();
                        } else {
                          UgiUiUtil.showOk(this, "commission tag",
                                           UgiUiUtil.getTagAccessErrorMessage(result));
                        }
                      });
            }, () -> {
              Ugi.getSingleton().getActiveInventory().resumeInventory();
              this.updateUI();
            }, value -> (value.length() == oldEpc.toString().length()) && value.matches("^[0-9a-fA-F]*$"));
  }

  private void doReadUserMemory(UgiTag tag) {
    Ugi.getSingleton().getActiveInventory().resumeInventory();
    updateUI();
    UgiUiUtil.showWaiting(this, "reading user memory");
    System.out.println("doReadUserMemory");
    Ugi.getSingleton().getActiveInventory().readTag(
            tag.getEpc(),
            UgiRfidConfiguration.MemoryBank.User,
            0, 16, 64,
            UgiInventory.NO_PASSWORD,
            (tag1, data, result) -> {
              System.out.println("doReadUserMemory CALLBACK: " + result);
              UgiUiUtil.hideWaiting();
              if (result == UgiInventory.TagAccessReturnValues.OK) {
                UgiUiUtil.showOk(this, "read user memory",
                                 "Read " + data.length + " bytes: " + byteArrayToString(data),
                                 "", null);
              } else {
                UgiUiUtil.showOk(this, "read user memory",
                                 "Error: " + UgiUiUtil.getTagAccessErrorMessage(result));
              }
            }
                                                   );
  }

  private void doWriteUserMemory(UgiTag tag) {
    Ugi.getSingleton().getActiveInventory().resumeInventory();
    updateUI();
    final byte[] newData = "Hello World!".getBytes();
    UgiUiUtil.showWaiting(this, "writing user memory");
    Ugi.getSingleton().getActiveInventory().writeTag(tag.getEpc(),
                                                     UgiRfidConfiguration.MemoryBank.User, 0, newData, null,
                                                     UgiInventory.NO_PASSWORD, (tag1, result) -> {
                                                       UgiUiUtil.hideWaiting();
                                                       if (result == UgiInventory.TagAccessReturnValues.OK) {
                                                         UgiUiUtil.showOk(this, "write user memory",
                                                                          "Write " + newData.length + " bytes: " + byteArrayToString(newData),
                                                                          "", null);
                                                       } else {
                                                         UgiUiUtil.showOk(this, "write user memory",
                                                                          "Error writing tag: " + UgiUiUtil.getTagAccessErrorMessage(result));
                                                       }
                                                     });
  }

  private void doReadThenWriteUserMemory(UgiTag tag) {
    Ugi.getSingleton().getActiveInventory().resumeInventory();
    updateUI();
    UgiUiUtil.showWaiting(this, "reading user memory");
    Ugi.getSingleton().getActiveInventory().readTag(
            tag.getEpc(),
            UgiRfidConfiguration.MemoryBank.User,
            0, 16, 64,
            UgiInventory.NO_PASSWORD,
            (tag12, data, result) -> {
              UgiUiUtil.hideWaiting();
              if (result == UgiInventory.TagAccessReturnValues.OK) {
                byte[] newData = new byte[data.length];
                System.arraycopy(data, 1, newData, 0, data.length - 1);
                newData[data.length - 1] = data[0];
                final byte[] _newData = newData;
                UgiUiUtil.showWaiting(this, "writing user memory");
                Ugi.getSingleton().getActiveInventory().writeTag(tag12.getEpc(),
                                                                 UgiRfidConfiguration.MemoryBank.User, 0, newData, data,
                                                                 UgiInventory.NO_PASSWORD, (tag1, result1) -> {
                                                                   UgiUiUtil.hideWaiting();
                                                                   if (result1 == UgiInventory.TagAccessReturnValues.OK) {
                                                                     UgiUiUtil.showOk(this, "write user memory",
                                                                                      "Write " + _newData.length + " bytes: " + byteArrayToString(_newData),
                                                                                      "", null);
                                                                   } else {
                                                                     UgiUiUtil.showOk(this, "write user memory",
                                                                                      "Error writing tag: " + UgiUiUtil.getTagAccessErrorMessage(result1));
                                                                   }
                                                                 });
              } else {
                UgiUiUtil.showOk(this, "read user memory",
                                 "Error reading tag: " + UgiUiUtil.getTagAccessErrorMessage(result));
              }
            }
                                                   );
  }

  private void doCustomCommand(UgiTag tag) {
    Ugi.getSingleton().getActiveInventory().resumeInventory();
    updateUI();

    int CUSTOM_COMMAND_READ_BANK = 3;
    int CUSTOM_COMMAND_READ_OFFSET = 0;
    int CUSTOM_COMMAND_READ_WORD_COUNT = 4;
    byte[] commandData = new byte[] {
            (byte)0xc2,  // command
            (byte)((CUSTOM_COMMAND_READ_BANK << 6) | (CUSTOM_COMMAND_READ_OFFSET >> 2)),
            (byte)((CUSTOM_COMMAND_READ_OFFSET << 6) | (CUSTOM_COMMAND_READ_WORD_COUNT >> 2)),
            (byte)((CUSTOM_COMMAND_READ_WORD_COUNT << 6) & 0xff)
    };

    UgiUiUtil.showWaiting(this, "custom command (read tag)");
    Ugi.getSingleton().getActiveInventory().customCommandToTag(
            tag.getEpc(),
            commandData,
            8 + 2 + 8 + 8,
            16 * CUSTOM_COMMAND_READ_WORD_COUNT,
            8 * 2,
            5000,
            (tag1, headerBit, response, result) -> {
              UgiUiUtil.hideWaiting();
              if (result == UgiInventory.TagAccessReturnValues.OK) {
                UgiUiUtil.showOk(this, "custom command (read tag)",
                                 "Custom command success: " + response.length + " bytes: " + byteArrayToString(response),
                                 "", null);
              } else {
                UgiUiUtil.showOk(this, "custom command (read tag)",
                                 "Error: " + UgiUiUtil.getTagAccessErrorMessage(result));
              }
            });
  }

  private void doLocate(final UgiTag tag) {
    updateTimerHandler = null;
    UgiUiUtil.stopInventoryWithCompletionShowWaiting(this, () -> {
      displayedTags.clear();
      detailedData.clear();
      this.updateTable();

      UgiRfidConfiguration config = UgiRfidConfiguration.forInventoryType(UgiRfidConfiguration.InventoryTypes.LOCATE_DISTANCE);
      config.selectBank = UgiRfidConfiguration.MemoryBank.Epc;
      config.selectMask = tag.getEpc().toBytes();
      config.selectOffset = 32;
      Ugi.getSingleton().startInventory(this, config);
      this.updateUI();
      this.updateCountAndTime();
      UgiUiUtil.showToast(this, "Restarted inventory", "Searching for only " + tag.getEpc().toString());
    });
  }


  ///////////////////////////////////////////

  private static String byteArrayToString(byte[] ba) {
    if (ba == null) return null;
    StringBuilder sb = new StringBuilder(ba.length*2);
    for (byte b : ba) {
      sb.append(NibbleToChar((b >> 4) & 0xf));
      sb.append(NibbleToChar(b & 0xf));
    }
    return sb.toString();
  }

  private static char NibbleToChar(int nibble) {
    return (char) (nibble + (nibble < 10 ? '0' : 'a'-10));
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Configure
  ///////////////////////////////////////////////////////////////////////////////////////

  private void doConfigure() {
    UgiUiUtil.showMenu(
            this, "configure", null,
            new UgiUiUtil.MenuTitleAndHandler("inventory type", () -> {
              String[] names = new String[UgiRfidConfiguration.getNumInventoryTypes()];
              for (int i = 0; i < UgiRfidConfiguration.getNumInventoryTypes(); i++) {
                names[i] = UgiRfidConfiguration.getNameForInventoryType(i + 1);
              }
              UgiUiUtil.showChoices(
                      this,
                      names,
                      inventoryType.getInternalCode() - 1,
                      "Inventory Type", null, "set type", true,
                      (choiceIndex, choice) -> inventoryType = UgiRfidConfiguration.InventoryTypes.fromInt(choiceIndex + 1), null, null);
            }),
            new UgiUiUtil.MenuTitleAndHandler("special functions", () -> UgiUiUtil.showChoices(
                    this,
                    new String[]{
                            "None",
                            "Read User Memory",
                            "Read TID memory",
                            "Read RF Micron sensor code",
                            "Read RF Micron temperature"
                    },
                    specialFunction,
                    "Special Functions", null, "", true,
                    (choiceIndex, choice) -> specialFunction = choiceIndex, null, null))
                      );
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Actions
  ///////////////////////////////////////////////////////////////////////////////////////

  public void doActions(@SuppressWarnings("UnusedParameters") View view) {
    Collection<UgiUiUtil.MenuTitleAndHandler> items = new ArrayList<>();
    items.add(new UgiUiUtil.MenuTitleAndHandler("audio reconfiguration", () -> Ugi.getSingleton().invokeAudioReconfiguration()));
    items.add(new UgiUiUtil.MenuTitleAndHandler("set audio jack location", () -> Ugi.getSingleton().invokeAudioJackLocation()));
    items.add(new UgiUiUtil.MenuTitleAndHandler("example: second page", () -> this.startActivityWithTransition(SecondPageActivity.class)));

    if (Ugi.getSingleton().getActiveInventory() == null) {
      items.add(new UgiUiUtil.MenuTitleAndHandler("example: find one tag", () -> this.startActivityWithTransition(FindOneTagActivity.class, null, result -> {
        if (result != null) {
          UgiEpc epc = (UgiEpc) result;
          this.handleTagFound(epc);
        }
      })));
    }
    UgiUiUtil.showMenu(this, null, null, items.toArray(new UgiUiUtil.MenuTitleAndHandler[items.size()]));
  }

  private void handleTagFound(UgiEpc epc) {
    UgiUiUtil.showOk(this, "Tag found", epc.toString());
  }

}
