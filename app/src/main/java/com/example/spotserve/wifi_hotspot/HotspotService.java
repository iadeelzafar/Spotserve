package com.example.spotserve.wifi_hotspot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import com.example.spotserve.MainActivity;
import com.example.spotserve.R;

import static com.example.spotserve.MainActivity.ACTION_TURN_OFF_AFTER_O;
import static com.example.spotserve.MainActivity.ACTION_TURN_OFF_BEFORE_O;
import static com.example.spotserve.MainActivity.ACTION_TURN_ON_AFTER_O;
import static com.example.spotserve.MainActivity.ACTION_TURN_ON_BEFORE_O;
import static com.example.spotserve.MainActivity.startHotspotDetails;
import static com.example.spotserve.MainActivity.stopAndroidWebServer;

/**
 * HotspotService is used to add a foreground service for the wifi hotspot.
 * Created by Adeel Zafar on 07/01/2019.
 */

public class HotspotService extends Service {
  private static final int HOTSPOT_NOTIFICATION_ID = 666;
  public static final String ACTION_STOP = "hotspot_stop";
  public static WifiHotspotManager hotspotManager;
  private BroadcastReceiver stopReceiver;
  private NotificationManager notificationManager;
  private NotificationCompat.Builder builder;

  @Override public void onCreate() {
    super.onCreate();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      hotspotManager = new WifiHotspotManager(this);
    }
    stopReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction().equals(ACTION_STOP)) {
          stopHotspot();
        }
      }
    };
    registerReceiver(stopReceiver, new IntentFilter(ACTION_STOP));
    notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    startForeground(HOTSPOT_NOTIFICATION_ID,
        buildForegroundNotification("Starting hotspot", false));
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    switch (intent.getAction()) {
      case ACTION_TURN_ON_BEFORE_O:
        if (hotspotManager.setWifiEnabled(null, true)) {
          startHotspotDetails();
          updateNotification("Hotspot running", true);
        }
        break;
      case ACTION_TURN_ON_AFTER_O:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          hotspotManager.turnOnHotspot();
          updateNotification("Hotspot running", true);
        }
        break;
      case ACTION_TURN_OFF_BEFORE_O:
        if (hotspotManager.setWifiEnabled(null, false)) {
          stopForeground(true);
          stopSelf();
          stopAndroidWebServer();
        }
        break;
      case ACTION_TURN_OFF_AFTER_O:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          hotspotManager.turnOffHotspot();
        }
        stopForeground(true);
        stopSelf();
        stopAndroidWebServer();
        break;
      default:
        break;
    }
    return START_NOT_STICKY;
  }

  @Nullable @Override public IBinder onBind(Intent intent) {
    return null;
  }

  private Notification buildForegroundNotification(String status, boolean showStopButton) {
    Log.v("DANG", "Building notification " + status);
    builder = new NotificationCompat.Builder(this);
    builder.setContentTitle("Spotserve Hotspot").setContentText(status);
    Intent targetIntent = new Intent(this, MainActivity.class);
    targetIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent contentIntent =
        PendingIntent.getActivity(this, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    builder.setContentIntent(contentIntent)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setWhen(System.currentTimeMillis());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      hotspotNotificationChannel();
    }
    if (showStopButton) {
      Intent stopIntent = new Intent(ACTION_STOP);
      PendingIntent stopHotspot =
          PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);
      builder.addAction(R.drawable.common_google_signin_btn_icon_dark, "Stop", stopHotspot);
    }
    return (builder.build());
  }

  private void updateNotification(String status, boolean stopAction) {
    notificationManager.notify(HOTSPOT_NOTIFICATION_ID,
        buildForegroundNotification(status, stopAction));
  }

  private void stopHotspot() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      hotspotManager.turnOffHotspot();
    } else {
      hotspotManager.setWifiEnabled(null, false);
    }
    stopForeground(true);
    stopSelf();
    stopAndroidWebServer();
  }

  @Override
  public void onDestroy() {
    if (stopReceiver != null) {
      unregisterReceiver(stopReceiver);
    }
    super.onDestroy();
  }

  private void hotspotNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel hotspotServiceChannel = new NotificationChannel(
          "hotspot_channel_id", "Spotserve Hotspot Channel",
          NotificationManager.IMPORTANCE_DEFAULT);
      hotspotServiceChannel.setDescription("Sample hotspot description");
      hotspotServiceChannel.setSound(null, null);
      builder.setChannelId("hotspot_channel_id");
      notificationManager.createNotificationChannel(hotspotServiceChannel);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public static boolean checkHotspotState(Context context) {
    if (hotspotManager == null) {
      Log.v("DANG", "hotspotManager initialized");
      hotspotManager = new WifiHotspotManager(context);
    }
    return hotspotManager.checkHotspotState();
  }
}
