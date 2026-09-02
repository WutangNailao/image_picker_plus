// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ImagePickerDelegateTest {
    @Mock lateinit var mockActivity: Activity
    @Mock lateinit var mockImageResizer: ImageResizer
    @Mock lateinit var mockPermissionManager: ImagePickerDelegate.PermissionManager
    @Mock lateinit var mockFileUtils: FileUtils
    @Mock lateinit var mockIntent: Intent
    @Mock lateinit var cache: ImagePickerCache
    @Mock lateinit var mockExecutor: ExecutorService

    private lateinit var mockResult: TestCallback
    private lateinit var mockFileUriResolver: ImagePickerDelegate.FileUriResolver
    private lateinit var mockStaticFile: MockedStatic<File>
    private lateinit var mockCloseable: AutoCloseable
    private lateinit var externalDirectory: File
    private lateinit var defaultUri: Uri
    private lateinit var clipUri: Uri

    private class MockFileUriResolver : ImagePickerDelegate.FileUriResolver {
        override fun resolveFileProviderUriForFile(fileProviderName: String, imageFile: File): Uri =
            Uri.parse("content://test/${imageFile.name}")

        override fun getFullImagePath(imageUri: Uri, listener: ImagePickerDelegate.OnPathReadyListener) {
            listener.onPathReady("pathFromUri")
        }
    }

    @Before
    fun setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this)
        mockResult = TestCallback()

        mockStaticFile = Mockito.mockStatic(File::class.java)
        mockStaticFile
            .`when`<File> { File.createTempFile(anyString(), anyString(), any()) }
            .thenReturn(File("/tmpfile"))

        `when`(mockActivity.packageName).thenReturn("com.example.test")
        `when`(mockActivity.packageManager).thenReturn(mock(PackageManager::class.java))

        val temporaryFolder = TemporaryFolder()
        temporaryFolder.create()
        externalDirectory = temporaryFolder.newFolder("image_picker_cache")
        `when`(mockActivity.cacheDir).thenReturn(externalDirectory)

        defaultUri = Uri.parse("content://test/default")
        clipUri = Uri.parse("content://test/clip")
        `when`(mockFileUtils.getPathFromUri(mockActivity, defaultUri)).thenReturn("pathFromUri")
        `when`(mockFileUtils.getPathFromUri(mockActivity, clipUri)).thenReturn("pathFromUri")

        `when`(mockImageResizer.resizeImageIfNeeded("pathFromUri", null, null, 100))
            .thenReturn("originalPath")
        `when`(mockImageResizer.resizeImageIfNeeded("pathFromUri", null, null, IMAGE_QUALITY))
            .thenReturn("originalPath")
        `when`(mockImageResizer.resizeImageIfNeeded("pathFromUri", WIDTH, HEIGHT, 100))
            .thenReturn("scaledPath")
        `when`(mockImageResizer.resizeImageIfNeeded("pathFromUri", WIDTH, null, 100))
            .thenReturn("scaledPath")
        `when`(mockImageResizer.resizeImageIfNeeded("pathFromUri", null, HEIGHT, 100))
            .thenReturn("scaledPath")

        mockFileUriResolver = MockFileUriResolver()

        `when`(mockIntent.data).thenReturn(defaultUri)
    }

    @After
    fun tearDown() {
        mockStaticFile.close()
        mockCloseable.close()
    }

    @Test
    fun whenConstructed_setsCorrectFileProviderName() {
        val delegate = createDelegate()
        assertThat(delegate.fileProviderName, equalTo("com.example.test.flutter.image_provider"))
    }

    @Test
    fun chooseImageFromGallery_whenPendingResultExists_finishesWithAlreadyActiveError() {
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.chooseImageFromGallery(DEFAULT_IMAGE_OPTIONS, false, mockResult)

        verifyFinishedWithAlreadyActiveError()
    }

    @Test
    fun chooseMultiImageFromGallery_whenPendingResultExists_finishesWithAlreadyActiveError() {
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.chooseMultiImageFromGallery(DEFAULT_IMAGE_OPTIONS, false, Int.MAX_VALUE, mockResult)

        verifyFinishedWithAlreadyActiveError()
    }

    @Test
    fun chooseMediaFromGallery_whenPendingResultExists_finishesWithAlreadyActiveError() {
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)
        val generalOptions = GeneralOptions(allowMultiple = true, usePhotoPicker = true)

        delegate.chooseMediaFromGallery(DEFAULT_MEDIA_OPTIONS, generalOptions, mockResult)

        verifyFinishedWithAlreadyActiveError()
    }

    @Test
    @Config(sdk = [30])
    fun chooseImageFromGallery_launchesChooseFromGalleryIntent() {
        val delegate = createDelegate()
        delegate.chooseImageFromGallery(DEFAULT_IMAGE_OPTIONS, false, mockResult)

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY))
    }

    @Test
    @Config(minSdk = 33)
    fun chooseImageFromGallery_withPhotoPicker_launchesChooseFromGalleryIntent() {
        val delegate = createDelegate()
        delegate.chooseImageFromGallery(DEFAULT_IMAGE_OPTIONS, true, mockResult)

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY))
    }

    @Test
    @Config(sdk = [30])
    fun chooseMultiImageFromGallery_launchesChooseFromGalleryIntent() {
        val delegate = createDelegate()
        delegate.chooseMultiImageFromGallery(DEFAULT_IMAGE_OPTIONS, true, Int.MAX_VALUE, mockResult)

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_CHOOSE_MULTI_IMAGE_FROM_GALLERY))
    }

    @Test
    @Config(minSdk = 33)
    fun chooseMultiImageFromGallery_withPhotoPicker_launchesChooseFromGalleryIntent() {
        val delegate = createDelegate()
        delegate.chooseMultiImageFromGallery(DEFAULT_IMAGE_OPTIONS, false, Int.MAX_VALUE, mockResult)

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_CHOOSE_MULTI_IMAGE_FROM_GALLERY))
    }

    @Test
    @Config(sdk = [30])
    fun chooseVideoFromGallery_launchesChooseFromGalleryIntent() {
        val delegate = createDelegate()
        delegate.chooseVideoFromGallery(DEFAULT_VIDEO_OPTIONS, true, mockResult)

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY))
    }

    @Test
    @Config(minSdk = 33)
    fun chooseVideoFromGallery_withPhotoPicker_launchesChooseFromGalleryIntent() {
        val delegate = createDelegate()
        delegate.chooseVideoFromGallery(DEFAULT_VIDEO_OPTIONS, true, mockResult)

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY))
    }

    @Test
    fun takeImageWithCamera_whenPendingResultExists_finishesWithAlreadyActiveError() {
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.takeImageWithCamera(DEFAULT_IMAGE_OPTIONS, mockResult)

        verifyFinishedWithAlreadyActiveError()
    }

    @Test
    fun takeImageWithCamera_whenHasNoCameraPermission_requestsForPermission() {
        `when`(mockPermissionManager.isPermissionGranted(Manifest.permission.CAMERA)).thenReturn(false)
        `when`(mockPermissionManager.needRequestCameraPermission()).thenReturn(true)

        val delegate = createDelegate()
        delegate.takeImageWithCamera(DEFAULT_IMAGE_OPTIONS, mockResult)

        verify(mockPermissionManager)
            .askForPermission(Manifest.permission.CAMERA, ImagePickerDelegate.REQUEST_CAMERA_IMAGE_PERMISSION)
    }

    @Test
    fun takeImageWithCamera_whenCameraPermissionNotPresent_requestsForPermission() {
        `when`(mockPermissionManager.needRequestCameraPermission()).thenReturn(false)

        val delegate = createDelegate()
        delegate.takeImageWithCamera(DEFAULT_IMAGE_OPTIONS, mockResult)

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA))
    }

    @Test
    fun takeImageWithCamera_whenHasCameraPermission_andAnActivityCanHandleCameraIntent_launchesTakeWithCameraIntent() {
        `when`(mockPermissionManager.isPermissionGranted(Manifest.permission.CAMERA)).thenReturn(true)

        val delegate = createDelegate()
        delegate.takeImageWithCamera(DEFAULT_IMAGE_OPTIONS, mockResult)

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA))
    }

    @Test
    fun takeImageWithCamera_whenHasCameraPermission_andNoActivityToHandleCameraIntent_finishesWithNoCamerasAvailableError() {
        `when`(mockPermissionManager.isPermissionGranted(Manifest.permission.CAMERA)).thenReturn(true)
        doThrow(ActivityNotFoundException::class.java)
            .`when`(mockActivity)
            .startActivityForResult(any(Intent::class.java), anyInt())
        val delegate = createDelegate()

        delegate.takeImageWithCamera(DEFAULT_IMAGE_OPTIONS, mockResult)

        assertFlutterError("no_available_camera", "No cameras available for taking pictures.")
    }

    @Test
    fun takeImageWithCamera_writesImageToCacheDirectory() {
        `when`(mockPermissionManager.isPermissionGranted(Manifest.permission.CAMERA)).thenReturn(true)

        val delegate = createDelegate()
        delegate.takeImageWithCamera(DEFAULT_IMAGE_OPTIONS, mockResult)

        mockStaticFile.verify(
            MockedStatic.Verification { File.createTempFile(any(), eq(".jpg"), eq(externalDirectory)) },
            times(1)
        )
    }

    @Test
    fun onRequestPermissionsResult_whenCameraPermissionDenied_finishesWithError() {
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.onRequestPermissionsResult(
            ImagePickerDelegate.REQUEST_CAMERA_IMAGE_PERMISSION,
            arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_DENIED)
        )

        assertFlutterError("camera_access_denied", "The user did not allow camera access.")
    }

    @Test
    fun onRequestTakeVideoPermissionsResult_whenCameraPermissionGranted_launchesTakeVideoWithCameraIntent() {
        val delegate = createDelegateWithPendingResultAndOptions(null, DEFAULT_VIDEO_OPTIONS)
        delegate.onRequestPermissionsResult(
            ImagePickerDelegate.REQUEST_CAMERA_VIDEO_PERMISSION,
            arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_GRANTED)
        )

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA))
    }

    @Test
    fun onRequestTakeImagePermissionsResult_whenCameraPermissionGranted_launchesTakeWithCameraIntent() {
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)
        delegate.onRequestPermissionsResult(
            ImagePickerDelegate.REQUEST_CAMERA_IMAGE_PERMISSION,
            arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_GRANTED)
        )

        verify(mockActivity)
            .startActivityForResult(any(Intent::class.java), eq(ImagePickerDelegate.REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA))
    }

    @Test
    fun onActivityResult_whenPickFromGalleryCanceled_finishesWithEmptyList() {
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY, Activity.RESULT_CANCELED, null)

        assertEquals(0, successPaths().size)
    }

    @Test
    fun onActivityResult_whenPickFromGalleryCanceled_storesNothingInCache() {
        executeAsyncInline()
        val delegate = createDelegate()

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY, Activity.RESULT_CANCELED, null)

        verify(cache, never()).saveResult(any(), any(), any())
    }

    @Test
    fun onActivityResult_whenImagePickedFromGallery_andNoResizeNeeded_finishesWithImagePath() {
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertEquals("originalPath", successPaths()[0])
    }

    @Test
    fun onActivityResult_whenImagePickedFromGallery_nullUriFromGetData_andNoResizeNeeded_finishesWithImagePath() {
        setupMockClipData()
        `when`(mockIntent.data).thenReturn(null)
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertEquals("originalPath", successPaths()[0])
    }

    @Test
    fun onActivityResult_whenVideoPickedFromGallery_nullUriFromGetData_finishesWithVideoPath() {
        setupMockClipData()
        `when`(mockIntent.data).thenReturn(null)
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(null, DEFAULT_VIDEO_OPTIONS)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertEquals("pathFromUri", successPaths()[0])
    }

    @Test
    fun onActivityResult_whenImagePickedFromGallery_nullUri_andNoResizeNeeded_finishesWithNoValidUriError() {
        setupMockClipDataNullUri()
        `when`(mockIntent.data).thenReturn(null)
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertFlutterError("no_valid_image_uri", "Cannot find the selected image.")
    }

    @Test
    fun onActivityResult_whenVideoPickedFromGallery_nullUri_finishesWithNoValidUriError() {
        setupMockClipDataNullUri()
        `when`(mockIntent.data).thenReturn(null)
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(null, DEFAULT_VIDEO_OPTIONS)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertFlutterError("no_valid_video_uri", "Cannot find the selected video.")
    }

    @Test
    fun onActivityResult_whenImagePickedFromGallery_andNoResizeNeeded_storesImageInCache() {
        executeAsyncInline()
        val delegate = createDelegate()

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        @Suppress("UNCHECKED_CAST")
        val pathListCapture = ArgumentCaptor.forClass(ArrayList::class.java) as ArgumentCaptor<ArrayList<String>>
        verify(cache, times(1)).saveResult(pathListCapture.capture(), any(), any())
        assertEquals("pathFromUri", pathListCapture.value[0])
    }

    @Test
    fun onActivityResult_whenImagePickedFromGallery_andResizeNeeded_finishesWithScaledImagePath() {
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(RESIZE_TRIGGERING_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertEquals("scaledPath", successPaths()[0])
    }

    @Test
    fun onActivityResult_whenVideoPickedFromGallery_andResizeParametersSupplied_finishesWithFilePath() {
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(RESIZE_TRIGGERING_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertEquals("pathFromUri", successPaths()[0])
    }

    @Test
    fun onActivityResult_whenTakeImageWithCameraCanceled_finishesWithEmptyList() {
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA, Activity.RESULT_CANCELED, null)

        assertEquals(0, successPaths().size)
    }

    @Test
    fun onActivityResult_whenImageTakenWithCamera_andNoResizeNeeded_finishesWithImagePath() {
        `when`(cache.retrievePendingCameraMediaUriPath()).thenReturn("testString")
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA, Activity.RESULT_OK, mockIntent)

        assertEquals("originalPath", successPaths()[0])
    }

    @Test
    fun onActivityResult_whenImageTakenWithCamera_andResizeNeeded_finishesWithScaledImagePath() {
        `when`(cache.retrievePendingCameraMediaUriPath()).thenReturn("testString")
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(RESIZE_TRIGGERING_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA, Activity.RESULT_OK, mockIntent)

        assertEquals("scaledPath", successPaths()[0])
    }

    @Test
    fun onActivityResult_whenVideoTakenWithCamera_andResizeParametersSupplied_finishesWithFilePath() {
        `when`(cache.retrievePendingCameraMediaUriPath()).thenReturn("testString")
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(RESIZE_TRIGGERING_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA, Activity.RESULT_OK, mockIntent)

        assertEquals("pathFromUri", successPaths()[0])
    }

    @Test
    fun onActivityResult_whenVideoTakenWithCamera_andMaxDurationParametersSupplied_finishesWithFilePath() {
        `when`(cache.retrievePendingCameraMediaUriPath()).thenReturn("testString")
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(null, VideoSelectionOptions(MAX_DURATION))

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA, Activity.RESULT_OK, mockIntent)

        assertEquals("pathFromUri", successPaths()[0])
    }

    @Test
    fun onActivityResult_whenImagePickedFromGallery_returnsTrue() {
        val delegate = createDelegate()

        val isHandled = delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertTrue(isHandled)
    }

    @Test
    fun onActivityResult_whenMultipleImagesPickedFromGallery_returnsTrue() {
        val delegate = createDelegate()

        val isHandled = delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_MULTI_IMAGE_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertTrue(isHandled)
    }

    @Test
    fun onActivityResult_whenMediaPickedFromGallery_returnsTrue() {
        val delegate = createDelegate()

        val isHandled = delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_MEDIA_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertTrue(isHandled)
    }

    @Test
    fun onActivityResult_whenVideoPickerFromGallery_returnsTrue() {
        val delegate = createDelegate()

        val isHandled = delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertTrue(isHandled)
    }

    @Test
    fun onActivityResult_whenImageTakenWithCamera_returnsTrue() {
        val delegate = createDelegate()

        val isHandled = delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA, Activity.RESULT_OK, mockIntent)

        assertTrue(isHandled)
    }

    @Test
    fun onActivityResult_whenVideoTakenWithCamera_returnsTrue() {
        val delegate = createDelegate()

        val isHandled = delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA, Activity.RESULT_OK, mockIntent)

        assertTrue(isHandled)
    }

    @Test
    fun onActivityResult_withUnknownRequest_returnsFalse() {
        val delegate = createDelegate()

        val isHandled = delegate.onActivityResult(314, Activity.RESULT_OK, mockIntent)

        assertFalse(isHandled)
    }

    @Test
    fun onActivityResult_whenImagePickedFromGallery_finishesWithErrorIfClipDataIsNull() {
        `when`(mockIntent.data).thenReturn(null)
        `when`(mockIntent.clipData).thenReturn(null)
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_MEDIA_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertFlutterError("no_valid_media_uri", "Cannot find the selected media.")
    }

    @Test
    fun onActivityResult_whenImagePickedFromGallery_finishesWithErrorIfClipDataUriIsNull() {
        setupMockClipDataNullUri()
        `when`(mockIntent.data).thenReturn(null)
        `when`(mockIntent.clipData).thenReturn(null)
        executeAsyncInline()
        val delegate = createDelegateWithPendingResultAndOptions(DEFAULT_IMAGE_OPTIONS, null)

        delegate.onActivityResult(ImagePickerDelegate.REQUEST_CODE_CHOOSE_MEDIA_FROM_GALLERY, Activity.RESULT_OK, mockIntent)

        assertFlutterError("no_valid_media_uri", "Cannot find the selected media.")
    }

    private fun createDelegate(): ImagePickerDelegate =
        ImagePickerDelegate(
            mockActivity,
            mockImageResizer,
            null,
            null,
            null,
            cache,
            mockPermissionManager,
            mockFileUriResolver,
            mockFileUtils,
            mockExecutor
        )

    private fun createDelegateWithPendingResultAndOptions(
        imageOptions: ImageSelectionOptions?,
        videoOptions: VideoSelectionOptions?
    ): ImagePickerDelegate =
        ImagePickerDelegate(
            mockActivity,
            mockImageResizer,
            imageOptions,
            videoOptions,
            mockResult,
            cache,
            mockPermissionManager,
            mockFileUriResolver,
            mockFileUtils,
            mockExecutor
        )

    private fun verifyFinishedWithAlreadyActiveError() {
        assertFlutterError("already_active", "Image picker is already active")
    }

    private fun assertFlutterError(code: String, message: String) {
        val error = mockResult.singleResult().exceptionOrNull() as FlutterError
        assertEquals(code, error.code)
        assertEquals(message, error.message)
    }

    private fun successPaths(): List<String> =
        mockResult.singleResult().getOrThrow().map { it.path }

    private fun executeAsyncInline() {
        Mockito.doAnswer {
            (it.getArgument(0) as Runnable).run()
            null
        }.`when`(mockExecutor).execute(any(Runnable::class.java))
    }

    private fun setupMockClipData() {
        val mockClipData = mock(ClipData::class.java)
        val mockItem = mock(ClipData.Item::class.java)
        `when`(mockItem.uri).thenReturn(clipUri)
        `when`(mockClipData.itemCount).thenReturn(1)
        `when`(mockClipData.getItemAt(0)).thenReturn(mockItem)
        `when`(mockIntent.clipData).thenReturn(mockClipData)
    }

    private fun setupMockClipDataNullUri() {
        val mockClipData = mock(ClipData::class.java)
        val mockItem = mock(ClipData.Item::class.java)
        `when`(mockItem.uri).thenReturn(null)
        `when`(mockClipData.itemCount).thenReturn(1)
        `when`(mockClipData.getItemAt(0)).thenReturn(mockItem)
        `when`(mockIntent.clipData).thenReturn(mockClipData)
    }

    companion object {
        private const val WIDTH = 10.0
        private const val HEIGHT = 10.0
        private const val MAX_DURATION = 10L
        private const val IMAGE_QUALITY = 90
        private val DEFAULT_IMAGE_OPTIONS = ImageSelectionOptions(quality = 100)
        private val RESIZE_TRIGGERING_IMAGE_OPTIONS =
            ImageSelectionOptions(maxWidth = WIDTH, quality = 100)
        private val DEFAULT_VIDEO_OPTIONS = VideoSelectionOptions()
        private val DEFAULT_MEDIA_OPTIONS = MediaSelectionOptions(DEFAULT_IMAGE_OPTIONS)
    }
}
