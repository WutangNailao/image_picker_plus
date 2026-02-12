## 0.0.3

* Update platform implementations:
  * `image_picker_plus_android` to `^0.0.4`
  * `image_picker_plus_ios` to `^0.0.6`
* Pull in Android migration to Kotlin.
* Pull in iOS fixes for nullable `imageQuality` handling to align with platform interface semantics.
* Pull in iOS performance improvements for PHPicker image processing (fast copy path, bounded concurrency, lower memory pressure).

## 0.0.2

* Export `XFileWithMetadata` from platform interface.
* Update example code to display platform-specific metadata (iOS localIdentifier, Android contentUri).
* Update platform implementations:
  * `image_picker_plus_android` to ^0.0.3
  * `image_picker_plus_ios` to ^0.0.5
  * `image_picker_plus_for_web` to ^0.0.2
  * `image_picker_plus_linux` to ^0.0.2
  * `image_picker_plus_macos` to ^0.0.2
  * `image_picker_plus_windows` to ^0.0.2
* Update `image_picker_plus_platform_interface` to ^0.0.3.

## 0.0.1

* Initial release of `image_picker_pluz`.
* Forked from `image_picker` v1.1.2.
* Renamed package from `image_picker` to `image_picker_pluz`.
