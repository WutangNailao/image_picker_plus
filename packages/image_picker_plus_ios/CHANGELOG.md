## 0.0.6

* Fix iOS image quality nullability handling to preserve interface semantics.
* Avoid false non-JPEG compression warnings when `imageQuality` is not provided.
* Optimize PHPicker image processing with bounded concurrency and a fast file-copy path when no resize/compression is requested.
* Reduce memory pressure during image and GIF processing.
* Respect `requestFullMetadata` in the iOS save pipeline to avoid unnecessary metadata work.
* Update package repository URL in `pubspec.yaml`.
* Remove legacy iOS example app files under `example/ios`.

## 0.0.5

* Rename `XFileWithLocalIdentifier` to `XFileWithMetadata`.
* Update `image_picker_plus_platform_interface` to ^0.0.3.

## 0.0.4

* Include PHAsset `localIdentifier` in iOS picker results via Pigeon.
* Update example code.

## 0.0.3

* Rewrite iOS plugin from Objective-C to Swift.
* Update minimum iOS deployment target to 14.0.

## 0.0.2

* Fix umbrella header import path from `image_picker_ios` to `image_picker_plus_ios`.

## 0.0.1

* Initial release of `image_picker_plus_ios`.
* Forked from `image_picker_ios` v0.8.13+4.
* Renamed package from `image_picker_ios` to `image_picker_plus_ios`.
