// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:typed_data';

import 'package:cross_file/cross_file.dart';

/// An [XFile] with optional iOS Photos local identifier metadata.
class XFileWithLocalIdentifier extends XFile {
  XFileWithLocalIdentifier(
    String path, {
    this.localIdentifier,
    String? name,
    String? mimeType,
    int? length,
    DateTime? lastModified,
    Uint8List? bytes,
  }) : super(
         path,
         name: name,
         mimeType: mimeType,
         length: length,
         lastModified: lastModified,
         bytes: bytes,
       );

  /// The Photos local identifier for iOS picks, when available.
  final String? localIdentifier;
}
