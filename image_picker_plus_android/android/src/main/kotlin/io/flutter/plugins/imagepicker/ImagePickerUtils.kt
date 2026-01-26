// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore

internal object ImagePickerUtils {
    /** Returns true if permission is present in manifest, otherwise false */
    private fun isPermissionPresentInManifest(context: Context, permissionName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                getPermissionsPackageInfoPreApi33(packageManager, context.packageName)
            }

            val requestedPermissions = packageInfo.requestedPermissions
            requestedPermissions?.contains(permissionName) == true
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun getPermissionsPackageInfoPreApi33(
        packageManager: PackageManager,
        packageName: String
    ): PackageInfo {
        return packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
    }

    /**
     * Camera permission needs to be requested if it is present in the manifest, even if the camera
     * permission is not used.
     *
     * Camera permission may be used in another package, as example flutter_barcode_reader.
     * https://github.com/flutter/flutter/issues/29837
     *
     * @return true if need to request camera permission, otherwise false
     */
    @JvmStatic
    fun needRequestCameraPermission(context: Context): Boolean {
        return isPermissionPresentInManifest(context, Manifest.permission.CAMERA)
    }

    /**
     * The system photo picker has a maximum limit of selectable items returned by
     * [MediaStore.getPickImagesMaxLimit]. On devices supporting picker provided via
     * ACTION_SYSTEM_FALLBACK_PICK_IMAGES, the limit may be ignored if it's higher than the allowed
     * limit. On devices not supporting the photo picker, the limit is ignored.
     *
     * @see MediaStore.EXTRA_PICK_IMAGES_MAX
     */
    @SuppressLint("NewApi", "ClassVerificationFailure")
    @JvmStatic
    fun getMaxItems(): Int {
        return if (Build.VERSION.SDK_INT >= 33 ||
            (Build.VERSION.SDK_INT >= 30 &&
                    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2)
        ) {
            MediaStore.getPickImagesMaxLimit()
        } else {
            Int.MAX_VALUE
        }
    }

    @JvmStatic
    fun getLimitFromOption(generalOptions: GeneralOptions): Int {
        val limit = generalOptions.limit
        var effectiveLimit = getMaxItems()

        if (limit != null && limit < effectiveLimit) {
            effectiveLimit = limit.toInt()
        }

        return effectiveLimit
    }
}
