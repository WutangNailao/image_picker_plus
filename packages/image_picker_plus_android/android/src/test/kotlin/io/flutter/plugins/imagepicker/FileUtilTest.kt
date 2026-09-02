// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets.UTF_8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class FileUtilTest {
    private lateinit var context: Context
    private lateinit var fileUtils: FileUtils
    private lateinit var shadowContentResolver: ShadowContentResolver

    @Before
    @Suppress("DEPRECATION")
    fun before() {
        context = ApplicationProvider.getApplicationContext()
        shadowContentResolver = shadowOf(context.contentResolver)
        fileUtils = FileUtils()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val mimeTypeMap = shadowOf(MimeTypeMap.getSingleton())
            mimeTypeMap.addExtensionMimeTypeMapping("jpg", "image/jpeg")
            mimeTypeMap.addExtensionMimeTypeMapping("png", "image/png")
            mimeTypeMap.addExtensionMimeTypeMapping("webp", "image/webp")
        }
    }

    @Test
    fun FileUtil_GetPathFromUri() {
        val uri = Uri.parse("content://dummy/dummy.png")
        shadowContentResolver.registerInputStream(uri, ByteArrayInputStream("imageStream".toByteArray(UTF_8)))
        val path = fileUtils.getPathFromUri(context, uri)
        val file = File(path!!)
        val bytes = ByteArray(file.length().toInt())

        BufferedInputStream(FileInputStream(file)).use { it.read(bytes, 0, bytes.size) }

        assertTrue(bytes.isNotEmpty())
        assertEquals("imageStream", String(bytes, UTF_8))
    }

    @Test
    fun FileUtil_GetPathFromUri_securityException() {
        val uri = Uri.parse("content://dummy/dummy.png")

        val mockContentResolver = mock(ContentResolver::class.java)
        `when`(mockContentResolver.openInputStream(any(Uri::class.java))).thenThrow(SecurityException::class.java)

        val mockContext = mock(Context::class.java)
        `when`(mockContext.contentResolver).thenReturn(mockContentResolver)

        val path = fileUtils.getPathFromUri(mockContext, uri)

        assertNull(path)
    }

    @Test
    fun FileUtil_getImageExtension() {
        val uri = Uri.parse("content://dummy/dummy.png")
        shadowContentResolver.registerInputStream(uri, ByteArrayInputStream("imageStream".toByteArray(UTF_8)))
        val path = fileUtils.getPathFromUri(context, uri)
        assertTrue(path!!.endsWith(".jpg"))
    }

    @Test
    fun FileUtil_getImageName() {
        val uri = MockContentProvider.PNG_URI
        Robolectric.buildContentProvider(MockContentProvider::class.java).create("dummy")
        shadowContentResolver.registerInputStream(uri, ByteArrayInputStream("imageStream".toByteArray(UTF_8)))
        val path = fileUtils.getPathFromUri(context, uri)
        assertTrue(path!!.endsWith("a.b.png"))
    }

    @Test
    fun FileUtil_getPathFromUri_noExtensionInBaseName() {
        val uri = MockContentProvider.NO_EXTENSION_URI
        Robolectric.buildContentProvider(MockContentProvider::class.java).create("dummy")
        shadowContentResolver.registerInputStream(uri, ByteArrayInputStream("imageStream".toByteArray(UTF_8)))
        val path = fileUtils.getPathFromUri(context, uri)
        assertTrue(path!!.endsWith("abc.png"))
    }

    @Test
    fun FileUtil_getImageName_mismatchedType() {
        val uri = MockContentProvider.WEBP_URI
        Robolectric.buildContentProvider(MockContentProvider::class.java).create("dummy")
        shadowContentResolver.registerInputStream(uri, ByteArrayInputStream("imageStream".toByteArray(UTF_8)))
        val path = fileUtils.getPathFromUri(context, uri)
        assertTrue(path!!.endsWith("c.d.webp"))
    }

    @Test
    fun getPathFromUri_sanitizesPathIndirection() {
        val uri = Uri.parse(MockMaliciousContentProvider.PNG_URI)
        Robolectric.buildContentProvider(MockMaliciousContentProvider::class.java).create("dummy")
        shadowContentResolver.registerInputStream(uri, ByteArrayInputStream("fileStream".toByteArray(UTF_8)))
        val path = fileUtils.getPathFromUri(context, uri)
        assertNotNull(path)
        assertTrue(path!!.endsWith("_bar.png"))
        assertFalse(path.contains(".."))
    }

    @Test
    fun FileUtil_getImageName_unknownType() {
        val uri = MockContentProvider.UNKNOWN_URI
        Robolectric.buildContentProvider(MockContentProvider::class.java).create("dummy")
        shadowContentResolver.registerInputStream(uri, ByteArrayInputStream("imageStream".toByteArray(UTF_8)))
        val path = fileUtils.getPathFromUri(context, uri)
        assertTrue(path!!.endsWith("e.f.g"))
    }

    private class MockContentProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?
        ): Cursor {
            val cursor = MatrixCursor(arrayOf(MediaStore.MediaColumns.DISPLAY_NAME))
            cursor.addRow(arrayOf(uri.lastPathSegment))
            return cursor
        }

        override fun getType(uri: Uri): String? {
            if (uri == PNG_URI) return "image/png"
            if (uri == WEBP_URI) return "image/webp"
            if (uri == NO_EXTENSION_URI) return "image/png"
            return null
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

        companion object {
            val PNG_URI: Uri = Uri.parse("content://dummy/a.b.png")
            val WEBP_URI: Uri = Uri.parse("content://dummy/c.d.png")
            val UNKNOWN_URI: Uri = Uri.parse("content://dummy/e.f.g")
            val NO_EXTENSION_URI: Uri = Uri.parse("content://dummy/abc")
        }
    }

    private class MockMaliciousContentProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?
        ): Cursor {
            val cursor = MatrixCursor(arrayOf(MediaStore.MediaColumns.DISPLAY_NAME))
            cursor.addRow(arrayOf("foo/../..bar.png"))
            return cursor
        }

        override fun getType(uri: Uri): String = "image/png"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

        companion object {
            const val PNG_URI = "content://dummy/a.png"
        }
    }
}
