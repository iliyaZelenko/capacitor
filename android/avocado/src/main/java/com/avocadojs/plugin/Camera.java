package com.avocadojs.plugin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.util.Log;

import com.avocadojs.Bridge;
import com.avocadojs.NativePlugin;
import com.avocadojs.Plugin;
import com.avocadojs.PluginCall;
import com.avocadojs.PluginMethod;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Camera plugin that opens the stock Camera app.
 * https://developer.android.com/training/camera/photobasics.html
 */
@NativePlugin(
    id="com.avocadojs.plugin.camera",
    requestCodes={Camera.REQUEST_IMAGE_CAPTURE}
)
public class Camera extends Plugin {
  static final int REQUEST_IMAGE_CAPTURE = 9001;

  private static final String PERMISSION_DENIED_ERROR = "Unable to access camera, user denied permission request";
  private static final String NO_CAMERA_ERROR = "Device doesn't have a camera available";
  private static final String NO_CAMERA_ACTIVITY_ERROR = "Unable to resolve camera activity";
  private static final String IMAGE_FILE_SAVE_ERROR = "Unable to create photo on disk";

  private static final boolean DEFAULT_SAVE_IMAGE_TO_GALLERY = true;

  private PluginCall lastCall;

  private String imageFileSavePath;

  @PluginMethod()
  public void getPhoto(PluginCall call) {
    lastCall = call;

    if(!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
      call.error(NO_CAMERA_ERROR);
      return;
    }


    if(!hasPermission(Manifest.permission.CAMERA)) {
      log("Missing camera permission");
      requestPermission(Manifest.permission.CAMERA, REQUEST_IMAGE_CAPTURE);
      return;
    }

    openCamera(call);
  }

  @Override
  protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.handleRequestPermissionsResult(requestCode, permissions, grantResults);

    log("handling request perms result");

    if(lastCall == null) {
      log("No stored plugin call for permissions request result");
      return;
    }

    for(int result : grantResults) {
      if(result == PackageManager.PERMISSION_DENIED) {
        this.lastCall.error(PERMISSION_DENIED_ERROR);
        return;
      }
    }

    if(requestCode == REQUEST_IMAGE_CAPTURE) {
      openCamera(lastCall);
    }
  }

  @Override
  protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
    super.handleOnActivityResult(requestCode, resultCode, data);

    if(requestCode == REQUEST_IMAGE_CAPTURE) {
      processImage(lastCall, data);
    }
  }

  public void openCamera(PluginCall call) {
    boolean saveToGallery = call.getBoolean("saveToGallery", DEFAULT_SAVE_IMAGE_TO_GALLERY);

    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
      // If we will be saving the photo, send the target file along
      try {
        String appId = getAppId();
        File photoFile = createImageFile(saveToGallery);
        imageFileSavePath = photoFile.getAbsolutePath();
        // TODO: Verify provider config exists
        Uri photoURI = FileProvider.getUriForFile(getActivity(), appId + ".fileprovider", photoFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
      } catch (IOException ex) {
        call.error(IMAGE_FILE_SAVE_ERROR, ex);
        return;
      }

      getActivity().startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
    } else {
      call.error(NO_CAMERA_ACTIVITY_ERROR);
    }
  }

  public void processImage(PluginCall call, Intent data) {
    int quality = call.getInt("quality", 100);
    boolean allowEditing = call.getBoolean("allowEditing", false);
    boolean saveToGallery = call.getBoolean("saveToGallery", DEFAULT_SAVE_IMAGE_TO_GALLERY);

    log("Processing image");
    if(saveToGallery && imageFileSavePath != null) {
      log("Saving image to gallery");
      Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
      File f = new File(imageFileSavePath);
      Uri contentUri = Uri.fromFile(f);
      mediaScanIntent.setData(contentUri);
      getActivity().sendBroadcast(mediaScanIntent);
    }

    /*
    Thumbnail
    Bundle extras = data.getExtras();
    Bitmap imageBitmap = (Bitmap) extras.get("data");
    */
  }

  private File createImageFile(boolean saveToGallery) throws IOException {
    // Create an image file name
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    String imageFileName = "JPEG_" + timeStamp + "_";
    File storageDir;
    if(saveToGallery) {
      storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    }  else {
      storageDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    }

    File image = File.createTempFile(
        imageFileName,  /* prefix */
        ".jpg",         /* suffix */
        storageDir      /* directory */
    );

    return image;
  }
}
