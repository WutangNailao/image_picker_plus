// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import io.flutter.plugin.common.PluginRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A delegate class doing the heavy lifting for the plugin.
 *
 * When invoked, both the [chooseImageFromGallery] and [takeImageWithCamera]
 * methods go through the same steps:
 *
 * 1. Check for an existing [pendingCallState]. If a previous pendingCallState exists,
 * this means that the chooseImageFromGallery() or takeImageWithCamera() method was called at least
 * twice. In this case, stop executing and finish with an error.
 *
 * 2. Check that a required runtime permission has been granted. The takeImageWithCamera() method
 * checks that [Manifest.permission.CAMERA] has been granted.
 *
 * The permission check can end up in two different outcomes:
 *
 * A) If the permission has already been granted, continue with picking the image from gallery or
 * camera.
 *
 * B) If the permission hasn't already been granted, ask for the permission from the user. If the
 * user grants the permission, proceed with step #3. If the user denies the permission, stop doing
 * anything else and finish with a null result.
 *
 * 3. Launch the gallery or camera for picking the image, depending on whether
 * chooseImageFromGallery() or takeImageWithCamera() was called.
 *
 * This can end up in three different outcomes:
 *
 * A) User picks an image. No maxWidth or maxHeight was specified when calling `pickImage()` method in the Dart side of this plugin. Finish with full path for the picked image
 * as the result.
 *
 * B) User picks an image. A maxWidth and/or maxHeight was provided when calling `pickImage()` method in the Dart side of this plugin. A scaled copy of the image is created.
 * Finish with full path for the scaled image as the result.
 *
 * C) User cancels picking an image. Finish with null result.
 */
