package com.example.spotserve;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.spotserve.wifi_hotspot.WifiHotspotManager;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

  private static final int DEFAULT_PORT = 8080;
  private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 65;

  // INSTANCE OF ANDROID WEB SERVER
  private WebServer webServer;
  private BroadcastReceiver broadcastReceiverNetworkState;
  private static boolean isStarted = false;
  private Context context = this;

  // VIEW
  private CoordinatorLayout coordinatorLayout;
  private EditText editTextPort;
  private Button buttonOnOff;
  private View textViewMessage;
  private TextView textViewIpAccess;
  private Button copyButton;

  public static boolean nightMode;

  private final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 102;
  private Task<LocationSettingsResponse> task;

  private WifiHotspotManager wifiHotspotManager;

  private static final int PICK_FILE_REQUEST = 1;
  private String selectedFilePath;
  private int port;

  Button attachmentButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // INIT VIEW
    coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorLayout);
    editTextPort = (EditText) findViewById(R.id.editTextPort);
    textViewMessage = findViewById(R.id.textViewMessage);
    textViewIpAccess = (TextView) findViewById(R.id.textViewIpAccess);
    copyButton = (Button) findViewById(R.id.copy_button);

    setIpAccess();

    wifiHotspotManager = new WifiHotspotManager(this);
    wifiHotspotManager.showWritePermissionSettings();


    attachmentButton = (Button) findViewById(R.id.attachment_button);

    checkWriteExternalStoragePermission();

    attachmentButton.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        showFileChooser();
      }
    });

    buttonOnOff = (Button) findViewById(R.id.floatingActionButtonOnOff);
    buttonOnOff.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {

        if (!isStarted && startAndroidWebServer()) {
          isStarted = true;
          textViewMessage.setVisibility(View.VISIBLE);
          copyButton.setVisibility(View.VISIBLE);
          buttonOnOff.setBackgroundColor(getResources().getColor(R.color.colorGreen));
          buttonOnOff.setText("Turn Off");
          editTextPort.setEnabled(false);
        } else if (stopAndroidWebServer()) {
          isStarted = false;
          textViewMessage.setVisibility(View.INVISIBLE);
          copyButton.setVisibility(View.INVISIBLE);
          buttonOnOff.setBackgroundColor(getResources().getColor(R.color.colorRed));
          buttonOnOff.setText("Turn On");
          editTextPort.setEnabled(true);
        }
      }
    });

    copyButton.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("ip_address_label", getIpAddress()+":"+getPortFromEditText());
        clipboard.setPrimaryClip(clip);
      }
    });

    // INIT BROADCAST RECEIVER TO LISTEN NETWORK STATE CHANGED
    initBroadcastReceiverNetworkStateChanged();
  }

  public boolean onCreateOptionsMenu(Menu menu) {

    menu.add(0, 1, 0, "Open AP");
    return super.onCreateOptionsMenu(menu);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch(item.getItemId()) {
      case 1:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          toggleHotspot();
        } else {
          switchHotspot();
        }
    }
        return super.onOptionsItemSelected(item);

  }

  // For API<26 we use this
  // Checks if wifi access point is already enabled then turns it off, otherwise enables it.
  private void switchHotspot() {
    if (wifiHotspotManager.isWifiApEnabled()) {
      wifiHotspotManager.setWifiEnabled(null, false);
    } else {
      //Check if user's hotspot is enabled
      if (isMobileDataEnabled(this)) {

        mobileDataDialog();
      } else {
        wifiHotspotManager.setWifiEnabled(null, true);
      }
    }
  }

  // This method checks if mobile data is enabled in user's device.
  public static boolean isMobileDataEnabled(Context context) {
    boolean enabled = false;
    ConnectivityManager cm =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    try {
      Class cmClass = Class.forName(cm.getClass().getName());
      Method method = cmClass.getDeclaredMethod("getMobileDataEnabled");
      method.setAccessible(true);
      enabled = (Boolean) method.invoke(cm);
    } catch (Exception e) {
      Log.e("DANG ", e.toString());
    }
    return enabled;
  }

  //This method sends the user to data usage summary settings activity
  public void disableMobileData() {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName("com.android.settings",
        "com.android.settings.Settings$DataUsageSummaryActivity"));
    startActivity(intent);
  }

  public static int dialogStyle() {
    if (MainActivity.nightMode) {
      return R.style.AppTheme_Dialog_Night;
    } else {
      return R.style.AppTheme_Dialog;
    }
  }

  private void mobileDataDialog() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      AlertDialog.Builder builder = new AlertDialog.Builder(this, dialogStyle());

      builder.setPositiveButton(this.getString(R.string.yes), (dialog, id) -> {
        disableMobileData();
      });
      builder.setNegativeButton(android.R.string.no, (dialog, id) -> {
        wifiHotspotManager.setWifiEnabled(null, true);
      });
      builder.setTitle(this.getString(R.string.mobile_data_enabled));
      builder.setMessage(
          this.getString(R.string.mobile_data_message) + "\n" + this.getString(
              R.string.mobile_data_message_confirmation)
      );
      AlertDialog dialog = builder.create();
      dialog.show();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private void toggleHotspot() {
    boolean check = false;
    //Check if location permissions are granted
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED) {
      if (wifiHotspotManager.checkHotspotState()) //If hotspot is already enabled, turn it off
      {
        wifiHotspotManager.turnOffHotspot();
      } else //If hotspot is not already enabled, then turn it on.
      {
        setupLocationServices();
      }
    } else {
      //This var makes sure recursion occurs only once.
      if (!check) {
        //Show rationale and request location permission.
        //No explanation needed; request the permission
        ActivityCompat.requestPermissions(this,
            new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
            MY_PERMISSIONS_ACCESS_FINE_LOCATION);

        check = true;

        //Go to toggle hotspot to check if permission is granted.
        toggleHotspot();
      }
    }
  }

  private void setupLocationServices() {
    LocationRequest mLocationRequest = new LocationRequest();
    mLocationRequest.setInterval(10);
    mLocationRequest.setSmallestDisplacement(10);
    mLocationRequest.setFastestInterval(10);
    mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    LocationSettingsRequest.Builder builder = new
        LocationSettingsRequest.Builder();
    builder.addLocationRequest(mLocationRequest);

    task = LocationServices.getSettingsClient(this).checkLocationSettings(builder.build());

    locationSettingsResponseBuilder();
  }

  private void locationSettingsResponseBuilder() {
    task.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
      @Override
      public void onComplete(Task<LocationSettingsResponse> task) {
        try {
          LocationSettingsResponse response = task.getResult(ApiException.class);
          // All location settings are satisfied. The client can initialize location
          // requests here.

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wifiHotspotManager.turnOnHotspot();
          }
        } catch (ApiException exception) {
          switch (exception.getStatusCode()) {
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
              // Location settings are not satisfied. But could be fixed by showing the
              // user a dialog.
              try {
                // Cast to a resolvable exception.
                ResolvableApiException resolvable = (ResolvableApiException) exception;
                // Show the dialog by calling startResolutionForResult(),
                // and check the result in onActivityResult().
                resolvable.startResolutionForResult(
                    MainActivity.this,
                    101);
              } catch (IntentSender.SendIntentException e) {
                // Ignore the error.
              } catch (ClassCastException e) {
                // Ignore, should be an impossible error.
              }
              break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
              // Location settings are not satisfied. However, we have no way to fix the
              // settings so we won't show the dialog.
              break;
          }
        }
      }
    });
  }

  private void checkWriteExternalStoragePermission() {
    // Here, thisActivity is the current activity
    if (ContextCompat.checkSelfPermission(this,
        Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {

      // No explanation needed; request the permission
      ActivityCompat.requestPermissions(this,
          new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
          MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
    } else {
      // Permission has already been granted
    }
  }

  //region Start And Stop WebServer
  private boolean startAndroidWebServer() {
    if (!isStarted) {
      port = getPortFromEditText();
      try {
        if (port == 0) {
          throw new Exception();
        }

        webServer = new WebServer(port, selectedFilePath);
        webServer.start();
        //dialog.dismiss();
        return true;
      } catch (Exception e) {
        e.printStackTrace();
        Snackbar.make(coordinatorLayout,
            "The PORT " + port + " doesn't work, please change it between 1000 and 9999.",
            Snackbar.LENGTH_LONG).show();
      }
    }
    return false;
  }

  //chooses file
  private void showFileChooser() {
    Log.v("DANG", "Coming 7");
    Intent intent = new Intent();
    //sets the select file to all types of files
    intent.setType("file/*");
    //allows to select data and return it
    intent.setAction(Intent.ACTION_GET_CONTENT);
    //starts new activity to select file and return data
    startActivityForResult(Intent.createChooser(intent, "Choose File to Upload.."),
        PICK_FILE_REQUEST);
  }

  private boolean stopAndroidWebServer() {
    if (isStarted && webServer != null) {
      webServer.stop();
      return true;
    }
    return false;
  }
  //endregion

  //region Private utils Method
  private void setIpAccess() {
    textViewIpAccess.setText(getIpAddress());
  }

  private void initBroadcastReceiverNetworkStateChanged() {
    final IntentFilter filters = new IntentFilter();
    filters.addAction("android.net.wifi.WIFI_STATE_CHANGED");
    filters.addAction("android.net.wifi.STATE_CHANGE");
    broadcastReceiverNetworkState = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        setIpAccess();
      }
    };
    super.registerReceiver(broadcastReceiverNetworkState, filters);
  }

  private int getPortFromEditText() {
    String valueEditText = editTextPort.getText().toString();
    return (valueEditText.length() > 0) ? Integer.parseInt(valueEditText) : DEFAULT_PORT;
  }

  public boolean isConnectedInWifi() {
    WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    NetworkInfo networkInfo = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE))
        .getActiveNetworkInfo();
    if (networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()
        && wifiManager.isWifiEnabled() && networkInfo.getTypeName().equals("WIFI")) {
      return true;
    }
    return false;
  }
  //endregion

  public boolean onKeyDown(int keyCode, KeyEvent evt) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (isStarted) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.warning)
            .setMessage(R.string.dialog_exit_message)
            .setPositiveButton(getResources().getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int id) {
                    finish();
                  }
                })
            .setNegativeButton(getResources().getString(android.R.string.cancel), null)
            .show();
      } else {
        finish();
      }
      return true;
    }
    return false;
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    stopAndroidWebServer();
    isStarted = false;
    if (broadcastReceiverNetworkState != null) {
      unregisterReceiver(broadcastReceiverNetworkState);
    }
  }

  // get Ip address of the device's wireless access point i.e. wifi hotspot OR wifi network
  private String getIpAddress() {
    String ip = "";
    try {
      Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
          .getNetworkInterfaces();
      while (enumNetworkInterfaces.hasMoreElements()) {
        NetworkInterface networkInterface = enumNetworkInterfaces
            .nextElement();
        Enumeration<InetAddress> enumInetAddress = networkInterface
            .getInetAddresses();
        while (enumInetAddress.hasMoreElements()) {
          InetAddress inetAddress = enumInetAddress.nextElement();

          if (inetAddress.isSiteLocalAddress()) {
            ip += inetAddress.getHostAddress() + "\n";
          }
        }
      }
    } catch (SocketException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      ip += "Something Wrong! " + e.toString() + "\n";
    }
    return  "http://"+ip;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == Activity.RESULT_OK) {
      if (requestCode == PICK_FILE_REQUEST) {
        if (data == null) {
          //no data present
          return;
        }

        Uri selectedFileUri = data.getData();
        selectedFilePath = FilePath.getPath(this, selectedFileUri);
        Log.i("DANG", "Selected File Path:" + selectedFilePath);

        if (selectedFilePath != null && !selectedFilePath.equals("")) {
          Toast.makeText(this, selectedFilePath, Toast.LENGTH_LONG).show();
        } else {
          Toast.makeText(this, "Cannot upload file to server", Toast.LENGTH_SHORT).show();
        }
      }
    }
  }
}

