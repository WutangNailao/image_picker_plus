// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.util.SizeFCompat
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min
import kotlin.math.round

class ImageResizer(
    private val context: Context,
    private val exifDataCopier: ExifDataCopier
) {
    /**
     * If necessary, resizes the image located in imagePath and then returns the path for the scaled
     * image.
     *
     * If no resizing is needed, returns the path for the original image.
     */
    fun resizeImageIfNeeded(
        imagePath: String,
        maxWidth: Double?,
        maxHeight: Double?,
        imageQuality: Int
    ): String {
        val originalSize = readFileDimensions(imagePath)
        if (originalSize.width == -1f || originalSize.height == -1f) {
            return imagePath
        }
        val shouldScale = maxWidth != null || maxHeight != null || imageQuality < 100
        if (!shouldScale) {
            return imagePath
        }
        return try {
            val pathParts = imagePath.split("/")
            val imageName = pathParts.last()
            val targetSize = calculateTargetSize(
                originalSize.width.toDouble(),
                originalSize.height.toDouble(),
                maxWidth,
                maxHeight
            )
            val options = BitmapFactory.Options()
            options.inSampleSize =
                calculateSampleSize(options, targetSize.width.toInt(), targetSize.height.toInt())
            val bmp = decodeFile(imagePath, options) ?: return imagePath
            val file = resizedImage(
                bmp,
                targetSize.width.toDouble(),
                targetSize.height.toDouble(),
                imageQuality,
                imageName
            )
            copyExif(imagePath, file.path)
            file.path
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    private fun resizedImage(
        bmp: Bitmap,
        width: Double,
        height: Double,
        imageQuality: Int,
        outputImageName: String
    ): File {
        val scaledBmp = createScaledBitmap(bmp, width.toInt(), height.toInt(), false)
        return createImageOnExternalDirectory("/scaled_$outputImageName", scaledBmp, imageQuality)
    }

    private fun calculateTargetSize(
        originalWidth: Double,
        originalHeight: Double,
        maxWidth: Double?,
        maxHeight: Double?
    ): SizeFCompat {
        val aspectRatio = originalWidth / originalHeight

        val hasMaxWidth = maxWidth != null
        val hasMaxHeight = maxHeight != null

        var width = if (hasMaxWidth) min(originalWidth, round(maxWidth!!)) else originalWidth
        var height = if (hasMaxHeight) min(originalHeight, round(maxHeight!!)) else originalHeight

        val shouldDownscaleWidth = hasMaxWidth && maxWidth!! < originalWidth
        val shouldDownscaleHeight = hasMaxHeight && maxHeight!! < originalHeight
        val shouldDownscale = shouldDownscaleWidth || shouldDownscaleHeight

        if (shouldDownscale) {
            val widthForMaxHeight = height * aspectRatio
            val heightForMaxWidth = width / aspectRatio

            if (heightForMaxWidth > height) {
                width = round(widthForMaxHeight)
            } else {
                height = round(heightForMaxWidth)
            }
        }

        return SizeFCompat(width.toFloat(), height.toFloat())
    }

    private fun createFile(externalFilesDirectory: File, child: String): File {
        val image = File(externalFilesDirectory, child)
        if (!image.parentFile!!.exists()) {
            image.parentFile!!.mkdirs()
        }
        return image
    }

    @Throws(IOException::class)
    private fun createOutputStream(imageFile: File): FileOutputStream {
        return FileOutputStream(imageFile)
    }

    private fun copyExif(filePathOri: String, filePathDest: String) {
        try {
            exifDataCopier.copyExif(ExifInterface(filePathOri), ExifInterface(filePathDest))
        } catch (ex: Exception) {
            Log.e("ImageResizer", "Error preserving Exif data on selected image: $ex")
        }
    }

    @VisibleForTesting
    fun readFileDimensions(path: String): SizeFCompat {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        decodeFile(path, options)
        return SizeFCompat(options.outWidth.toFloat(), options.outHeight.toFloat())
    }

    private fun decodeFile(path: String, opts: BitmapFactory.Options?): Bitmap? {
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun createScaledBitmap(bmp: Bitmap, width: Int, height: Int, filter: Boolean): Bitmap {
        return Bitmap.createScaledBitmap(bmp, width, height, filter)
    }

    /**
     * Calculates the largest sample size value that is a power of two based on a target width and
     * height.
     *
     * This value is necessary to tell the Bitmap decoder to subsample the original image,
     * returning a smaller image to save memory.
     *
     * @see [Loading Large Bitmaps Efficiently](https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap)
     */
    private fun calculateSampleSize(
        options: BitmapFactory.Options,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var sampleSize = 1
        if (height > targetHeight || width > targetWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sampleSize >= targetHeight && halfWidth / sampleSize >= targetWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    @Throws(IOException::class)
    private fun createImageOnExternalDirectory(
        name: String,
        bitmap: Bitmap,
        imageQuality: Int
    ): File {
        val outputStream = ByteArrayOutputStream()
        val saveAsPNG = bitmap.hasAlpha()
        if (saveAsPNG) {
            Log.d(
                "ImageResizer",
                "image_picker: compressing is not supported for type PNG. Returning the image with original quality"
            )
        }
        bitmap.compress(
            if (saveAsPNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
            imageQuality,
            outputStream
        )

        val cacheDirectory = context.cacheDir
        val imageFile = createFile(cacheDirectory, name)
        createOutputStream(imageFile).use { fileOutput ->
            fileOutput.write(outputStream.toByteArray())
        }
        return imageFile
    }
}
