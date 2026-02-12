// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/*
 * Copyright (C) 2007-2008 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file was modified by the Flutter authors from the following original file:
 * https://raw.githubusercontent.com/iPaulPro/aFileChooser/master/aFileChooser/src/com/ipaulpro/afilechooser/utils/FileUtils.java
 */

package io.flutter.plugins.imagepicker

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import io.flutter.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class FileUtils {
    /**
     * Copies the file from the given content URI to a temporary directory, retaining the original
     * file name if possible.
     *
     * If the filename contains path indirection or separators (.. or /), the end file name will be
     * the segment after the final separator, with indirection replaced by underscores. E.g.
     * "example/../..file.png" -> "_file.png". See: [Improperly trusting ContentProvider-provided filename](https://developer.android.com/privacy-and-security/risks/untrustworthy-contentprovider-provided-filename).
     *
     * Each file is placed in its own directory to avoid conflicts according to the following
     * scheme: {cacheDir}/{randomUuid}/{fileName}
     *
     * File extension is changed to match MIME type of the file, if known. Otherwise, the extension
     * is left unchanged.
     *
     * If the original file name is unknown, a predefined "image_picker" filename is used and the
     * file extension is deduced from the mime type (with fallback to ".jpg" in case of failure).
     */
    fun getPathFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val uuid = UUID.randomUUID().toString()
                val targetDirectory = File(context.cacheDir, uuid)
                targetDirectory.mkdir()
                // TODO(SynSzakala) according to the docs, `deleteOnExit` does not work reliably on Android; we should preferably
                //  just clear the picked files after the app startup.
                targetDirectory.deleteOnExit()
                var fileName = getImageName(context, uri)
                val extension = getImageExtension(context, uri)

                if (fileName == null) {
                    Log.w("FileUtils", "Cannot get file name for $uri")
                    fileName = "image_picker${extension ?: ".jpg"}"
                } else if (extension != null) {
                    fileName = getBaseName(fileName) + extension
                }
                val filePath = File(targetDirectory, fileName).path
                val outputFile = saferOpenFile(filePath, targetDirectory.canonicalPath)
                FileOutputStream(outputFile).use { outputStream ->
                    copy(inputStream, outputStream)
                    outputFile.path
                }
            }
        } catch (e: IOException) {
            // If closing the output stream fails, we cannot be sure that the
            // target file was written in full. Flushing the stream merely moves
            // the bytes into the OS, not necessarily to the file.
            null
        } catch (e: SecurityException) {
            // Calling `ContentResolver#openInputStream()` has been reported to throw a
            // `SecurityException` on some devices in certain circumstances. Instead of crashing, we
            // return `null`.
            //
            // See https://github.com/flutter/flutter/issues/100025 for more details.
            null
        } catch (e: IllegalArgumentException) {
            // This is likely a result of an IllegalArgumentException that we have thrown in
            // saferOpenFile(). TODO(gmackall): surface this error in dart.
            null
        }
    }

    companion object {
        /** @return extension of image with dot, or null if it's empty. */
        private fun getImageExtension(context: Context, uriImage: Uri): String? {
            val extension: String? = try {
                if (uriImage.scheme == ContentResolver.SCHEME_CONTENT) {
                    val mime = MimeTypeMap.getSingleton()
                    mime.getExtensionFromMimeType(context.contentResolver.getType(uriImage))
                } else {
                    MimeTypeMap.getFileExtensionFromUrl(
                        Uri.fromFile(File(uriImage.path!!)).toString()
                    )
                }
            } catch (e: Exception) {
                return null
            }

            if (extension.isNullOrEmpty()) {
                return null
            }

            return ".${sanitizeFilename(extension)}"
        }

        // From https://developer.android.com/privacy-and-security/risks/untrustworthy-contentprovider-provided-filename#sanitize-provided-filenames.
        @JvmStatic
        protected fun sanitizeFilename(displayName: String?): String? {
            if (displayName == null) {
                return null
            }

            val badCharacters = arrayOf("..", "/")
            val segments = displayName.split("/")
            var fileName = segments.last()
            for (suspString in badCharacters) {
                fileName = fileName.replace(suspString, "_")
            }
            return fileName
        }

        /**
         * Use with file name sanitization and an non-guessable directory. From
         * [Path traversal mitigations](https://developer.android.com/privacy-and-security/risks/path-traversal#path-traversal-mitigations).
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class, IOException::class)
        protected fun saferOpenFile(path: String, expectedDir: String): File {
            val f = File(path)
            val canonicalPath = f.canonicalPath
            if (!canonicalPath.startsWith(expectedDir)) {
                throw IllegalArgumentException(
                    "Trying to open path outside of the expected directory. File: " +
                            "${f.canonicalPath} was expected to be within directory: $expectedDir."
                )
            }
            return f
        }

        /** @return name of the image provided by ContentResolver; this may be null. */
        private fun getImageName(context: Context, uriImage: Uri): String? {
            queryImageName(context, uriImage)?.use { cursor ->
                if (!cursor.moveToFirst() || cursor.columnCount < 1) return null
                val unsanitizedImageName = cursor.getString(0)
                return sanitizeFilename(unsanitizedImageName)
            }
            return null
        }

        private fun queryImageName(context: Context, uriImage: Uri) = context
            .contentResolver
            .query(uriImage, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)

        private fun copy(inputStream: InputStream, out: OutputStream) {
            val buffer = ByteArray(4 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
            out.flush()
        }

        private fun getBaseName(fileName: String): String {
            val lastDotIndex = fileName.lastIndexOf('.')
            if (lastDotIndex < 0) {
                return fileName
            }
            // Basename is everything before the last '.'.
            return fileName.substring(0, lastDotIndex)
        }
    }
}
