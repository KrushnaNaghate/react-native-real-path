package com.rngrp;

import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;

import android.content.ContentUris;
import android.content.Context;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.database.Cursor;
import android.webkit.MimeTypeMap;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

public class GRP extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;
  public GRP(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "GRP";
  }

  private WritableMap makeErrorPayload(Exception ex) {
    WritableMap error = Arguments.createMap();
    error.putString("message", ex.getMessage());
    return error;
  }

  @ReactMethod
  public void getRealPathFromURI(String uriString, Callback callback) {
    Uri uri = Uri.parse(uriString);
    Context context = getReactApplicationContext();
    try {
      final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
      if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
        if (isMediaDocument(uri)) {
          // http://www.banbaise.com/archives/745
          final String docId = DocumentsContract.getDocumentId(uri);
          final String[] split = docId.split(":");
          final String type = split[0];

          Uri contentUri = null;
          if ("image".equals(type)) {
              contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
          } else if ("video".equals(type)) {
              contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
          } else if ("audio".equals(type)) {
              contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
          }

          final String selection = "_id=?";
          final String[] selectionArgs = new String[] { split[1] };

          callback.invoke(null, checkForNull(getDataColumn(context, contentUri, selection, selectionArgs), uri, context));
        } else if (isDownloadsDocument(uri)) {

          final String id = DocumentsContract.getDocumentId(uri);
          final Uri contentUri = ContentUris.withAppendedId(
                  Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

          callback.invoke(null, checkForNull(getDataColumn(context, contentUri, null, null), uri, context));
          ;
        } else if (isExternalStorageDocument(uri)) {
          final String docId = DocumentsContract.getDocumentId(uri);
          final String[] split = docId.split(":");
          final String type = split[0];

          if ("primary".equalsIgnoreCase(type)) {
            callback.invoke(null, checkForNull(Environment.getExternalStorageDirectory() + "/" + split[1], uri, context));
          } else {
            String[] proj = {MediaStore.Images.Media.DATA};
            Cursor cursor = context.getContentResolver().query(uri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();

            callback.invoke(null, checkForNull(path, uri, context));
          }
        }
      }
      else if ("content".equalsIgnoreCase(uri.getScheme())) {
        callback.invoke(null, checkForNull(getDataColumn(context, uri, null, null), uri, context));
      }
      else if ("file".equalsIgnoreCase(uri.getScheme())) {
        callback.invoke(null, checkForNull(uri.getPath(), uri, context));
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex), checkForNull(null, uri, context));
    }
  }

  public static boolean isMediaDocument(Uri uri) {
    return "com.android.providers.media.documents".equals(uri.getAuthority());
  }

  public static boolean isDownloadsDocument(Uri uri) {
    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
  }
  public static boolean isExternalStorageDocument(Uri uri) {
    return "com.android.externalstorage.documents".equals(uri.getAuthority());
  }

  private File createFileFromURI(Uri uri, Context context) throws Exception {
    String fileName = "photo-" + uri.getLastPathSegment();

    String mimeType = getMimeType(uri, context);
    if(mimeType != null && mimeType instanceof String && mimeType.length() > 0) {
      fileName = fileName + "." + mimeType;
    }

    File file = new File(reactContext.getExternalCacheDir(), fileName);
    InputStream input = reactContext.getContentResolver().openInputStream(uri);
    OutputStream output = new FileOutputStream(file);

    try {
      byte[] buffer = new byte[4 * 1024];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      output.flush();
    } finally {
      output.close();
      input.close();
    }

    return file;
  }

  private String checkForNull(String path, Uri uri, Context context) {
    try {
        // if (path == null) {
        File file = createFileFromURI(uri, context);
        return file.getAbsolutePath();
      // } else {
      //   return path;
      // }
    } catch (Exception ex) {

    }
    return "";
  }

  private String getMimeType(Uri uri, Context context) {
    ContentResolver cR = context.getContentResolver();
    MimeTypeMap mime = MimeTypeMap.getSingleton();
    String type = mime.getExtensionFromMimeType(cR.getType(uri));
    return type;
  }

  public static String getDataColumn(Context context, Uri uri, String selection,
                                     String[] selectionArgs) {
    // https://github.com/hiddentao/cordova-plugin-filepath/pull/6
    Cursor cursor = null;
    final String column = "_data";
    final String[] projection = {column, "_display_name"};

    try {
      /* get `_data` */
      cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
      if (cursor != null && cursor.moveToFirst()) {
        final int column_index = cursor.getColumnIndexOrThrow(column);
        /* bingo! */
        final String filepath = cursor.getString(column_index);
        return filepath;
      }
    } catch (Exception e) {
      final int column_index = cursor.getColumnIndexOrThrow("_display_name");
      final String displayName = cursor.getString(column_index);

      InputStream input = null;
      try {
        input = context.getContentResolver().openInputStream(uri);
        /* save stream to temp file */
        try {
          File file = new File(context.getCacheDir(), displayName);
          OutputStream output = new FileOutputStream(file);
          try {
            byte[] buffer = new byte[4 * 1024]; // or other buffer size
            int read;

            while ((read = input.read(buffer)) != -1) {
              output.write(buffer, 0, read);
            }
            output.flush();

            final String outputPath = file.getAbsolutePath();
            return outputPath;
              
          } finally {
            output.close();
          }
        } catch (Exception e1a) {
          //
        } finally {
          try {
            input.close();
          } catch (IOException e1b) {
            //
          }
        }
      } catch (FileNotFoundException e2) {
        //
      } finally {
        if (input != null) {
          try {
            input.close();
          } catch (IOException e3) {
            //
          }
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
    return null;
  }
}
