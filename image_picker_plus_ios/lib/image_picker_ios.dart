// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
import 'dart:async';

import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:image_picker_plus_platform_interface/image_picker_plus_platform_interface.dart';

import 'src/messages.g.dart';

// Converts an [ImageSource] to the corresponding Pigeon API enum value.
SourceType _convertSource(ImageSource source) {
  switch (source) {
    case ImageSource.camera:
      return SourceType.camera;
    case ImageSource.gallery:
      return SourceType.gallery;
  }
  // The enum comes from a different package, which could get a new value at
  // any time, so a fallback case is necessary. Since there is no reasonable
  // default behavior, throw to alert the client that they need an updated
  // version. This is deliberately outside the switch rather than a `default`
  // so that the linter will flag the switch as needing an update.
  // ignore: dead_code
  throw UnimplementedError('Unknown source: $source');
}

// Converts a [CameraDevice] to the corresponding Pigeon API enum value.
SourceCamera _convertCamera(CameraDevice camera) {
  switch (camera) {
    case CameraDevice.front:
      return SourceCamera.front;
    case CameraDevice.rear:
      return SourceCamera.rear;
  }
  // The enum comes from a different package, which could get a new value at
  // any time, so a fallback case is necessary. Since there is no reasonable
  // default behavior, throw to alert the client that they need an updated
  // version. This is deliberately outside the switch rather than a `default`
  // so that the linter will flag the switch as needing an update.
  // ignore: dead_code
  throw UnimplementedError('Unknown camera: $camera');
}

/// An implementation of [ImagePickerPlatform] for iOS.
class ImagePickerIOS extends ImagePickerPlatform {
  /// Creates a new plugin implementation instance.
  ImagePickerIOS({@visibleForTesting ImagePickerApi? api})
    : _hostApi = api ?? ImagePickerApi();

  final ImagePickerApi _hostApi;

  /// Registers this class as the default platform implementation.
  static void registerWith() {
    ImagePickerPlatform.instance = ImagePickerIOS();
  }

  @override
  Future<PickedFile?> pickImage({
    required ImageSource source,
    double? maxWidth,
    double? maxHeight,
    int? imageQuality,
    CameraDevice preferredCameraDevice = CameraDevice.rear,
  }) async {
    final PickedMedia? result = await _pickImageAsPath(
      source: source,
      options: ImagePickerOptions(
        maxWidth: maxWidth,
        maxHeight: maxHeight,
        imageQuality: imageQuality,
        preferredCameraDevice: preferredCameraDevice,
      ),
    );
    return result != null ? PickedFile(result.path) : null;
  }

  @override
  Future<XFile?> getImageFromSource({
    required ImageSource source,
    ImagePickerOptions options = const ImagePickerOptions(),
  }) async {
    final PickedMedia? result = await _pickImageAsPath(
      source: source,
      options: options,
    );
    return result != null ? _xFileFromResult(result) : null;
  }

  @override
  Future<List<PickedFile>?> pickMultiImage({
    double? maxWidth,
    double? maxHeight,
    int? imageQuality,
  }) async {
    final List<PickedMedia> results = await _pickMultiImageAsPath(
      options: MultiImagePickerOptions(
        imageOptions: ImageOptions(
          maxWidth: maxWidth,
          maxHeight: maxHeight,
          imageQuality: imageQuality,
        ),
      ),
    );
    // Convert an empty list to a null return since that was the legacy behavior
    // of this method.
    if (results.isEmpty) {
      return null;
    }

    return results
        .map((PickedMedia result) => PickedFile(result.path))
        .toList();
  }

  @override
  Future<List<XFile>> getMultiImageWithOptions({
    MultiImagePickerOptions options = const MultiImagePickerOptions(),
  }) async {
    final List<PickedMedia> results = await _pickMultiImageAsPath(
      options: options,
    );
    return results.map(_xFileFromResult).toList();
  }

  Future<List<PickedMedia>> _pickMultiImageAsPath({
    MultiImagePickerOptions options = const MultiImagePickerOptions(),
  }) async {
    final int? imageQuality = options.imageOptions.imageQuality;
    if (imageQuality != null && (imageQuality < 0 || imageQuality > 100)) {
      throw ArgumentError.value(
        imageQuality,
        'imageQuality',
        'must be between 0 and 100',
      );
    }

    final double? maxWidth = options.imageOptions.maxWidth;
    if (maxWidth != null && maxWidth < 0) {
      throw ArgumentError.value(maxWidth, 'maxWidth', 'cannot be negative');
    }

    final double? maxHeight = options.imageOptions.maxHeight;
    if (maxHeight != null && maxHeight < 0) {
      throw ArgumentError.value(maxHeight, 'maxHeight', 'cannot be negative');
    }

    final int? limit = options.limit;
    if (limit != null && limit < 2) {
      throw ArgumentError.value(limit, 'limit', 'cannot be lower than 2');
    }

    return _hostApi.pickMultiImage(
      MaxSize(width: maxWidth, height: maxHeight),
      imageQuality,
      options.imageOptions.requestFullMetadata,
      limit,
    );
  }

  Future<PickedMedia?> _pickImageAsPath({
    required ImageSource source,
    ImagePickerOptions options = const ImagePickerOptions(),
  }) {
    final int? imageQuality = options.imageQuality;
    if (imageQuality != null && (imageQuality < 0 || imageQuality > 100)) {
      throw ArgumentError.value(
        imageQuality,
        'imageQuality',
        'must be between 0 and 100',
      );
    }

    final double? maxHeight = options.maxHeight;
    final double? maxWidth = options.maxWidth;
    if (maxWidth != null && maxWidth < 0) {
      throw ArgumentError.value(maxWidth, 'maxWidth', 'cannot be negative');
    }

    if (maxHeight != null && maxHeight < 0) {
      throw ArgumentError.value(maxHeight, 'maxHeight', 'cannot be negative');
    }

    return _hostApi.pickImage(
      SourceSpecification(
        type: _convertSource(source),
        camera: _convertCamera(options.preferredCameraDevice),
      ),
      MaxSize(width: maxWidth, height: maxHeight),
      imageQuality,
      options.requestFullMetadata,
    );
  }

