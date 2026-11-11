package ws.harbor.insta360x5.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object MediaPermissions {
    const val REQUEST_CODE = 2401

    fun needed(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            Build.VERSION.SDK_INT >= 23 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> emptyArray()
        }
    }

    fun ensure(activity: Activity): Boolean {
        val perms = needed().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (perms.isEmpty()) return true
        ActivityCompat.requestPermissions(activity, perms.toTypedArray(), REQUEST_CODE)
        return false
    }
}
