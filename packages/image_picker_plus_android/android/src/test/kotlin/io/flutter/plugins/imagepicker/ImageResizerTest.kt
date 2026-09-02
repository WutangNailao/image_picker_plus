// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

// RobolectricTestRunner always creates a default mock bitmap when reading from file. So we cannot
// actually test the scaling. But we can still test whether the original or scaled file is created.
@RunWith(RobolectricTestRunner::class)
class ImageResizerTest {
    private lateinit var resizer: ImageResizer
    private lateinit var mockContext: Context
    private lateinit var imageFile: File
    private lateinit var svgImageFile: File
    private lateinit var tallJPG: File
    private lateinit var wideJPG: File
    private lateinit var externalDirectory: File
    private lateinit var originalImageBitmap: Bitmap
    private lateinit var mockCloseable: AutoCloseable

    @Before
    fun setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this)
        imageFile = File(javaClass.classLoader!!.getResource("pngImage.png")!!.file)
        svgImageFile = File(javaClass.classLoader!!.getResource("flutter_image.svg")!!.file)
        tallJPG = File(javaClass.classLoader!!.getResource("jpgImageTall.jpg")!!.file)
        wideJPG = File(javaClass.classLoader!!.getResource("jpgImageWide.jpg")!!.file)
        originalImageBitmap = BitmapFactory.decodeFile(imageFile.path)
        val temporaryFolder = TemporaryFolder()
        temporaryFolder.create()
        externalDirectory = temporaryFolder.newFolder("image_picker_testing_path")
        mockContext = mock(Context::class.java)
        `when`(mockContext.cacheDir).thenReturn(externalDirectory)
        resizer = ImageResizer(mockContext, ExifDataCopier())
    }

    @After
    fun tearDown() {
        mockCloseable.close()
    }

    @Test
    fun onResizeImageIfNeeded_whenQualityIsMax_shouldNotResize_returnTheUnscaledFile() {
        val outputFile = resizer.resizeImageIfNeeded(imageFile.path, null, null, 100)
        assertThat(outputFile, equalTo(imageFile.path))
    }

    @Test
    fun onResizeImageIfNeeded_whenQualityIsNotMax_shouldResize_returnResizedFile() {
        val outputFile = resizer.resizeImageIfNeeded(imageFile.path, null, null, 50)
        assertThat(outputFile, equalTo("${externalDirectory.path}/scaled_pngImage.png"))
    }

    @Test
    fun onResizeImageIfNeeded_whenWidthIsNotNull_shouldResize_returnResizedFile() {
        val outputFile = resizer.resizeImageIfNeeded(imageFile.path, 50.0, null, 100)
        assertThat(outputFile, equalTo("${externalDirectory.path}/scaled_pngImage.png"))
    }

    @Test
    fun onResizeImageIfNeeded_whenHeightIsNotNull_shouldResize_returnResizedFile() {
        val outputFile = resizer.resizeImageIfNeeded(imageFile.path, null, 50.0, 100)
        assertThat(outputFile, equalTo("${externalDirectory.path}/scaled_pngImage.png"))
    }

    @Test
    fun onResizeImageIfNeeded_whenImagePathIsNotBitmap_shouldReturnPathAndNotNull() {
        val nonBitmapImagePath = svgImageFile.path
        Mockito.mockStatic(BitmapFactory::class.java).use { mockedBitmapFactory ->
            mockedBitmapFactory
                .`when`<Bitmap?> { BitmapFactory.decodeFile(nonBitmapImagePath, null) }
                .thenReturn(null)

            val resizedImagePath = resizer.resizeImageIfNeeded(nonBitmapImagePath, null, null, 100)

            assertNotNull(resizedImagePath)
            assertThat(resizedImagePath, equalTo(nonBitmapImagePath))
        }
    }

    @Test
    fun onResizeImageIfNeeded_whenResizeIsNotNecessary_shouldOnlyQueryBitmapDimensions() {
        mockStatic(BitmapFactory::class.java, Mockito.CALLS_REAL_METHODS).use { mockBitmapFactory ->
            resizer.resizeImageIfNeeded(imageFile.path, null, null, 100)
            val argument = ArgumentCaptor.forClass(BitmapFactory.Options::class.java)
            mockBitmapFactory.verify { BitmapFactory.decodeFile(anyString(), argument.capture()) }
            val capturedOptions = argument.value
            assertTrue(capturedOptions.inJustDecodeBounds)
        }
    }

    @Test
    fun onResizeImageIfNeeded_whenResizeIsNecessary_shouldDecodeBitmapPixels() {
        mockStatic(BitmapFactory::class.java, Mockito.CALLS_REAL_METHODS).use { mockBitmapFactory ->
            resizer.resizeImageIfNeeded(imageFile.path, 50.0, 50.0, 100)
            val argument = ArgumentCaptor.forClass(BitmapFactory.Options::class.java)
            mockBitmapFactory.verify(
                MockedStatic.Verification { BitmapFactory.decodeFile(anyString(), argument.capture()) },
                times(2)
            )
            val capturedOptions = argument.allValues
            assertTrue(capturedOptions[0].inJustDecodeBounds)
            assertFalse(capturedOptions[1].inJustDecodeBounds)
        }
    }

    @Test
    fun onResizeImageIfNeeded_whenImageIsVertical_WidthIsGreaterThanOriginal_shouldResizeCorrectly() {
        resizer.resizeImageIfNeeded(tallJPG.path, 5.0, 5.0, 100)
        val originalSize = resizer.readFileDimensions("${externalDirectory.path}/scaled_jpgImageTall.jpg")

        assertThat(originalSize.width, equalTo(3.0F))
        assertThat(originalSize.height, equalTo(5.0F))
    }

    @Test
    fun onResizeImageIfNeeded_whenImageIsVertical_HeightIsGreaterThanOriginal_shouldResizeCorrectly() {
        resizer.resizeImageIfNeeded(tallJPG.path, 3.0, 10.0, 100)
        val originalSize = resizer.readFileDimensions("${externalDirectory.path}/scaled_jpgImageTall.jpg")

        assertThat(originalSize.width, equalTo(3.0F))
        assertThat(originalSize.height, equalTo(5.0F))
    }

    @Test
    fun onResizeImageIfNeeded_whenImageIsVertical_HeightAndWidthIsGreaterThanOriginal_shouldNotResize() {
        resizer.resizeImageIfNeeded(tallJPG.path, 10.0, 10.0, 100)
        val originalSize = resizer.readFileDimensions("${externalDirectory.path}/scaled_jpgImageTall.jpg")

        assertThat(originalSize.width, equalTo(4.0F))
        assertThat(originalSize.height, equalTo(7.0F))
    }

    @Test
    fun onResizeImageIfNeeded_whenImageIsHorizontal_WidthIsGreaterThanOriginal_shouldResizeCorrectly() {
        resizer.resizeImageIfNeeded(wideJPG.path, 10.0, 20.0, 100)
        val originalSize = resizer.readFileDimensions("${externalDirectory.path}/scaled_jpgImageWide.jpg")

        assertThat(originalSize.width, equalTo(10.0F))
        assertThat(originalSize.height, equalTo(6.0F))
    }

    @Test
    fun onResizeImageIfNeeded_whenImageIsHorizontal_HeightIsGreaterThanOriginal_shouldResizeCorrectly() {
        resizer.resizeImageIfNeeded(wideJPG.path, 10.0, 10.0, 100)
        val originalSize = resizer.readFileDimensions("${externalDirectory.path}/scaled_jpgImageWide.jpg")

        assertThat(originalSize.width, equalTo(10.0F))
        assertThat(originalSize.height, equalTo(6.0F))
    }

    @Test
    fun onResizeImageIfNeeded_whenImageIsHorizontal_HeightAndWidthIsGreaterThanOriginal_shouldNotResize() {
        resizer.resizeImageIfNeeded(wideJPG.path, 100.0, 100.0, 100)
        val originalSize = resizer.readFileDimensions("${externalDirectory.path}/scaled_jpgImageWide.jpg")

        assertThat(originalSize.width, equalTo(12.0F))
        assertThat(originalSize.height, equalTo(7.0F))
    }
}
