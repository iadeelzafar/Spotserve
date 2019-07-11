package com.example.spotserve;

import android.content.Context;
import android.util.Log;
import android.webkit.MimeTypeMap;
import fi.iki.elonen.NanoHTTPD;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public class WebServer extends NanoHTTPD {

  File selectedFile;
  String selectedFilePath;
  String type;

  public WebServer(int port, String selectedFilePath) {
    super(port);
    this.selectedFilePath = selectedFilePath;
    findMimeType();
  }

  private void findMimeType() {
    String extension = MimeTypeMap.getFileExtensionFromUrl(selectedFilePath);
    if (extension != null) {
      type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
      Log.v("DANG", "The type is : " + type);
    } else {
      Log.v("DANG", "In else");
    }
  }

  @Override
  public Response serve(String uri, Method method,
      Map<String, String> header,
      Map<String, String> parameters,
      Map<String, String> files) {
    String answer = "";
    selectedFile = new File(selectedFilePath);
    FileInputStream fileInputStream = null;
    try {
      // Open file from SD Card
      //File root = Environment.getExternalStorageDirectory();
      FileReader index = new FileReader(selectedFilePath);
      BufferedReader reader = new BufferedReader(index);
      fileInputStream = new FileInputStream(selectedFile);
      String line = "";
      while ((line = reader.readLine()) != null) {
        answer += line;
      }
      reader.close();

    } catch(IOException ioe) {
      Log.w("Httpd", ioe.toString());
    }


    return newFixedLengthResponse(answer);
  }
}
