// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter
import io.flutter.plugin.common.BinaryMessenger


@Suppress("DEPRECATION")
class ImagePickerPlugin : FlutterPlugin, ActivityAware, ImagePickerApi {

    private inner class LifeCycleObserver(
        private val thisActivity: Activity
    ) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

        override fun onCreate(owner: LifecycleOwner) {}

        override fun onStart(owner: LifecycleOwner) {}

        override fun onResume(owner: LifecycleOwner) {}

        override fun onPause(owner: LifecycleOwner) {}

        override fun onStop(owner: LifecycleOwner) {
            onActivityStopped(thisActivity)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            onActivityDestroyed(thisActivity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            if (thisActivity == activity && activity.applicationContext != null) {
                (activity.applicationContext as Application)
                    .unregisterActivityLifecycleCallbacks(this)
            }
        }

        override fun onActivityStopped(activity: Activity) {
            if (thisActivity == activity) {
                activityState?.getDelegate()?.saveStateBeforeResult()
            }
        }
    }

    /**
     * Move all activity-lifetime-bound states into this helper object, so that [setup] and
     * [tearDown] would just become constructor and finalize calls of the helper object.
     */
    @VisibleForTesting
    internal inner class ActivityState {
        private var application: Application? = null
        private var activity: Activity? = null
        private var delegate: ImagePickerDelegate? = null
        private var observer: LifeCycleObserver? = null
        private var activityBinding: ActivityPluginBinding? = null
        private var messenger: BinaryMessenger? = null

        // This is null when not using v2 embedding
        private var lifecycle: Lifecycle? = null

        // Default constructor
        constructor(
            application: Application,
            activity: Activity,
            messenger: BinaryMessenger,
            handler: ImagePickerApi,
            activityBinding: ActivityPluginBinding
        ) {
            this.application = application
            this.activity = activity
            this.activityBinding = activityBinding
            this.messenger = messenger

            delegate = constructDelegate(activity)
            ImagePickerApi.setUp(messenger, handler)
            observer = LifeCycleObserver(activity)

            // V2 embedding setup for activity listeners.
            activityBinding.addActivityResultListener(delegate!!)
            activityBinding.addRequestPermissionsResultListener(delegate!!)
            lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(activityBinding)
            lifecycle?.addObserver(observer!!)
        }

        // Only invoked by [ImagePickerPlugin(ImagePickerDelegate, Activity)] for testing.
        constructor(delegate: ImagePickerDelegate, activity: Activity) {
            this.activity = activity
            this.delegate = delegate
            this.messenger = null
            this.application = null
            this.activityBinding = null
            this.observer = null
            this.lifecycle = null
        }

        fun release() {
            activityBinding?.let { binding ->
                delegate?.let { d ->
                    binding.removeActivityResultListener(d)
                    binding.removeRequestPermissionsResultListener(d)
                }
                activityBinding = null
            }

            lifecycle?.let { lc ->
                observer?.let { lc.removeObserver(it) }
                lifecycle = null
            }

            messenger?.let { ImagePickerApi.setUp(it, null) }

            application?.let { app ->
                observer?.let { app.unregisterActivityLifecycleCallbacks(it) }
                application = null
            }

            activity = null
            observer = null
            delegate = null
        }

        fun getActivity(): Activity? = activity

        fun getDelegate(): ImagePickerDelegate? = delegate
    }

    private var pluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    internal var activityState: ActivityState? = null

    /**
     * Default constructor for the plugin.
     *
     * Use this constructor for production code.
     */
    // See also: [ImagePickerPlugin(ImagePickerDelegate, Activity)] for testing.
    constructor()

    @VisibleForTesting
    constructor(delegate: ImagePickerDelegate, activity: Activity) {
        activityState = ActivityState(delegate, activity)
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        pluginBinding = binding
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        pluginBinding = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        setup(
            pluginBinding!!.binaryMessenger,
            pluginBinding!!.applicationContext as Application,
            binding.activity,
            binding
        )
    }

