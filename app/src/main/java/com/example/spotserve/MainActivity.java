package com.example.spotserve;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
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

