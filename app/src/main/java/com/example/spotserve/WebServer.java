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
  public Response serve(IHTTPSession session) {
    String answer = "";
    try {
      FileReader index = new FileReader(selectedFilePath);
      BufferedReader reader = new BufferedReader(index);
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
