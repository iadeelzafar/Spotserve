package com.example.spotserve;

import android.content.Context;
import android.util.Log;
import android.webkit.MimeTypeMap;
import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

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
  public Response serve(IHTTPSession session) {

    selectedFile = new File(selectedFilePath);

    FileInputStream fileInputStream = null;
    try {
      fileInputStream = new FileInputStream(selectedFile);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return createResponse(Response.Status.OK, type, fileInputStream);
  }

  //Announce that the file server accepts partial content requests
  private Response createResponse(Response.Status status, String mimeType,
      FileInputStream message) {
    return newChunkedResponse(status, mimeType, message);
  }
}
