// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.app.Activity
import android.app.Application
import androidx.lifecycle.Lifecycle
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.HiddenLifecycleReference
import io.flutter.plugin.common.BinaryMessenger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class ImagePickerPluginTest {
    @Mock lateinit var mockActivityBinding: ActivityPluginBinding
    @Mock lateinit var mockPluginBinding: FlutterPluginBinding
    @Mock lateinit var mockActivity: Activity
    @Mock lateinit var mockApplication: Application
    @Mock lateinit var mockImagePickerDelegate: ImagePickerDelegate

    private lateinit var plugin: ImagePickerPlugin
    private lateinit var mockCloseable: AutoCloseable
    private lateinit var mockResult: TestCallback

    @Before
    fun setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this)
        `when`(mockActivityBinding.activity).thenReturn(mockActivity)
        `when`(mockPluginBinding.applicationContext).thenReturn(mockApplication)
        plugin = ImagePickerPlugin(mockImagePickerDelegate, mockActivity)
        mockResult = TestCallback()
    }

    @After
    fun tearDown() {
        mockCloseable.close()
    }

    @Test
    fun pickImages_whenActivityIsNull_finishesWithForegroundActivityRequiredError() {
        val imagePickerPluginWithNullActivity = ImagePickerPlugin()
        imagePickerPluginWithNullActivity.pickImages(
            SOURCE_GALLERY,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_ALLOW_MULTIPLE_USE_PHOTO_PICKER,
            mockResult
        )

        val error = mockResult.singleResult().exceptionOrNull() as FlutterError
        assertEquals("no_activity", error.code)
        assertEquals("image_picker plugin requires a foreground activity.", error.message)
        verifyNoInteractions(mockImagePickerDelegate)
    }

    @Test
    fun pickVideos_whenActivityIsNull_finishesWithForegroundActivityRequiredError() {
        val imagePickerPluginWithNullActivity = ImagePickerPlugin()
        imagePickerPluginWithNullActivity.pickVideos(
            SOURCE_CAMERA_REAR,
            DEFAULT_VIDEO_OPTIONS,
            GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )

        val error = mockResult.singleResult().exceptionOrNull() as FlutterError
        assertEquals("no_activity", error.code)
        assertEquals("image_picker plugin requires a foreground activity.", error.message)
        verifyNoInteractions(mockImagePickerDelegate)
    }

    @Test
    fun retrieveLostResults_whenActivityIsNull_finishesWithForegroundActivityRequiredError() {
        val imagePickerPluginWithNullActivity = ImagePickerPlugin()
        val error = assertThrows(FlutterError::class.java) {
            imagePickerPluginWithNullActivity.retrieveLostResults()
        }
        assertEquals("image_picker plugin requires a foreground activity.", error.message)
        assertEquals("no_activity", error.code)
        verifyNoInteractions(mockImagePickerDelegate)
    }

    @Test
    fun pickImages_whenSourceIsGallery_invokesChooseImageFromGallery() {
        plugin.pickImages(
            SOURCE_GALLERY,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate).chooseImageFromGallery(DEFAULT_IMAGE_OPTIONS, false, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun pickImages_whenSourceIsGalleryUsingPhotoPicker_invokesChooseImageFromGallery() {
        plugin.pickImages(
            SOURCE_GALLERY,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate).chooseImageFromGallery(DEFAULT_IMAGE_OPTIONS, true, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun pickImages_invokesChooseMultiImageFromGallery() {
        plugin.pickImages(
            SOURCE_GALLERY,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate)
            .chooseMultiImageFromGallery(DEFAULT_IMAGE_OPTIONS, false, Int.MAX_VALUE, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun pickImages_usingPhotoPicker_invokesChooseMultiImageFromGallery() {
        plugin.pickImages(
            SOURCE_GALLERY,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_ALLOW_MULTIPLE_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate)
            .chooseMultiImageFromGallery(DEFAULT_IMAGE_OPTIONS, true, Int.MAX_VALUE, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun pickImages_usingPhotoPicker_withLimit5_invokesChooseMultiImageFromGallery() {
        plugin.pickImages(
            SOURCE_GALLERY,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_ALLOW_MULTIPLE_USE_PHOTO_PICKER_WITH_LIMIT,
            mockResult
        )
        verify(mockImagePickerDelegate).chooseMultiImageFromGallery(DEFAULT_IMAGE_OPTIONS, true, 5, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun pickImages_withLimit5_invokesChooseMultiImageFromGallery() {
        plugin.pickImages(
            SOURCE_GALLERY,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER_WITH_LIMIT,
            mockResult
        )
        verify(mockImagePickerDelegate).chooseMultiImageFromGallery(DEFAULT_IMAGE_OPTIONS, false, 5, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun pickMedia_invokesChooseMediaFromGallery() {
        val mediaSelectionOptions = MediaSelectionOptions(DEFAULT_IMAGE_OPTIONS)
        plugin.pickMedia(
            mediaSelectionOptions,
            GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate)
            .chooseMediaFromGallery(
                mediaSelectionOptions,
                GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
                mockResult
            )
        mockResult.assertNoResults()
    }

    @Test
    fun pickMedia_usingPhotoPicker_invokesChooseMediaFromGallery() {
        val mediaSelectionOptions = MediaSelectionOptions(DEFAULT_IMAGE_OPTIONS)
        plugin.pickMedia(
            mediaSelectionOptions,
            GENERAL_OPTIONS_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate)
            .chooseMediaFromGallery(
                mediaSelectionOptions,
                GENERAL_OPTIONS_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
                mockResult
            )
        mockResult.assertNoResults()
    }

    @Test
    fun pickImages_whenSourceIsCamera_invokesTakeImageWithCamera() {
        plugin.pickImages(
            SOURCE_CAMERA_REAR,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate).takeImageWithCamera(DEFAULT_IMAGE_OPTIONS, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun pickImages_whenSourceIsCamera_invokesTakeImageWithCamera_RearCamera() {
        plugin.pickImages(
            SOURCE_CAMERA_REAR,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate).setCameraDevice(ImagePickerDelegate.CameraDevice.REAR)
    }

    @Test
    fun pickImages_whenSourceIsCamera_invokesTakeImageWithCamera_FrontCamera() {
        plugin.pickImages(
            SOURCE_CAMERA_FRONT,
            DEFAULT_IMAGE_OPTIONS,
            GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate).setCameraDevice(ImagePickerDelegate.CameraDevice.FRONT)
    }

    @Test
    fun pickVideos_whenSourceIsCamera_invokesTakeImageWithCamera_RearCamera() {
        plugin.pickVideos(
            SOURCE_CAMERA_REAR,
            DEFAULT_VIDEO_OPTIONS,
            GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate).setCameraDevice(ImagePickerDelegate.CameraDevice.REAR)
    }

    @Test
    fun pickVideos_whenSourceIsCamera_invokesTakeImageWithCamera_FrontCamera() {
        plugin.pickVideos(
            SOURCE_CAMERA_FRONT,
            DEFAULT_VIDEO_OPTIONS,
            GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate).setCameraDevice(ImagePickerDelegate.CameraDevice.FRONT)
    }

    @Test
    fun pickVideos_invokesChooseMultiVideoFromGallery() {
        plugin.pickVideos(
            SOURCE_GALLERY,
            DEFAULT_VIDEO_OPTIONS,
            GENERAL_OPTIONS_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate)
            .chooseMultiVideoFromGallery(DEFAULT_VIDEO_OPTIONS, false, Int.MAX_VALUE, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun pickVideos_usingPhotoPicker_invokesChooseMultiVideoFromGallery() {
        plugin.pickVideos(
            SOURCE_GALLERY,
            DEFAULT_VIDEO_OPTIONS,
            GENERAL_OPTIONS_ALLOW_MULTIPLE_USE_PHOTO_PICKER,
            mockResult
        )
        verify(mockImagePickerDelegate)
            .chooseMultiVideoFromGallery(DEFAULT_VIDEO_OPTIONS, true, Int.MAX_VALUE, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun pickVideos_withLimit5_invokesChooseMultiVideoFromGallery() {
        plugin.pickVideos(
            SOURCE_GALLERY,
            DEFAULT_VIDEO_OPTIONS,
            GENERAL_OPTIONS_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER_WITH_LIMIT,
            mockResult
        )
        verify(mockImagePickerDelegate).chooseMultiVideoFromGallery(DEFAULT_VIDEO_OPTIONS, false, 5, mockResult)
        mockResult.assertNoResults()
    }

    @Test
    fun onConstructor_whenContextTypeIsActivity_shouldNotCrash() {
        ImagePickerPlugin(mockImagePickerDelegate, mockActivity)
        assertTrue(
            "No exception thrown when ImagePickerPlugin() ran with context instanceof Activity",
            true
        )
    }

    @Test
    fun onDetachedFromActivity_shouldReleaseActivityState() {
        val mockBinaryMessenger = mock(BinaryMessenger::class.java)
        `when`(mockPluginBinding.binaryMessenger).thenReturn(mockBinaryMessenger)

        val mockLifecycleReference = mock(HiddenLifecycleReference::class.java)
        `when`(mockActivityBinding.lifecycle).thenReturn(mockLifecycleReference)

        val mockLifecycle = mock(Lifecycle::class.java)
        `when`(mockLifecycleReference.lifecycle).thenReturn(mockLifecycle)

        plugin.onAttachedToEngine(mockPluginBinding)
        plugin.onAttachedToActivity(mockActivityBinding)
        assertNotNull(plugin.activityState)

        plugin.onDetachedFromActivity()
        assertNull(plugin.activityState)
    }

    companion object {
        private val DEFAULT_IMAGE_OPTIONS = ImageSelectionOptions(quality = 100)
        private val DEFAULT_VIDEO_OPTIONS = VideoSelectionOptions()
        private val GENERAL_OPTIONS_ALLOW_MULTIPLE_USE_PHOTO_PICKER =
            GeneralOptions(allowMultiple = true, usePhotoPicker = true)
        private val GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_USE_PHOTO_PICKER =
            GeneralOptions(allowMultiple = false, usePhotoPicker = true)
        private val GENERAL_OPTIONS_DONT_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER =
            GeneralOptions(allowMultiple = false, usePhotoPicker = false)
        private val GENERAL_OPTIONS_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER =
            GeneralOptions(allowMultiple = true, usePhotoPicker = false)
        private val GENERAL_OPTIONS_ALLOW_MULTIPLE_DONT_USE_PHOTO_PICKER_WITH_LIMIT =
            GeneralOptions(allowMultiple = true, usePhotoPicker = false, limit = 5)
        private val GENERAL_OPTIONS_ALLOW_MULTIPLE_USE_PHOTO_PICKER_WITH_LIMIT =
            GeneralOptions(allowMultiple = true, usePhotoPicker = true, limit = 5)
        private val SOURCE_GALLERY = SourceSpecification(type = SourceType.GALLERY)
        private val SOURCE_CAMERA_FRONT =
            SourceSpecification(type = SourceType.CAMERA, camera = SourceCamera.FRONT)
        private val SOURCE_CAMERA_REAR =
            SourceSpecification(type = SourceType.CAMERA, camera = SourceCamera.REAR)
    }
}
