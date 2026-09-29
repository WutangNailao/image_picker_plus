## 0.0.11

* Wait for camera photo and video capture to finish dismissing `UIImagePickerController` before returning results.
* Keep delayed camera results tied to their original request so cancelled captures cannot complete a newer request.

## 0.0.10

* Wait for PHPicker dismissal and media processing to finish before returning the selection result, so a subsequent native full-screen presentation can start safely.
* Ignore stale picker callbacks from cancelled requests.

## 0.0.9

* Rename the Swift package directory to `ios/image_picker_plus_ios` so Flutter can detect Swift Package Manager support.
* Include the federated plugin declaration correction from the retracted 0.0.8 release.

## 0.0.8

* Correct the federated plugin declaration to implement `image_picker_pluz`.
* Retracted because the Swift package directory did not match the plugin name.

## 0.0.7

* Update iOS Pigeon definitions and generated code to use Swift output instead of Objective-C selectors.
* Update the Swift plugin implementation to match the generated Swift Pigeon API.
* Improve iOS view controller lookup for scene-based apps by preferring Flutter's registrar view controller and active window scenes.
* Simplify the iOS source layout by flattening files under `ios/image_picker_ios/Sources`.

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
