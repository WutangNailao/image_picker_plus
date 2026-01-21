// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import ImageIO
import MobileCoreServices
import UIKit

// MARK: - GIFInfo

public class GIFInfo {
    public let images: [UIImage]
    public let interval: TimeInterval

    public init(images: [UIImage], interval: TimeInterval) {
        self.images = images
        self.interval = interval
    }
}

// MARK: - ImagePickerImageUtil

public class ImagePickerImageUtil {

    private static func drawScaledImage(_ imageToScale: UIImage?, width: Double, height: Double) -> UIImage? {
        guard let image = imageToScale, width > 0, height > 0 else { return nil }

        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: image.imageRendererFormat)

        return renderer.image { context in
            let cgContext = context.cgContext
            cgContext.translateBy(x: 0, y: height)
            cgContext.scaleBy(x: 1, y: -1)
            if let cgImage = image.cgImage {
                cgContext.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
            }
        }
    }

    public static func scaledImage(_ image: UIImage, maxWidth: NSNumber?, maxHeight: NSNumber?, isMetadataAvailable: Bool) -> UIImage {
        let originalWidth = Double(image.size.width)
        let originalHeight = Double(image.size.height)

        let hasMaxWidth = maxWidth != nil
        let hasMaxHeight = maxHeight != nil

        if (originalWidth == maxWidth?.doubleValue && originalHeight == maxHeight?.doubleValue) ||
            (!hasMaxWidth && !hasMaxHeight) {
            return image
        }

        let aspectRatio = originalWidth / originalHeight

        var width = hasMaxWidth ? min(round(maxWidth!.doubleValue), originalWidth) : originalWidth
        var height = hasMaxHeight ? min(round(maxHeight!.doubleValue), originalHeight) : originalHeight

        let shouldDownscaleWidth = hasMaxWidth && maxWidth!.doubleValue < originalWidth
        let shouldDownscaleHeight = hasMaxHeight && maxHeight!.doubleValue < originalHeight
        let shouldDownscale = shouldDownscaleWidth || shouldDownscaleHeight

        if shouldDownscale {
            let widthForMaxHeight = height * aspectRatio
            let heightForMaxWidth = width / aspectRatio

            if heightForMaxWidth > height {
                width = round(widthForMaxHeight)
            } else {
                height = round(heightForMaxWidth)
            }
        }

        if !isMetadataAvailable {
            guard let cgImage = image.cgImage else { return image }
            let imageToScale = UIImage(cgImage: cgImage, scale: 1, orientation: image.imageOrientation)
            return drawScaledImage(imageToScale, width: width, height: height) ?? image
        }

        guard let cgImage = image.cgImage else { return image }
        let imageToScale = UIImage(cgImage: cgImage, scale: 1, orientation: .up)

        switch image.imageOrientation {
        case .left, .right, .leftMirrored, .rightMirrored:
            swap(&width, &height)
        default:
            break
        }

        return drawScaledImage(imageToScale, width: width, height: height) ?? image
    }

    public static func scaledGIFImage(_ data: Data, maxWidth: NSNumber?, maxHeight: NSNumber?) -> GIFInfo? {
        let options: [CFString: Any] = [
            kCGImageSourceShouldCache: true,
            kCGImageSourceTypeIdentifierHint: kUTTypeGIF
        ]

        guard let imageSource = CGImageSourceCreateWithData(data as CFData, options as CFDictionary) else {
            return nil
        }

        let numberOfFrames = CGImageSourceGetCount(imageSource)
        var images: [UIImage] = []
        var interval: TimeInterval = 0.0

        for index in 0..<numberOfFrames {
            guard let cgImage = CGImageSourceCreateImageAtIndex(imageSource, index, options as CFDictionary) else {
                continue
            }

            if let properties = CGImageSourceCopyPropertiesAtIndex(imageSource, index, nil) as? [CFString: Any],
               let gifProperties = properties[kCGImagePropertyGIFDictionary] as? [CFString: Any] {
                var delay = gifProperties[kCGImagePropertyGIFUnclampedDelayTime] as? Double
                if delay == nil {
                    delay = gifProperties[kCGImagePropertyGIFDelayTime] as? Double
                }

                if interval == 0.0, let delay = delay {
                    interval = delay
                }
            }

            var image = UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
            image = scaledImage(image, maxWidth: maxWidth, maxHeight: maxHeight, isMetadataAvailable: true)
            images.append(image)
        }

        return GIFInfo(images: images, interval: interval)
    }
}