class ImagePickerDelegate : PluginRegistry.ActivityResultListener,
    PluginRegistry.RequestPermissionsResultListener {

    enum class CameraDevice {
        REAR,
        FRONT
    }

    /** Holds call state during intent handling. */
    private data class PendingCallState(
        val imageOptions: ImageSelectionOptions?,
        val videoOptions: VideoSelectionOptions?,
        val result: (Result<List<PickedMedia>>) -> Unit
    )

    data class MediaPath(
        val path: String,
        val uri: String?,
        val mimeType: String?
    )

    interface PermissionManager {
        fun isPermissionGranted(permissionName: String): Boolean
        fun askForPermission(permissionName: String, requestCode: Int)
        fun needRequestCameraPermission(): Boolean
    }

    interface FileUriResolver {
        fun resolveFileProviderUriForFile(fileProviderName: String, imageFile: File): Uri
        fun getFullImagePath(imageUri: Uri, listener: OnPathReadyListener)
    }

    fun interface OnPathReadyListener {
        fun onPathReady(path: String)
    }

    @VisibleForTesting
    val fileProviderName: String

    private val activity: Activity
    private val imageResizer: ImageResizer
    private val cache: ImagePickerCache
    private val permissionManager: PermissionManager
    private val fileUriResolver: FileUriResolver
    private val fileUtils: FileUtils
    private val executor: ExecutorService
    private var cameraDevice: CameraDevice? = null

    private var pendingCameraMediaUri: Uri? = null
    private var pendingCallState: PendingCallState? = null
    private val pendingCallStateLock = Any()

    constructor(
        activity: Activity,
        imageResizer: ImageResizer,
        cache: ImagePickerCache
    ) : this(
        activity,
        imageResizer,
        null,
        null,
        null,
        cache,
        object : PermissionManager {
            override fun isPermissionGranted(permissionName: String): Boolean {
                return ActivityCompat.checkSelfPermission(activity, permissionName) ==
                        PackageManager.PERMISSION_GRANTED
            }

            override fun askForPermission(permissionName: String, requestCode: Int) {
                ActivityCompat.requestPermissions(activity, arrayOf(permissionName), requestCode)
            }

            override fun needRequestCameraPermission(): Boolean {
                return ImagePickerUtils.needRequestCameraPermission(activity)
            }
        },
        object : FileUriResolver {
            override fun resolveFileProviderUriForFile(fileProviderName: String, file: File): Uri {
                return FileProvider.getUriForFile(activity, fileProviderName, file)
            }

            override fun getFullImagePath(imageUri: Uri, listener: OnPathReadyListener) {
                MediaScannerConnection.scanFile(
                    activity,
                    arrayOf(imageUri.path ?: ""),
                    null
                ) { path, _ -> listener.onPathReady(path) }
            }
        },
        FileUtils(),
        Executors.newSingleThreadExecutor()
    )

    /**
     * This constructor is used exclusively for testing; it can be used to provide mocks to final
     * fields of this class. Otherwise those fields would have to be mutable and visible.
     */
    @VisibleForTesting
    constructor(
        activity: Activity,
        imageResizer: ImageResizer,
        pendingImageOptions: ImageSelectionOptions?,
        pendingVideoOptions: VideoSelectionOptions?,
        result: ((Result<List<PickedMedia>>) -> Unit)?,
        cache: ImagePickerCache,
        permissionManager: PermissionManager,
        fileUriResolver: FileUriResolver,
        fileUtils: FileUtils,
        executor: ExecutorService
    ) {
        this.activity = activity
        this.imageResizer = imageResizer
        this.fileProviderName = "${activity.packageName}.flutter.image_provider"
        if (result != null) {
            this.pendingCallState = PendingCallState(pendingImageOptions, pendingVideoOptions, result)
        }
        this.permissionManager = permissionManager
        this.fileUriResolver = fileUriResolver
        this.fileUtils = fileUtils
        this.cache = cache
        this.executor = executor
    }

    fun setCameraDevice(device: CameraDevice) {
        cameraDevice = device
    }

    // Save the state of the image picker so it can be retrieved with `retrieveLostImage`.
    fun saveStateBeforeResult() {
        val localImageOptions: ImageSelectionOptions? = synchronized(pendingCallStateLock) {
            pendingCallState?.imageOptions
        }

        cache.saveType(
            if (localImageOptions != null)
                ImagePickerCache.CacheType.IMAGE
            else
                ImagePickerCache.CacheType.VIDEO
        )
        if (localImageOptions != null) {
            cache.saveDimensionWithOutputOptions(localImageOptions)
        }

        val localPendingCameraMediaUri = pendingCameraMediaUri
        if (localPendingCameraMediaUri != null) {
            cache.savePendingCameraMediaUriPath(localPendingCameraMediaUri)
        }
    }

    fun retrieveLostImage(): CacheRetrievalResult? {
        val cacheMap = cache.getCacheMap()
        if (cacheMap.isEmpty()) {
            return null
        }

        val type = cacheMap[ImagePickerCache.MAP_KEY_TYPE] as? CacheRetrievalType
            ?: CacheRetrievalType.IMAGE
        val error = cacheMap[ImagePickerCache.MAP_KEY_ERROR] as? CacheRetrievalError

        @Suppress("UNCHECKED_CAST")
        val pathList = cacheMap[ImagePickerCache.MAP_KEY_PATH_LIST] as? ArrayList<String>
        val newPathList = ArrayList<String>()
        if (pathList != null) {
            for (path in pathList) {
                val maxWidth = cacheMap[ImagePickerCache.MAP_KEY_MAX_WIDTH] as? Double
                val maxHeight = cacheMap[ImagePickerCache.MAP_KEY_MAX_HEIGHT] as? Double
                val boxedImageQuality = cacheMap[ImagePickerCache.MAP_KEY_IMAGE_QUALITY] as? Int
                val imageQuality = boxedImageQuality ?: 100

                newPathList.add(imageResizer.resizeImageIfNeeded(path, maxWidth, maxHeight, imageQuality))
            }
        }

        cache.clear()

        return CacheRetrievalResult(type, error, newPathList)
    }

    fun chooseMediaFromGallery(
        options: MediaSelectionOptions,
        generalOptions: GeneralOptions,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        if (!setPendingOptionsAndResult(options.imageSelectionOptions, null, callback)) {
            finishWithAlreadyActiveError(callback)
            return
        }

        launchPickMediaFromGalleryIntent(generalOptions)
    }

    private fun launchPickMediaFromGalleryIntent(generalOptions: GeneralOptions) {
        val pickMediaIntent: Intent = if (generalOptions.usePhotoPicker) {
            if (generalOptions.allowMultiple) {
                val limit = ImagePickerUtils.getLimitFromOption(generalOptions)

                ActivityResultContracts.PickMultipleVisualMedia(limit)
                    .createIntent(
                        activity,
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            .build()
                    )
            } else {
                ActivityResultContracts.PickVisualMedia()
                    .createIntent(
                        activity,
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            .build()
                    )
            }
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                val mimeTypes = arrayOf("video/*", "image/*")
                putExtra("CONTENT_TYPE", mimeTypes)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, generalOptions.allowMultiple)
            }
        }
        activity.startActivityForResult(pickMediaIntent, REQUEST_CODE_CHOOSE_MEDIA_FROM_GALLERY)
    }

    fun chooseVideoFromGallery(
        options: VideoSelectionOptions,
        usePhotoPicker: Boolean,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        if (!setPendingOptionsAndResult(null, options, callback)) {
            finishWithAlreadyActiveError(callback)
            return
        }

        launchPickVideoFromGalleryIntent(usePhotoPicker)
    }

    private fun launchPickVideoFromGalleryIntent(usePhotoPicker: Boolean) {
        val pickVideoIntent: Intent = if (usePhotoPicker) {
            ActivityResultContracts.PickVisualMedia()
                .createIntent(
                    activity,
                    PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        .build()
                )
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
            }
        }

        activity.startActivityForResult(pickVideoIntent, REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY)
    }

    fun takeVideoWithCamera(
        options: VideoSelectionOptions,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        if (!setPendingOptionsAndResult(null, options, callback)) {
            finishWithAlreadyActiveError(callback)
            return
        }

        if (needRequestCameraPermission() &&
            !permissionManager.isPermissionGranted(Manifest.permission.CAMERA)
        ) {
            permissionManager.askForPermission(
                Manifest.permission.CAMERA,
                REQUEST_CAMERA_VIDEO_PERMISSION
            )
            return
        }

        launchTakeVideoWithCameraIntent()
    }

    private fun launchTakeVideoWithCameraIntent() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)

        val localVideoOptions: VideoSelectionOptions? = synchronized(pendingCallStateLock) {
            pendingCallState?.videoOptions
        }

        if (localVideoOptions?.maxDurationSeconds != null) {
            val maxSeconds = localVideoOptions.maxDurationSeconds!!.toInt()
            intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, maxSeconds)
        }
        if (cameraDevice == CameraDevice.FRONT) {
            useFrontCamera(intent)
        }

        val videoFile = createTemporaryWritableVideoFile()
        pendingCameraMediaUri = Uri.parse("file:${videoFile.absolutePath}")

        val videoUri = fileUriResolver.resolveFileProviderUriForFile(fileProviderName, videoFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
        grantUriPermissions(intent, videoUri)

        try {
            activity.startActivityForResult(intent, REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA)
        } catch (e: ActivityNotFoundException) {
            try {
                // If we can't delete the file again here, there's not really anything we can do about it.
                videoFile.delete()
            } catch (exception: SecurityException) {
                exception.printStackTrace()
            }
            finishWithError("no_available_camera", "No cameras available for taking pictures.")
        }
    }

    fun chooseImageFromGallery(
        options: ImageSelectionOptions,
        usePhotoPicker: Boolean,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        if (!setPendingOptionsAndResult(options, null, callback)) {
            finishWithAlreadyActiveError(callback)
            return
        }

        launchPickImageFromGalleryIntent(usePhotoPicker)
    }

    fun chooseMultiImageFromGallery(
        options: ImageSelectionOptions,
        usePhotoPicker: Boolean,
        limit: Int,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        if (!setPendingOptionsAndResult(options, null, callback)) {
            finishWithAlreadyActiveError(callback)
            return
        }

        launchMultiPickImageFromGalleryIntent(usePhotoPicker, limit)
    }

    private fun launchPickImageFromGalleryIntent(usePhotoPicker: Boolean) {
        val pickImageIntent: Intent = if (usePhotoPicker) {
            ActivityResultContracts.PickVisualMedia()
                .createIntent(
                    activity,
                    PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .build()
                )
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
        }
        activity.startActivityForResult(pickImageIntent, REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY)
    }

    private fun launchMultiPickImageFromGalleryIntent(usePhotoPicker: Boolean, limit: Int) {
        val pickMultiImageIntent: Intent = if (usePhotoPicker) {
            ActivityResultContracts.PickMultipleVisualMedia(limit)
                .createIntent(
                    activity,
                    PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .build()
                )
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
        activity.startActivityForResult(
            pickMultiImageIntent,
            REQUEST_CODE_CHOOSE_MULTI_IMAGE_FROM_GALLERY
        )
    }

    fun chooseMultiVideoFromGallery(
        options: VideoSelectionOptions,
        usePhotoPicker: Boolean,
        limit: Int,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        if (!setPendingOptionsAndResult(null, options, callback)) {
            finishWithAlreadyActiveError(callback)
            return
        }

        launchMultiPickVideoFromGalleryIntent(usePhotoPicker, limit)
    }

    private fun launchMultiPickVideoFromGalleryIntent(usePhotoPicker: Boolean, limit: Int) {
        val pickMultiVideoIntent: Intent = if (usePhotoPicker) {
            ActivityResultContracts.PickMultipleVisualMedia(limit)
                .createIntent(
                    activity,
                    PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        .build()
                )
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
        activity.startActivityForResult(
            pickMultiVideoIntent,
            REQUEST_CODE_CHOOSE_MULTI_VIDEO_FROM_GALLERY
        )
    }

    fun takeImageWithCamera(
        options: ImageSelectionOptions,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        if (!setPendingOptionsAndResult(options, null, callback)) {
            finishWithAlreadyActiveError(callback)
            return
        }

        if (needRequestCameraPermission() &&
            !permissionManager.isPermissionGranted(Manifest.permission.CAMERA)
        ) {
            permissionManager.askForPermission(
                Manifest.permission.CAMERA,
                REQUEST_CAMERA_IMAGE_PERMISSION
            )
            return
        }
        launchTakeImageWithCameraIntent()
    }

    private fun needRequestCameraPermission(): Boolean {
        return permissionManager.needRequestCameraPermission()
    }

    private fun launchTakeImageWithCameraIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraDevice == CameraDevice.FRONT) {
            useFrontCamera(intent)
        }

        val imageFile = createTemporaryWritableImageFile()
        pendingCameraMediaUri = Uri.parse("file:${imageFile.absolutePath}")

        val imageUri = fileUriResolver.resolveFileProviderUriForFile(fileProviderName, imageFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        grantUriPermissions(intent, imageUri)

        try {
            activity.startActivityForResult(intent, REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA)
        } catch (e: ActivityNotFoundException) {
            try {
                // If we can't delete the file again here, there's not really anything we can do about it.
                imageFile.delete()
            } catch (exception: SecurityException) {
                exception.printStackTrace()
            }
            finishWithError("no_available_camera", "No cameras available for taking pictures.")
        }
    }

    private fun createTemporaryWritableImageFile(): File {
        return createTemporaryWritableFile(".jpg")
    }

    private fun createTemporaryWritableVideoFile(): File {
        return createTemporaryWritableFile(".mp4")
    }

    private fun createTemporaryWritableFile(suffix: String): File {
        val filename = UUID.randomUUID().toString()
        val externalFilesDirectory = activity.cacheDir

        return try {
            externalFilesDirectory.mkdirs()
            File.createTempFile(filename, suffix, externalFilesDirectory)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun grantUriPermissions(intent: Intent, imageUri: Uri) {
        val packageManager = activity.packageManager
        val compatibleActivities: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            queryIntentActivitiesPreApi33(packageManager, intent)
        }

        for (info in compatibleActivities) {
            activity.grantUriPermission(
                info.activityInfo.packageName,
                imageUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): Boolean {
        val permissionGranted =
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

        when (requestCode) {
            REQUEST_CAMERA_IMAGE_PERMISSION -> {
                if (permissionGranted) {
                    launchTakeImageWithCameraIntent()
                }
            }
            REQUEST_CAMERA_VIDEO_PERMISSION -> {
                if (permissionGranted) {
                    launchTakeVideoWithCameraIntent()
                }
            }
            else -> return false
        }

        if (!permissionGranted) {
            when (requestCode) {
                REQUEST_CAMERA_IMAGE_PERMISSION,
                REQUEST_CAMERA_VIDEO_PERMISSION -> {
                    finishWithError("camera_access_denied", "The user did not allow camera access.")
                }
            }
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val handlerRunnable: Runnable = when (requestCode) {
            REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY ->
                Runnable { handleChooseImageResult(resultCode, data) }
            REQUEST_CODE_CHOOSE_MULTI_IMAGE_FROM_GALLERY ->
                Runnable { handleChooseMultiImageResult(resultCode, data) }
            REQUEST_CODE_CHOOSE_MULTI_VIDEO_FROM_GALLERY ->
                Runnable { handleChooseMultiVideoResult(resultCode, data) }
            REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA ->
                Runnable { handleCaptureImageResult(resultCode) }
            REQUEST_CODE_CHOOSE_MEDIA_FROM_GALLERY ->
                Runnable { handleChooseMediaResult(resultCode, data) }
            REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY ->
                Runnable { handleChooseVideoResult(resultCode, data) }
            REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA ->
                Runnable { handleCaptureVideoResult(resultCode) }
            else -> return false
        }

        executor.execute(handlerRunnable)

        return true
    }

    private fun getPathsFromIntent(data: Intent, includeMimeType: Boolean): ArrayList<MediaPath>? {
        val paths = ArrayList<MediaPath>()

        var uri = data.data
        // On several pre-Android 13 devices using Android Photo Picker, the Uri from getData() could
        // be null.
        if (uri == null) {
            val clipData = data.clipData

            // If data.getData() and data.getClipData() are both null, we are in an error state. By
            // convention we return null from here, and then finish with an error from the corresponding
            // handler.
            if (clipData == null) {
                return null
            }

            for (i in 0 until clipData.itemCount) {
                uri = clipData.getItemAt(i).uri
                // Same error state as above.
                if (uri == null) {
                    return null
                }
                val path = fileUtils.getPathFromUri(activity, uri)
                // Again, same error state as above.
                if (path == null) {
                    return null
                }
                val mimeType = if (includeMimeType) activity.contentResolver.getType(uri) else null
                // Preserve the original content:// URI
                paths.add(MediaPath(path, uri.toString(), mimeType))
            }
        } else {
            val path = fileUtils.getPathFromUri(activity, uri)
            if (path == null) {
                return null
            }
            // Preserve the original content:// URI
            paths.add(MediaPath(path, uri.toString(), null))
        }
        return paths
    }

    private fun handleChooseImageResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val paths = getPathsFromIntent(data, false)
            // If there's no valid Uri, return an error
            if (paths == null) {
                finishWithError("no_valid_image_uri", "Cannot find the selected image.")
                return
            }

            handleMediaResult(paths)
            return
        }

        // User cancelled choosing a picture.
        finishWithSuccess(null)
    }

    private fun handleChooseMultiVideoResult(resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            val paths = getPathsFromIntent(intent, false)
            // If there's no valid Uri, return an error
            if (paths == null) {
                finishWithError(
                    "missing_valid_video_uri",
                    "Cannot find at least one of the selected videos."
                )
                return
            }

            handleMediaResult(paths)
            return
        }

        // User cancelled choosing a video.
        finishWithSuccess(null)
    }

    private fun handleChooseMediaResult(resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            val paths = getPathsFromIntent(intent, true)
            // If there's no valid Uri, return an error
            if (paths == null) {
                finishWithError("no_valid_media_uri", "Cannot find the selected media.")
                return
            }

            handleMediaResult(paths)
            return
        }

        // User cancelled choosing a picture.
        finishWithSuccess(null)
    }

    private fun handleChooseMultiImageResult(resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            val paths = getPathsFromIntent(intent, false)
            // If there's no valid Uri, return an error
            if (paths == null) {
                finishWithError(
                    "missing_valid_image_uri",
                    "Cannot find at least one of the selected images."
                )
                return
            }

            handleMediaResult(paths)
            return
        }

        // User cancelled choosing a picture.
        finishWithSuccess(null)
    }

    private fun handleChooseVideoResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val paths = getPathsFromIntent(data, false)
            // If there's no valid Uri, return an error
            if (paths == null || paths.size < 1) {
                finishWithError("no_valid_video_uri", "Cannot find the selected video.")
                return
            }

            val mediaPath = paths[0]
            finishWithSuccess(mediaPath.path, mediaPath.uri, mediaPath.mimeType)
            return
        }

        // User cancelled choosing a picture.
        finishWithSuccess(null)
    }

    private fun handleCaptureImageResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val localPendingCameraMediaUri = pendingCameraMediaUri

            fileUriResolver.getFullImagePath(
                localPendingCameraMediaUri
                    ?: Uri.parse(cache.retrievePendingCameraMediaUriPath())
            ) { path -> handleImageResult(path, true) }
            return
        }

        // User cancelled taking a picture.
        finishWithSuccess(null)
    }

    private fun handleCaptureVideoResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            val localPendingCameraMediaUrl = pendingCameraMediaUri
            fileUriResolver.getFullImagePath(
                localPendingCameraMediaUrl
                    ?: Uri.parse(cache.retrievePendingCameraMediaUriPath()),
                ::finishWithSuccess
            )
            return
        }

        // User cancelled taking a picture.
        finishWithSuccess(null)
    }

    fun handleImageResult(path: String, shouldDeleteOriginalIfScaled: Boolean) {
        val localImageOptions: ImageSelectionOptions? = synchronized(pendingCallStateLock) {
            pendingCallState?.imageOptions
        }

        if (localImageOptions != null) {
            val finalImagePath = getResizedImagePath(path, localImageOptions)
            // Delete original file if scaled.
            if (finalImagePath != null && finalImagePath != path && shouldDeleteOriginalIfScaled) {
                File(path).delete()
            }
            finishWithSuccess(finalImagePath)
        } else {
            finishWithSuccess(path)
        }
    }

    private fun getResizedImagePath(path: String, outputOptions: ImageSelectionOptions): String {
        return imageResizer.resizeImageIfNeeded(
            path,
            outputOptions.maxWidth,
            outputOptions.maxHeight,
            outputOptions.quality.toInt()
        )
    }

    private fun handleMediaResult(paths: ArrayList<MediaPath>) {
        val localImageOptions: ImageSelectionOptions? = synchronized(pendingCallStateLock) {
            pendingCallState?.imageOptions
        }

        val mediaList = ArrayList<PickedMedia>()
        if (localImageOptions != null) {
            for (mediaPath in paths) {
                var finalPath = mediaPath.path
                if (mediaPath.mimeType == null || !mediaPath.mimeType.startsWith("video/")) {
                    finalPath = getResizedImagePath(mediaPath.path, localImageOptions)
                }
                mediaList.add(
                    PickedMedia(
                        finalPath,
                        mediaPath.uri,
                        mediaPath.mimeType
                    )
                )
            }
            finishWithListSuccess(mediaList)
        } else {
            for (mediaPath in paths) {
                mediaList.add(
                    PickedMedia(
                        mediaPath.path,
                        mediaPath.uri,
                        mediaPath.mimeType
                    )
                )
            }
            finishWithListSuccess(mediaList)
        }
    }

    private fun setPendingOptionsAndResult(
        imageOptions: ImageSelectionOptions?,
        videoOptions: VideoSelectionOptions?,
        callback: (Result<List<PickedMedia>>) -> Unit
    ): Boolean {
        synchronized(pendingCallStateLock) {
            if (pendingCallState != null) {
                return false
            }
            pendingCallState = PendingCallState(imageOptions, videoOptions, callback)
        }

        // Clean up cache if a new image picker is launched.
        cache.clear()

        return true
    }

    // Handles completion of selection with a single result.
    //
    // A null imagePath indicates that the image picker was cancelled without
    // selection.
    private fun finishWithSuccess(imagePath: String?) {
        finishWithSuccess(imagePath, null, null)
    }

    // Handles completion of selection with a single result including content URI.
    private fun finishWithSuccess(imagePath: String?, contentUri: String?, mimeType: String?) {
        val mediaList = ArrayList<PickedMedia>()
        val pathList = ArrayList<String>()
        if (imagePath != null) {
            mediaList.add(PickedMedia(imagePath, contentUri, mimeType))
            pathList.add(imagePath)
        }

        val localResult: ((Result<List<PickedMedia>>) -> Unit)? = synchronized(pendingCallStateLock) {
            val callback = pendingCallState?.result
            pendingCallState = null
            callback
        }

        if (localResult == null) {
            // Only save data for later retrieval if something was actually selected.
            if (pathList.isNotEmpty()) {
                cache.saveResult(pathList, null, null)
            }
        } else {
            localResult(Result.success(mediaList))
        }
    }

    private fun finishWithListSuccess(mediaList: ArrayList<PickedMedia>) {
        val pathList = ArrayList<String>()
        for (media in mediaList) {
            pathList.add(media.path)
        }

        val localResult: ((Result<List<PickedMedia>>) -> Unit)? = synchronized(pendingCallStateLock) {
            val callback = pendingCallState?.result
            pendingCallState = null
            callback
        }

        if (localResult == null) {
            cache.saveResult(pathList, null, null)
        } else {
            localResult(Result.success(mediaList))
        }
    }

    private fun finishWithAlreadyActiveError(callback: (Result<List<PickedMedia>>) -> Unit) {
        callback(Result.failure(FlutterError("already_active", "Image picker is already active", null)))
    }

    private fun finishWithError(errorCode: String, errorMessage: String) {
        val localResult: ((Result<List<PickedMedia>>) -> Unit)? = synchronized(pendingCallStateLock) {
            val callback = pendingCallState?.result
            pendingCallState = null
            callback
        }

        if (localResult == null) {
            cache.saveResult(null, errorCode, errorMessage)
        } else {
            localResult(Result.failure(FlutterError(errorCode, errorMessage, null)))
        }
    }

    private fun useFrontCamera(intent: Intent) {
        intent.putExtra("android.intent.extras.CAMERA_FACING", CameraCharacteristics.LENS_FACING_FRONT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        }
    }

    companion object {
        @VisibleForTesting
        const val REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY = 2342

        @VisibleForTesting
        const val REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA = 2343

        @VisibleForTesting
        const val REQUEST_CAMERA_IMAGE_PERMISSION = 2345

        @VisibleForTesting
        const val REQUEST_CODE_CHOOSE_MULTI_IMAGE_FROM_GALLERY = 2346

        @VisibleForTesting
        const val REQUEST_CODE_CHOOSE_MEDIA_FROM_GALLERY = 2347

        @VisibleForTesting
        const val REQUEST_CODE_CHOOSE_MULTI_VIDEO_FROM_GALLERY = 2348

        @VisibleForTesting
        const val REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY = 2352

        @VisibleForTesting
        const val REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA = 2353

        @VisibleForTesting
        const val REQUEST_CAMERA_VIDEO_PERMISSION = 2355

        @Suppress("DEPRECATION")
        @JvmStatic
        private fun queryIntentActivitiesPreApi33(
            packageManager: PackageManager,
            intent: Intent
        ): List<ResolveInfo> {
            return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }
}
