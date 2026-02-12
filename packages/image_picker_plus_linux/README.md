# image\_picker\_plus\_linux

A Linux implementation of [`image_picker_plus`][1].

## Limitations

`ImageSource.camera` is not supported unless a `cameraDelegate` is set.

### pickImage()
The arguments `maxWidth`, `maxHeight`, and `imageQuality` are not currently supported.

### pickVideo()
The argument `maxDuration` is not currently supported.

## Usage

### Import the package

Most apps should depend on `image_picker_plus` rather than this package
directly. This package is pulled in automatically when you use
`image_picker_plus`.

However, if you `import` this package to use any of its APIs directly, you
should add it to your `pubspec.yaml` as usual.

[1]: https://pub.dev/packages/image_picker_plus