  @override
  Future<List<XFile>> getMedia({required MediaOptions options}) async {
    final MediaSelectionOptions mediaSelectionOptions =
        _mediaOptionsToMediaSelectionOptions(options);

    final List<PickedMedia> results = await _hostApi.pickMedia(
      mediaSelectionOptions,
    );
    return results.map(_xFileFromResult).toList();
  }

  MaxSize _imageOptionsToMaxSizeWithValidation(ImageOptions imageOptions) {
    final double? maxHeight = imageOptions.maxHeight;
    final double? maxWidth = imageOptions.maxWidth;
    final int? imageQuality = imageOptions.imageQuality;

    if (imageQuality != null && (imageQuality < 0 || imageQuality > 100)) {
      throw ArgumentError.value(
        imageQuality,
        'imageQuality',
        'must be between 0 and 100',
      );
    }

    if (maxWidth != null && maxWidth < 0) {
      throw ArgumentError.value(maxWidth, 'maxWidth', 'cannot be negative');
    }

    if (maxHeight != null && maxHeight < 0) {
      throw ArgumentError.value(maxHeight, 'maxHeight', 'cannot be negative');
    }

    return MaxSize(width: maxWidth, height: maxHeight);
  }

  MediaSelectionOptions _mediaOptionsToMediaSelectionOptions(
    MediaOptions mediaOptions,
  ) {
    final MaxSize maxSize = _imageOptionsToMaxSizeWithValidation(
      mediaOptions.imageOptions,
    );

    final bool allowMultiple = mediaOptions.allowMultiple;
    final int? limit = mediaOptions.limit;

    if (!allowMultiple && limit != null) {
      throw ArgumentError.value(
        allowMultiple,
        'allowMultiple',
        'cannot be false, when limit is not null',
      );
    }

    if (limit != null && limit < 2) {
      throw ArgumentError.value(limit, 'limit', 'cannot be lower than 2');
    }

    return MediaSelectionOptions(
      maxSize: maxSize,
      imageQuality: mediaOptions.imageOptions.imageQuality,
      requestFullMetadata: mediaOptions.imageOptions.requestFullMetadata,
      allowMultiple: mediaOptions.allowMultiple,
      limit: mediaOptions.limit,
    );
  }

  @override
  Future<PickedFile?> pickVideo({
    required ImageSource source,
    CameraDevice preferredCameraDevice = CameraDevice.rear,
    Duration? maxDuration,
  }) async {
    final PickedMedia? result = await _pickVideoAsPath(
      source: source,
      maxDuration: maxDuration,
      preferredCameraDevice: preferredCameraDevice,
    );
    return result != null ? PickedFile(result.path) : null;
  }

  Future<PickedMedia?> _pickVideoAsPath({
    required ImageSource source,
    CameraDevice preferredCameraDevice = CameraDevice.rear,
    Duration? maxDuration,
  }) {
    return _hostApi.pickVideo(
      SourceSpecification(
        type: _convertSource(source),
        camera: _convertCamera(preferredCameraDevice),
      ),
      maxDuration?.inSeconds,
    );
  }

  @override
  Future<XFile?> getImage({
    required ImageSource source,
    double? maxWidth,
    double? maxHeight,
    int? imageQuality,
    CameraDevice preferredCameraDevice = CameraDevice.rear,
  }) async {
    final PickedMedia? result = await _pickImageAsPath(
      source: source,
      options: ImagePickerOptions(
        maxWidth: maxWidth,
        maxHeight: maxHeight,
        imageQuality: imageQuality,
        preferredCameraDevice: preferredCameraDevice,
      ),
    );
    return result != null ? _xFileFromResult(result) : null;
  }

  @override
  Future<List<XFile>?> getMultiImage({
    double? maxWidth,
    double? maxHeight,
    int? imageQuality,
  }) async {
    final List<PickedMedia> results = await _pickMultiImageAsPath(
      options: MultiImagePickerOptions(
        imageOptions: ImageOptions(
          maxWidth: maxWidth,
          maxHeight: maxHeight,
          imageQuality: imageQuality,
        ),
      ),
    );
    // Convert an empty list to a null return since that was the legacy behavior
    // of this method.
    if (results.isEmpty) {
      return null;
    }

    return results.map(_xFileFromResult).toList();
  }

  @override
  Future<XFile?> getVideo({
    required ImageSource source,
    CameraDevice preferredCameraDevice = CameraDevice.rear,
    Duration? maxDuration,
  }) async {
    final PickedMedia? result = await _pickVideoAsPath(
      source: source,
      maxDuration: maxDuration,
      preferredCameraDevice: preferredCameraDevice,
    );
    return result != null ? _xFileFromResult(result) : null;
  }

  @override
  Future<List<XFile>> getMultiVideoWithOptions({
    MultiVideoPickerOptions options = const MultiVideoPickerOptions(),
  }) async {
    final List<PickedMedia> results = await _hostApi.pickMultiVideo(
      options.maxDuration?.inSeconds,
      options.limit,
    );
    return results.map(_xFileFromResult).toList();
  }

  XFile _xFileFromResult(PickedMedia result) {
    return XFileWithMetadata(
      result.path,
      name: result.path.split('/').last,
      localIdentifier: result.localIdentifier,
    );
  }
}
