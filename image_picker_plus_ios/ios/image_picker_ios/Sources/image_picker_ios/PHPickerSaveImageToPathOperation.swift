// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import Flutter
import PhotosUI
import UniformTypeIdentifiers

public typealias GetSavedPath = (String?, FlutterError?) -> Void

// MARK: - PHPickerSaveImageToPathOperation

public class PHPickerSaveImageToPathOperation: Operation {

    private let pickerResult: PHPickerResult
    private let maxHeight: NSNumber?
    private let maxWidth: NSNumber?
    private let desiredImageQuality: NSNumber
    private let requestFullMetadata: Bool
    private let savedPathBlock: GetSavedPath

    private var _executing = false
    private var _finished = false

    public override var isAsynchronous: Bool { true }

    public override var isExecuting: Bool { _executing }

    public override var isFinished: Bool { _finished }

    public init(
        result: PHPickerResult,
        maxHeight: NSNumber?,
        maxWidth: NSNumber?,
        desiredImageQuality: NSNumber,
        fullMetadata: Bool,
        savedPathBlock: @escaping GetSavedPath
    ) {
        self.pickerResult = result
        self.maxHeight = maxHeight
        self.maxWidth = maxWidth
        self.desiredImageQuality = desiredImageQuality
        self.requestFullMetadata = fullMetadata
        self.savedPathBlock = savedPathBlock
        super.init()
    }

    private func setExecuting(_ value: Bool) {
        willChangeValue(forKey: "isExecuting")
        _executing = value
        didChangeValue(forKey: "isExecuting")
    }

    private func setFinished(_ value: Bool) {
        willChangeValue(forKey: "isFinished")
        _finished = value
        didChangeValue(forKey: "isFinished")
    }

    private func completeOperation(with path: String?, error: FlutterError?) {
        savedPathBlock(path, error)
        setExecuting(false)
        setFinished(true)
    }

    public override func start() {
        if isCancelled {
            setFinished(true)
            return
        }

        setExecuting(true)

        if pickerResult.itemProvider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
            pickerResult.itemProvider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { [weak self] data, error in
                guard let self = self else { return }
                if let data = data {
                    self.processImage(data)
                } else {
                    let flutterError = FlutterError(
                        code: "invalid_image",
                        message: error?.localizedDescription ?? "Unknown error",
                        details: (error as NSError?)?.domain
                    )
                    self.completeOperation(with: nil, error: flutterError)
                }
            }
        } else if pickerResult.itemProvider.hasItemConformingToTypeIdentifier(UTType.movie.identifier) {
            processVideo()
        } else {
            let flutterError = FlutterError(
                code: "invalid_source",
                message: "Invalid media source.",
                details: nil
            )
            completeOperation(with: nil, error: flutterError)
        }
    }

    private func processImage(_ pickerImageData: Data) {
        var localImage = UIImage(data: pickerImageData)

        if let image = localImage, (maxWidth != nil || maxHeight != nil) {
            localImage = ImagePickerImageUtil.scaledImage(
                image,
                maxWidth: maxWidth,
                maxHeight: maxHeight,
                isMetadataAvailable: true
            )
        }

        guard let image = localImage else {
            completeOperation(with: nil, error: FlutterError(code: "invalid_image", message: "Could not decode image", details: nil))
            return
        }

        let savedPath = ImagePickerPhotoAssetUtil.saveImage(
            withOriginalImageData: pickerImageData,
            image: image,
            maxWidth: maxWidth,
            maxHeight: maxHeight,
            imageQuality: desiredImageQuality
        )

        completeOperation(with: savedPath, error: nil)
    }

    private func processVideo() {
        guard let typeIdentifier = pickerResult.itemProvider.registeredTypeIdentifiers.first else {
            completeOperation(with: nil, error: FlutterError(code: "invalid_source", message: "No type identifier found", details: nil))
            return
        }

        pickerResult.itemProvider.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { [weak self] videoURL, error in
            guard let self = self else { return }

            if let error = error {
                let flutterError = FlutterError(
                    code: "invalid_image",
                    message: error.localizedDescription,
                    details: (error as NSError).domain
                )
                self.completeOperation(with: nil, error: flutterError)
                return
            }

            guard let videoURL = videoURL,
                  let destination = ImagePickerPhotoAssetUtil.saveVideo(from: videoURL) else {
                self.completeOperation(
                    with: nil,
                    error: FlutterError(
                        code: "flutter_image_picker_copy_video_error",
                        message: "Could not cache the video file.",
                        details: nil
                    )
                )
                return
            }

            self.completeOperation(with: destination.path, error: nil)
        }
    }
}
