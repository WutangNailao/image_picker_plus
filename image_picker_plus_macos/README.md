# image\_picker\_plus\_macos

A macOS implementation of [`image_picker_plus`][1].

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

### Entitlements

This package is currently implemented using [`file_selector`][3], so you will
need to add a read-only file acces [entitlement][4]:
```xml
    <key>com.apple.security.files.user-selected.read-only</key>
    <true/>
```

## Alternatives

If you would prefer an implementation that uses the dedicated photo picker UI
available in newer versions of macOS, which is more similar to the iOS
experience, you may want to consider using
[an alternate, unendorsed implementation built by the community][5].

[1]: https://pub.dev/packages/image_picker_plus
[3]: https://pub.dev/packages/file_selector
[4]: https://flutter.dev/to/macos-entitlements
[5]: https://pub.dev/packages?q=topic%3Aimage-picker+platform%3Amacos
