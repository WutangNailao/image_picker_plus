// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:typed_data';

import 'package:cross_file/cross_file.dart';

/// An [XFile] with optional platform-specific metadata.
///
/// This class extends [XFile] to include additional platform-specific
/// identifiers that allow tracing back to the original media source:
///
/// - **iOS**: [localIdentifier] - The Photos library local identifier
/// - **Android**: [contentUri] - The original content:// URI from MediaStore
class XFileWithMetadata extends XFile {
  XFileWithMetadata(
    super.path, {
    this.localIdentifier,
    this.contentUri,
    super.name,
    super.mimeType,
    super.length,
    super.lastModified,
    super.bytes,
  });

  /// The Photos local identifier for iOS picks, when available.
  ///
  /// This identifier can be used with the Photos framework to fetch
  /// the original PHAsset.
  final String? localIdentifier;

  /// The content:// URI for Android picks, when available.
  ///
  /// This is the original URI returned by the Android system picker,
  /// which can be used to access the file through ContentResolver.
  final String? contentUri;
}
