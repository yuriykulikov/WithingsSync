package com.github.yuriykulikov.withingssync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

fun isActivityRecognitionPermissionApproved(context: Context): Boolean {
  return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
      ActivityCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
          PackageManager.PERMISSION_GRANTED
}
