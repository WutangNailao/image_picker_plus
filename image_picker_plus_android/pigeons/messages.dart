// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/messages.g.dart',
    kotlinOut:
        'android/src/main/kotlin/io/flutter/plugins/imagepicker/Messages.kt',
    kotlinOptions: KotlinOptions(package: 'io.flutter.plugins.imagepicker'),
    dartPackageName: "image_picker_plus_android",
    copyrightHeader: 'pigeons/copyright.txt',
  ),
)
class GeneralOptions {
  GeneralOptions(this.allowMultiple, this.usePhotoPicker, this.limit);
  bool allowMultiple;
  bool usePhotoPicker;
  int? limit;
}

/// Options for image selection and output.
class ImageSelectionOptions {
  ImageSelectionOptions({this.maxWidth, this.maxHeight, required this.quality});

  /// If set, the max width that the image should be resized to fit in.
  double? maxWidth;

  /// If set, the max height that the image should be resized to fit in.
  double? maxHeight;

  /// The quality of the output image, from 0-100.
  ///
  /// 100 indicates original quality.
  int quality;
}

class MediaSelectionOptions {
  MediaSelectionOptions({required this.imageSelectionOptions});

  ImageSelectionOptions imageSelectionOptions;
}

/// Options for image selection and output.
class VideoSelectionOptions {
  VideoSelectionOptions({this.maxDurationSeconds});

  /// The maximum desired length for the video, in seconds.
  int? maxDurationSeconds;
}

// Corresponds to `CameraDevice` from the platform interface package.
enum SourceCamera { rear, front }

// Corresponds to `ImageSource` from the platform interface package.
enum SourceType { camera, gallery }

/// Represents a picked media file with its path and original content URI.
class PickedMedia {
  PickedMedia({required this.path, this.contentUri, this.mimeType});

  /// The file system path to the picked media.
  String path;

  /// The original content:// URI from the Android system picker.
  String? contentUri;

  /// The MIME type of the media (e.g., "image/jpeg", "video/mp4").
  String? mimeType;
}

/// Specification for the source of an image or video selection.
class SourceSpecification {
  SourceSpecification(this.type, this.camera);
  SourceType type;
  SourceCamera? camera;
}

/// An error that occurred during lost result retrieval.
///
/// The data here maps to the `PlatformException` that will be created from it.
class CacheRetrievalError {
  CacheRetrievalError({required this.code, this.message});
  final String code;
  final String? message;
}

// Corresponds to `RetrieveType` from the platform interface package.
enum CacheRetrievalType { image, video }

/// The result of retrieving cached results from a previous run.
class CacheRetrievalResult {
  CacheRetrievalResult({
    required this.type,
    this.error,
    this.paths = const <String>[],
  });

  /// The type of the retrieved data.
  final CacheRetrievalType type;

  /// The error from the last selection, if any.
  final CacheRetrievalError? error;

  /// The results from the last selection, if any.
  final List<String> paths;
}

@HostApi()
abstract class ImagePickerApi {
  /// Selects images and returns their paths with content URIs.
  @TaskQueue(type: TaskQueueType.serialBackgroundThread)
  @async
  List<PickedMedia> pickImages(
    SourceSpecification source,
    ImageSelectionOptions options,
    GeneralOptions generalOptions,
  );

  /// Selects video and returns their paths with content URIs.
  @TaskQueue(type: TaskQueueType.serialBackgroundThread)
  @async
  List<PickedMedia> pickVideos(
    SourceSpecification source,
    VideoSelectionOptions options,
    GeneralOptions generalOptions,
  );

  /// Selects images and videos and returns their paths with content URIs.
  @async
  List<PickedMedia> pickMedia(
    MediaSelectionOptions mediaSelectionOptions,
    GeneralOptions generalOptions,
  );

  /// Returns results from a previous app session, if any.
  @TaskQueue(type: TaskQueueType.serialBackgroundThread)
  CacheRetrievalResult? retrieveLostResults();
}