    override fun onDetachedFromActivity() {
        tearDown()
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    private fun setup(
        messenger: BinaryMessenger,
        application: Application,
        activity: Activity,
        activityBinding: ActivityPluginBinding
    ) {
        activityState = ActivityState(application, activity, messenger, this, activityBinding)
    }

    private fun tearDown() {
        activityState?.release()
        activityState = null
    }

    @VisibleForTesting
    fun constructDelegate(setupActivity: Activity): ImagePickerDelegate {
        val cache = ImagePickerCache(setupActivity)

        val exifDataCopier = ExifDataCopier()
        val imageResizer = ImageResizer(setupActivity, exifDataCopier)
        return ImagePickerDelegate(setupActivity, imageResizer, cache)
    }

    private fun getImagePickerDelegate(): ImagePickerDelegate? {
        if (activityState == null || activityState?.getActivity() == null) {
            return null
        }
        return activityState?.getDelegate()
    }

    private fun setCameraDevice(
        delegate: ImagePickerDelegate,
        source: SourceSpecification
    ) {
        val camera = source.camera
        if (camera != null) {
            val device = when (camera) {
                SourceCamera.FRONT -> ImagePickerDelegate.CameraDevice.FRONT
                SourceCamera.REAR -> ImagePickerDelegate.CameraDevice.REAR
            }
            delegate.setCameraDevice(device)
        }
    }

    override fun pickImages(
        source: SourceSpecification,
        options: ImageSelectionOptions,
        generalOptions: GeneralOptions,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        val delegate = getImagePickerDelegate()
        if (delegate == null) {
            callback(
                Result.failure(
                    FlutterError(
                        "no_activity",
                        "image_picker plugin requires a foreground activity.",
                        null
                    )
                )
            )
            return
        }

        setCameraDevice(delegate, source)
        if (generalOptions.allowMultiple) {
            val limit = ImagePickerUtils.getLimitFromOption(generalOptions)

            delegate.chooseMultiImageFromGallery(
                options,
                generalOptions.usePhotoPicker,
                limit,
                callback
            )
        } else {
            when (source.type) {
                SourceType.GALLERY ->
                    delegate.chooseImageFromGallery(options, generalOptions.usePhotoPicker, callback)
                SourceType.CAMERA ->
                    delegate.takeImageWithCamera(options, callback)
            }
        }
    }

    override fun pickMedia(
        mediaSelectionOptions: MediaSelectionOptions,
        generalOptions: GeneralOptions,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        val delegate = getImagePickerDelegate()
        if (delegate == null) {
            callback(
                Result.failure(
                    FlutterError(
                        "no_activity",
                        "image_picker plugin requires a foreground activity.",
                        null
                    )
                )
            )
            return
        }
        delegate.chooseMediaFromGallery(mediaSelectionOptions, generalOptions, callback)
    }

    override fun pickVideos(
        source: SourceSpecification,
        options: VideoSelectionOptions,
        generalOptions: GeneralOptions,
        callback: (Result<List<PickedMedia>>) -> Unit
    ) {
        val delegate = getImagePickerDelegate()
        if (delegate == null) {
            callback(
                Result.failure(
                    FlutterError(
                        "no_activity",
                        "image_picker plugin requires a foreground activity.",
                        null
                    )
                )
            )
            return
        }

        setCameraDevice(delegate, source)
        if (generalOptions.allowMultiple) {
            val limit = ImagePickerUtils.getLimitFromOption(generalOptions)
            delegate.chooseMultiVideoFromGallery(
                options,
                generalOptions.usePhotoPicker,
                limit,
                callback
            )
        } else {
            when (source.type) {
                SourceType.GALLERY ->
                    delegate.chooseVideoFromGallery(options, generalOptions.usePhotoPicker, callback)
                SourceType.CAMERA ->
                    delegate.takeVideoWithCamera(options, callback)
            }
        }
    }

    override fun retrieveLostResults(): CacheRetrievalResult? {
        val delegate = getImagePickerDelegate()
            ?: throw FlutterError(
                "no_activity",
                "image_picker plugin requires a foreground activity.",
                null
            )
        return delegate.retrieveLostImage()
    }
}
