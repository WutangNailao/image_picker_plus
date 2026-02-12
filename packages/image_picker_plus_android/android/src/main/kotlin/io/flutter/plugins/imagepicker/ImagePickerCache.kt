// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.VisibleForTesting

class ImagePickerCache(private val context: Context) {
    enum class CacheType {
        IMAGE,
        VIDEO
    }

    fun saveType(type: CacheType) {
        val typeValue = when (type) {
            CacheType.IMAGE -> MAP_TYPE_VALUE_IMAGE
            CacheType.VIDEO -> MAP_TYPE_VALUE_VIDEO
        }
        setType(typeValue)
    }

    private fun setType(type: String) {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SHARED_PREFERENCE_TYPE_KEY, type).apply()
    }

    fun saveDimensionWithOutputOptions(options: ImageSelectionOptions) {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            options.maxWidth?.let {
                putLong(SHARED_PREFERENCE_MAX_WIDTH_KEY, it.toRawBits())
            }
            options.maxHeight?.let {
                putLong(SHARED_PREFERENCE_MAX_HEIGHT_KEY, it.toRawBits())
            }
            putInt(SHARED_PREFERENCE_IMAGE_QUALITY_KEY, options.quality.toInt())
        }.apply()
    }

    fun savePendingCameraMediaUriPath(uri: Uri) {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SHARED_PREFERENCE_PENDING_IMAGE_URI_PATH_KEY, uri.path).apply()
    }

    fun retrievePendingCameraMediaUriPath(): String {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SHARED_PREFERENCE_PENDING_IMAGE_URI_PATH_KEY, "") ?: ""
    }

    fun saveResult(
        path: ArrayList<String>?,
        errorCode: String?,
        errorMessage: String?
    ) {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

        prefs.edit().apply {
            path?.let {
                val imageSet = it.toSet()
                putStringSet(FLUTTER_IMAGE_PICKER_IMAGE_PATH_KEY, imageSet)
            }
            errorCode?.let {
                putString(SHARED_PREFERENCE_ERROR_CODE_KEY, it)
            }
            errorMessage?.let {
                putString(SHARED_PREFERENCE_ERROR_MESSAGE_KEY, it)
            }
        }.apply()
    }

    fun clear() {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun getCacheMap(): Map<String, Any> {
        val resultMap = mutableMapOf<String, Any>()
        var hasData = false

        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

        if (prefs.contains(FLUTTER_IMAGE_PICKER_IMAGE_PATH_KEY)) {
            val imagePathList = prefs.getStringSet(FLUTTER_IMAGE_PICKER_IMAGE_PATH_KEY, null)
            if (imagePathList != null) {
                val pathList = ArrayList(imagePathList)
                resultMap[MAP_KEY_PATH_LIST] = pathList
                hasData = true
            }
        }

        if (prefs.contains(SHARED_PREFERENCE_ERROR_CODE_KEY)) {
            val errorCode = prefs.getString(SHARED_PREFERENCE_ERROR_CODE_KEY, "") ?: ""
            val errorMessage = if (prefs.contains(SHARED_PREFERENCE_ERROR_MESSAGE_KEY)) {
                prefs.getString(SHARED_PREFERENCE_ERROR_MESSAGE_KEY, "") ?: ""
            } else {
                null
            }
            hasData = true
            resultMap[MAP_KEY_ERROR] = CacheRetrievalError(errorCode, errorMessage)
        }

        if (hasData) {
            if (prefs.contains(SHARED_PREFERENCE_TYPE_KEY)) {
                val typeValue = prefs.getString(SHARED_PREFERENCE_TYPE_KEY, "") ?: ""
                resultMap[MAP_KEY_TYPE] = if (typeValue == MAP_TYPE_VALUE_VIDEO) {
                    CacheRetrievalType.VIDEO
                } else {
                    CacheRetrievalType.IMAGE
                }
            }
            if (prefs.contains(SHARED_PREFERENCE_MAX_WIDTH_KEY)) {
                val maxWidthValue = prefs.getLong(SHARED_PREFERENCE_MAX_WIDTH_KEY, 0)
                resultMap[MAP_KEY_MAX_WIDTH] = Double.fromBits(maxWidthValue)
            }
            if (prefs.contains(SHARED_PREFERENCE_MAX_HEIGHT_KEY)) {
                val maxHeightValue = prefs.getLong(SHARED_PREFERENCE_MAX_HEIGHT_KEY, 0)
                resultMap[MAP_KEY_MAX_HEIGHT] = Double.fromBits(maxHeightValue)
            }
            val imageQuality = prefs.getInt(SHARED_PREFERENCE_IMAGE_QUALITY_KEY, 100)
            resultMap[MAP_KEY_IMAGE_QUALITY] = imageQuality
        }
        return resultMap
    }

    companion object {
        const val MAP_KEY_PATH_LIST = "pathList"
        const val MAP_KEY_MAX_WIDTH = "maxWidth"
        const val MAP_KEY_MAX_HEIGHT = "maxHeight"
        const val MAP_KEY_IMAGE_QUALITY = "imageQuality"
        const val MAP_KEY_TYPE = "type"
        const val MAP_KEY_ERROR = "error"

        private const val MAP_TYPE_VALUE_IMAGE = "image"
        private const val MAP_TYPE_VALUE_VIDEO = "video"

        private const val FLUTTER_IMAGE_PICKER_IMAGE_PATH_KEY =
            "flutter_image_picker_image_path"
        private const val SHARED_PREFERENCE_ERROR_CODE_KEY = "flutter_image_picker_error_code"
        private const val SHARED_PREFERENCE_ERROR_MESSAGE_KEY =
            "flutter_image_picker_error_message"

        private const val SHARED_PREFERENCE_MAX_WIDTH_KEY = "flutter_image_picker_max_width"

        private const val SHARED_PREFERENCE_MAX_HEIGHT_KEY = "flutter_image_picker_max_height"

        private const val SHARED_PREFERENCE_IMAGE_QUALITY_KEY =
            "flutter_image_picker_image_quality"

        private const val SHARED_PREFERENCE_TYPE_KEY = "flutter_image_picker_type"
        private const val SHARED_PREFERENCE_PENDING_IMAGE_URI_PATH_KEY =
            "flutter_image_picker_pending_image_uri"

        @VisibleForTesting
        const val SHARED_PREFERENCES_NAME = "flutter_image_picker_shared_preference"
    }
}
