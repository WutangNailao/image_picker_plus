// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import ImageIO
import UIKit

// MARK: - ImagePickerMIMEType

public enum ImagePickerMIMEType {
    case png
    case jpeg
    case gif
    case other
}

// MARK: - ImagePickerMetaDataUtil

public class ImagePickerMetaDataUtil {

    public static let defaultSuffix = ".jpg"
    public static let defaultMIMEType: ImagePickerMIMEType = .jpeg

    private static let firstByteJPEG: UInt8 = 0xFF
    private static let firstBytePNG: UInt8 = 0x89
    private static let firstByteGIF: UInt8 = 0x47

    public static func getImageMIMEType(from imageData: Data) -> ImagePickerMIMEType {
        guard !imageData.isEmpty else { return .other }

        var firstByte: UInt8 = 0
        imageData.copyBytes(to: &firstByte, count: 1)

        switch firstByte {
        case firstByteJPEG:
            return .jpeg
        case firstBytePNG:
            return .png
        case firstByteGIF:
            return .gif
        default:
            return .other
        }
    }

    public static func imageTypeSuffix(from type: ImagePickerMIMEType) -> String? {
        switch type {
        case .jpeg:
            return ".jpg"
        case .png:
            return ".png"
        case .gif:
            return ".gif"
        case .other:
            return nil
        }
    }

    public static func getMetaData(from imageData: Data) -> [String: Any]? {
        guard let source = CGImageSourceCreateWithData(imageData as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any] else {
            return nil
        }
        return properties
    }

    public static func image(from imageData: Data, with metadata: [String: Any]) -> Data? {
        let targetData = NSMutableData()

        guard let source = CGImageSourceCreateWithData(imageData as CFData, nil),
              let sourceType = CGImageSourceGetType(source),
              let destination = CGImageDestinationCreateWithData(targetData, sourceType, 1, nil) else {
            return nil
        }

        CGImageDestinationAddImageFromSource(destination, source, 0, metadata as CFDictionary)
        CGImageDestinationFinalize(destination)

        return targetData as Data
    }

    public static func convertImage(_ image: UIImage, using type: ImagePickerMIMEType, quality: NSNumber?) -> Data {
        if quality != nil && type != .jpeg {
            print("image_picker: compressing is not supported for type \(imageTypeSuffix(from: type) ?? "unknown"). Returning the image with original quality")
        }

        switch type {
        case .jpeg:
            let qualityFloat = quality?.floatValue ?? 1.0
            return image.jpegData(compressionQuality: CGFloat(qualityFloat)) ?? Data()
        case .png:
            return image.pngData() ?? Data()
        default:
            let qualityFloat = quality?.floatValue ?? 1.0
            return image.jpegData(compressionQuality: CGFloat(qualityFloat)) ?? Data()
        }
    }
}
