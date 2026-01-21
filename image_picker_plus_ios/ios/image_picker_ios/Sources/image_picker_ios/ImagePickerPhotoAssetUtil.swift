// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import ImageIO
import MobileCoreServices
import Photos
import UIKit

// MARK: - ImagePickerPhotoAssetUtil

public class ImagePickerPhotoAssetUtil {

    public static func getAsset(from info: [String: Any]) -> PHAsset? {
        return info[UIImagePickerController.InfoKey.phAsset.rawValue] as? PHAsset
    }

    public static func saveVideo(from videoURL: URL) -> URL? {
        guard FileManager.default.isReadableFile(atPath: videoURL.path) else {
            return nil
        }

        let fileName = videoURL.lastPathComponent
        let destinationPath = temporaryFilePath(fileName)
        let destination = URL(fileURLWithPath: destinationPath)

        do {
            try FileManager.default.copyItem(at: videoURL, to: destination)
            return destination
        } catch {
            return nil
        }
    }

    public static func saveImage(
        withOriginalImageData originalImageData: Data?,
        image: UIImage,
        maxWidth: NSNumber?,
        maxHeight: NSNumber?,
        imageQuality: NSNumber
    ) -> String? {
        var suffix = ImagePickerMetaDataUtil.defaultSuffix
        var type = ImagePickerMetaDataUtil.defaultMIMEType
        var metaData: [String: Any]?

        if let data = originalImageData {
            type = ImagePickerMetaDataUtil.getImageMIMEType(from: data)
            suffix = ImagePickerMetaDataUtil.imageTypeSuffix(from: type) ?? ImagePickerMetaDataUtil.defaultSuffix
            metaData = ImagePickerMetaDataUtil.getMetaData(from: data)
        }

        if type == .gif, let data = originalImageData {
            guard let gifInfo = ImagePickerImageUtil.scaledGIFImage(data, maxWidth: maxWidth, maxHeight: maxHeight) else {
                return nil
            }
            return saveImage(with: metaData, gifInfo: gifInfo, suffix: suffix)
        } else {
            return saveImage(with: metaData, image: image, suffix: suffix, type: type, imageQuality: imageQuality)
        }
    }

    public static func saveImage(
        with info: [String: Any]?,
        image: UIImage,
        imageQuality: NSNumber
    ) -> String? {
        let metaData = info?[UIImagePickerController.InfoKey.mediaMetadata.rawValue] as? [String: Any]
        return saveImage(
            with: metaData,
            image: image,
            suffix: ImagePickerMetaDataUtil.defaultSuffix,
            type: ImagePickerMetaDataUtil.defaultMIMEType,
            imageQuality: imageQuality
        )
    }

    private static func saveImage(
        with metaData: [String: Any]?,
        gifInfo: GIFInfo,
        suffix: String
    ) -> String? {
        let path = temporaryFilePath(suffix)
        return saveImage(with: metaData, gifInfo: gifInfo, path: path)
    }

    private static func saveImage(
        with metaData: [String: Any]?,
        image: UIImage,
        suffix: String,
        type: ImagePickerMIMEType,
        imageQuality: NSNumber
    ) -> String? {
        var data = ImagePickerMetaDataUtil.convertImage(image, using: type, quality: imageQuality)

        if let meta = metaData, let updatedData = ImagePickerMetaDataUtil.image(from: data, with: meta) {
            data = updatedData
        }

        return createFile(data, suffix: suffix)
    }

    private static func saveImage(
        with metaData: [String: Any]?,
        gifInfo: GIFInfo,
        path: String
    ) -> String? {
        let url = URL(fileURLWithPath: path)

        guard let destination = CGImageDestinationCreateWithURL(url as CFURL, kUTTypeGIF, gifInfo.images.count, nil) else {
            return nil
        }

        let frameProperties: [CFString: Any] = [
            kCGImagePropertyGIFDictionary: [
                kCGImagePropertyGIFDelayTime: gifInfo.interval
            ]
        ]

        var gifMetaProperties = metaData ?? [:]
        var gifProperties = gifMetaProperties[kCGImagePropertyGIFDictionary as String] as? [String: Any] ?? [:]
        gifProperties[kCGImagePropertyGIFLoopCount as String] = 0
        gifMetaProperties[kCGImagePropertyGIFDictionary as String] = gifProperties

        CGImageDestinationSetProperties(destination, gifMetaProperties as CFDictionary)

        for image in gifInfo.images {
            if let cgImage = image.cgImage {
                CGImageDestinationAddImage(destination, cgImage, frameProperties as CFDictionary)
            }
        }

        CGImageDestinationFinalize(destination)

        return path
    }

    public static func temporaryFilePath(_ suffix: String) -> String {
        let guid = ProcessInfo.processInfo.globallyUniqueString
        let fileName = "image_picker_\(guid)\(suffix)"
        let tmpDirectory = NSTemporaryDirectory()
        return (tmpDirectory as NSString).appendingPathComponent(fileName)
    }

    private static func createFile(_ data: Data, suffix: String) -> String? {
        let path = temporaryFilePath(suffix)
        if FileManager.default.createFile(atPath: path, contents: data, attributes: nil) {
            return path
        }
        return nil
    }
}
