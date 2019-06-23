package com.example.spotserve.wifi_hotspot;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import android.util.Log;
import com.example.spotserve.MainActivity;
import com.example.spotserve.R;
import java.lang.reflect.Method;

/**
 * WifiHotstopManager class makes use of the Android's WifiManager and WifiConfiguration class
 * to implement the wifi hotspot feature.
 * Created by Adeel Zafar on 28/5/2019.
 */

public class WifiHotspotManager {
  private WifiManager wifiManager;
  private Context context;
  private WifiManager.LocalOnlyHotspotReservation hotspotReservation;
  private boolean oreoenabled = false;
  private WifiConfiguration currentConfig;

  public WifiHotspotManager(Context context) {
    this.context = context;
    wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
  }

  //To get write permission settings, we use this method.
  public void showWritePermissionSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (!Settings.System.canWrite(this.context)) {
        Log.v("DANG", " " + !Settings.System.canWrite(this.context));
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + this.context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.context.startActivity(intent);
      }
    }
  }

  // This method enables/disables the wifi access point
  // It is used for API<26
  public boolean setWifiEnabled(WifiConfiguration wifiConfig, boolean enabled) {
    try {
      if (enabled) { //disables wifi hotspot if it's already enabled
        wifiManager.setWifiEnabled(false);
      }

      Method method = wifiManager.getClass()
          .getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
      //if(isWifiApEnabled())
      //  //Toast.makeText(context,"Hotspot turned off",Toast.LENGTH_SHORT).show();
      //else
      //  Toast.makeText(context,"Hotspot started",Toast.LENGTH_SHORT).show();
      return (Boolean) method.invoke(wifiManager, wifiConfig, enabled);
    } catch (Exception e) {
      Log.e(this.getClass().toString(), "", e);
      return false;
    }
  }

  //Workaround to turn on hotspot for Oreo versions
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void turnOnHotspot() {
    if (!oreoenabled) {
      wifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {

        @Override
        public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
          super.onStarted(reservation);
          hotspotReservation = reservation;
          currentConfig = reservation.getWifiConfiguration();

          Log.v("DANG", "THE PASSWORD IS: "
              + currentConfig.preSharedKey
              + " \n SSID is : "
              + currentConfig.SSID);
          Toast.makeText(context,"Local Hotspot started",Toast.LENGTH_LONG).show();
          hotspotDetailsDialog();
          oreoenabled = true;
        }

        @Override
        public void onStopped() {
          super.onStopped();
          Log.v("DANG", "Local Hotspot Stopped");
          Toast.makeText(context,"Local Hotspot Stopped",Toast.LENGTH_LONG).show();
        }

        @Override
        public void onFailed(int reason) {
          super.onFailed(reason);
          Log.v("DANG", "Local Hotspot failed to start");
          Toast.makeText(context,"Local Hotspot failed to start",Toast.LENGTH_LONG).show();
        }
      }, new Handler());
    }
  }

  //Workaround to turn off hotspot for Oreo versions
  @RequiresApi(api = Build.VERSION_CODES.O)
  public void turnOffHotspot() {
    if (hotspotReservation != null) {
      hotspotReservation.close();
      hotspotReservation = null;
      oreoenabled = false;
    }
  }

  //This method checks the state of the hostpot for devices>=Oreo
  public boolean checkHotspotState() {
    if (hotspotReservation != null) {
      return true;
    } else {
      return false;
    }
  }

  // This method returns the current state of the Wifi access point
  public WIFI_AP_STATE_ENUMS getWifiApState() {
    try {
      Method method = wifiManager.getClass().getMethod("getWifiApState");

      int tmp = ((Integer) method.invoke(wifiManager));

      // Fix for Android 4
      if (tmp >= 10) {
        tmp = tmp - 10;
      }

      return WIFI_AP_STATE_ENUMS.class.getEnumConstants()[tmp];
    } catch (Exception e) {
      Log.e(this.getClass().toString(), "", e);
      return WIFI_AP_STATE_ENUMS.WIFI_AP_STATE_FAILED;
    }
  }

  //This method returns if wifi access point is enabled or not
  public boolean isWifiApEnabled() {
    return getWifiApState() == WIFI_AP_STATE_ENUMS.WIFI_AP_STATE_ENABLED;
  }

  //This method is to get the wifi ap configuration
  public WifiConfiguration getWifiApConfiguration() {
    try {
      Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
      return (WifiConfiguration) method.invoke(wifiManager);
    } catch (Exception e) {
      Log.e(this.getClass().toString(), "", e);
      return null;
    }
  }

  //This method is to set the wifi ap configuration
  public boolean setWifiApConfiguration(WifiConfiguration wifiConfig) {
    try {
      Method method =
          wifiManager.getClass().getMethod("setWifiApConfiguration", WifiConfiguration.class);
      return (Boolean) method.invoke(wifiManager, wifiConfig);
    } catch (Exception e) {
      Log.e(this.getClass().toString(), "", e);
      return false;
    }
  }
  public static int dialogStyle() {
    if (MainActivity.nightMode) {
      return R.style.AppTheme_Dialog_Night;
    } else {
      return R.style.AppTheme_Dialog;
    }
  }

  private void hotspotDetailsDialog() {

    AlertDialog.Builder builder = new AlertDialog.Builder(context, dialogStyle());

    builder.setPositiveButton(android.R.string.ok, (dialog, id) -> {
      //Do nothing
    });
    builder.setTitle(context.getString(R.string.hotspot_turned_on));
    builder.setMessage(
        context.getString(R.string.hotspot_details_message) + "\n" + context.getString(
            R.string.hotspot_ssid_label) + " " + currentConfig.SSID + "\n" + context.getString(
            R.string.hotspot_pass_label) + " " + currentConfig.preSharedKey);
    AlertDialog dialog = builder.create();
    dialog.show();
  }
}
