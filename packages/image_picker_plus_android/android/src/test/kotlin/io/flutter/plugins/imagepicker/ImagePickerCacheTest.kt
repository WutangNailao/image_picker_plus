// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import io.flutter.plugins.imagepicker.ImagePickerCache.Companion.SHARED_PREFERENCES_NAME
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class ImagePickerCacheTest {
    @Mock lateinit var mockActivity: Activity
    @Mock lateinit var mockPreference: SharedPreferences
    @Mock lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var mockCloseable: AutoCloseable

    @Before
    fun setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this)
        preferenceStorage = HashMap()

        `when`(mockActivity.packageName).thenReturn("com.example.test")
        `when`(mockActivity.packageManager).thenReturn(mock(PackageManager::class.java))
        `when`(mockActivity.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE))
            .thenReturn(mockPreference)
        `when`(mockPreference.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putInt(any(String::class.java), anyInt()))
            .thenAnswer {
                preferenceStorage[it.getArgument(0)] = it.getArgument(1)
                mockEditor
            }
        `when`(mockEditor.putLong(any(String::class.java), anyLong()))
            .thenAnswer {
                preferenceStorage[it.getArgument(0)] = it.getArgument(1)
                mockEditor
            }
        `when`(mockEditor.putString(any(String::class.java), any(String::class.java)))
            .thenAnswer {
                preferenceStorage[it.getArgument(0)] = it.getArgument(1)
                mockEditor
            }

        `when`(mockPreference.getInt(any(String::class.java), anyInt()))
            .thenAnswer {
                preferenceStorage[it.getArgument(0)] as? Int ?: it.getArgument(1)
            }
        `when`(mockPreference.getLong(any(String::class.java), anyLong()))
            .thenAnswer {
                preferenceStorage[it.getArgument(0)] as? Long ?: it.getArgument(1)
            }
        `when`(mockPreference.getString(any(String::class.java), any(String::class.java)))
            .thenAnswer {
                preferenceStorage[it.getArgument(0)] as? String ?: it.getArgument(1)
            }
        `when`(mockPreference.contains(any(String::class.java))).thenReturn(true)
    }

    @After
    fun tearDown() {
        mockCloseable.close()
    }

    @Test
    fun imageCache_shouldBeAbleToSetAndGetQuality() {
        val quality = 90
        val cache = ImagePickerCache(mockActivity)
        cache.saveDimensionWithOutputOptions(ImageSelectionOptions(quality = quality.toLong()))
        val resultMap = cache.getCacheMap()
        val imageQuality = resultMap[ImagePickerCache.MAP_KEY_IMAGE_QUALITY] as Int
        assertThat(imageQuality, equalTo(quality))

        cache.saveDimensionWithOutputOptions(ImageSelectionOptions(quality = 100))
        val resultMapWithDefaultQuality = cache.getCacheMap()
        val defaultImageQuality =
            resultMapWithDefaultQuality[ImagePickerCache.MAP_KEY_IMAGE_QUALITY] as Int
        assertThat(defaultImageQuality, equalTo(100))
    }

    @Test
    fun imageCache_shouldNotThrowIfPathIsNullInSaveResult() {
        val cache = ImagePickerCache(mockActivity)
        cache.saveResult(null, "errorCode", "errorMessage")
        assertTrue(
            "No exception thrown when ImagePickerCache.saveResult() was passed a null path",
            true
        )
    }

    companion object {
        private lateinit var preferenceStorage: MutableMap<String, Any>
    }
}
