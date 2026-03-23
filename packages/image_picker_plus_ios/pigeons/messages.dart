// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/src/messages.g.dart',
    swiftOut: 'ios/image_picker_ios/Sources/Messages.swift',
    copyrightHeader: 'pigeons/copyright.txt',
  ),
)
class MaxSize {
  MaxSize(this.width, this.height);
  double? width;
  double? height;
}

class MediaSelectionOptions {
  MediaSelectionOptions({
    required this.maxSize,
    this.imageQuality,
    required this.requestFullMetadata,
    required this.allowMultiple,
    this.limit,
  });

  MaxSize maxSize;
  int? imageQuality;
  bool requestFullMetadata;
  bool allowMultiple;
  int? limit;
}

class PickedMedia {
  PickedMedia({required this.path, this.localIdentifier});
  String path;
  String? localIdentifier;
}

// Corresponds to `CameraDevice` from the platform interface package.
enum SourceCamera { rear, front }

// Corresponds to `ImageSource` from the platform interface package.
enum SourceType { camera, gallery }

class SourceSpecification {
  SourceSpecification(this.type, this.camera);
  SourceType type;
  SourceCamera camera;
}

@HostApi()
abstract class ImagePickerApi {
  @async
  @SwiftFunction('pickImage(withSource:maxSize:quality:fullMetadata:)')
  PickedMedia? pickImage(
    SourceSpecification source,
    MaxSize maxSize,
    int? imageQuality,
    bool requestFullMetadata,
  );
  @async
  @SwiftFunction('pickMultiImage(withMaxSize:quality:fullMetadata:limit:)')
  List<PickedMedia> pickMultiImage(
    MaxSize maxSize,
    int? imageQuality,
    bool requestFullMetadata,
    int? limit,
  );
  @async
  @SwiftFunction('pickVideo(withSource:maxDuration:)')
  PickedMedia? pickVideo(SourceSpecification source, int? maxDurationSeconds);
  @async
  @SwiftFunction('pickMultiVideo(withMaxDuration:limit:)')
  List<PickedMedia> pickMultiVideo(int? maxDurationSeconds, int? limit);

  /// Selects images and videos and returns their paths.
  @async
  @SwiftFunction('pickMedia(withMediaSelectionOptions:)')
  List<PickedMedia> pickMedia(MediaSelectionOptions mediaSelectionOptions);
}
