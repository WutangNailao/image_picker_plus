// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.imagepicker

import androidx.exifinterface.media.ExifInterface
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class ExifDataCopierTest {
    @Mock lateinit var mockOldExif: ExifInterface
    @Mock lateinit var mockNewExif: ExifInterface

    private val exifDataCopier = ExifDataCopier()
    private lateinit var mockCloseable: AutoCloseable

    private val orientationValue = "Horizontal (normal)"
    private val imageWidthValue = "4032"
    private val whitePointValue = "0.96419 1 0.82489"
    private val colorSpaceValue = "Uncalibrated"
    private val exposureTimeValue = "1/9"
    private val exposureModeValue = "Auto"
    private val exifVersionValue = "0232"
    private val makeValue = "Apple"
    private val dateTimeOriginalValue = "2023:02:14 18:55:19"
    private val offsetTimeValue = "+01:00"

    @Before
    fun setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun tearDown() {
        mockCloseable.close()
    }

    @Test
    fun copyExif_copiesOrientationAttribute() {
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_ORIENTATION)).thenReturn(orientationValue)

        exifDataCopier.copyExif(mockOldExif, mockNewExif)

        verify(mockNewExif).setAttribute(ExifInterface.TAG_ORIENTATION, orientationValue)
    }

    @Test
    fun copyExif_doesNotCopyCategory1AttributesExceptForOrientation() {
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)).thenReturn(imageWidthValue)
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_WHITE_POINT)).thenReturn(whitePointValue)
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_COLOR_SPACE)).thenReturn(colorSpaceValue)

        exifDataCopier.copyExif(mockOldExif, mockNewExif)

        verify(mockNewExif, never()).setAttribute(ExifInterface.TAG_IMAGE_WIDTH, imageWidthValue)
        verify(mockNewExif, never()).setAttribute(ExifInterface.TAG_WHITE_POINT, whitePointValue)
        verify(mockNewExif, never()).setAttribute(ExifInterface.TAG_COLOR_SPACE, colorSpaceValue)
    }

    @Test
    fun copyExif_copiesCategory2Attributes() {
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)).thenReturn(exposureTimeValue)
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_EXPOSURE_MODE)).thenReturn(exposureModeValue)
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_EXIF_VERSION)).thenReturn(exifVersionValue)

        exifDataCopier.copyExif(mockOldExif, mockNewExif)

        verify(mockNewExif).setAttribute(ExifInterface.TAG_EXPOSURE_TIME, exposureTimeValue)
        verify(mockNewExif).setAttribute(ExifInterface.TAG_EXPOSURE_MODE, exposureModeValue)
        verify(mockNewExif).setAttribute(ExifInterface.TAG_EXIF_VERSION, exifVersionValue)
    }

    @Test
    fun copyExif_copiesCategory3Attributes() {
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_MAKE)).thenReturn(makeValue)
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            .thenReturn(dateTimeOriginalValue)
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_OFFSET_TIME)).thenReturn(offsetTimeValue)

        exifDataCopier.copyExif(mockOldExif, mockNewExif)

        verify(mockNewExif).setAttribute(ExifInterface.TAG_MAKE, makeValue)
        verify(mockNewExif).setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTimeOriginalValue)
        verify(mockNewExif).setAttribute(ExifInterface.TAG_OFFSET_TIME, offsetTimeValue)
    }

    @Test
    fun copyExif_doesNotCopyUnsetAttributes() {
        `when`(mockOldExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)).thenReturn(null)

        exifDataCopier.copyExif(mockOldExif, mockNewExif)

        verify(mockNewExif, never()).setAttribute(ExifInterface.TAG_EXPOSURE_TIME, null)
    }

    @Test
    fun copyExif_savesAttributes() {
        exifDataCopier.copyExif(mockOldExif, mockNewExif)

        verify(mockNewExif).saveAttributes()
    }
}
