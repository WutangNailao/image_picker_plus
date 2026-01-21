// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import AVFoundation
import Flutter
import MobileCoreServices
import Photos
import PhotosUI
import UIKit

// MARK: - FlutterResultAdapter

public typealias FlutterResultAdapter = ([String]?, FlutterError?) -> Void

// MARK: - ImagePickerMethodCallContext

public class ImagePickerMethodCallContext {
    let result: FlutterResultAdapter
    var maxSize: FLTMaxSize?
    var imageQuality: NSNumber?
    var maxItemCount: Int = 0
    var maxDuration: Double = 0
    var requestFullMetadata: Bool = false
    var includeImages: Bool = false
    var includeVideo: Bool = false

    init(result: @escaping FlutterResultAdapter) {
        self.result = result
    }
}

// MARK: - ViewProvider Protocol

public protocol ViewProvider {
    var viewController: UIViewController? { get }
}

// MARK: - DefaultViewProvider

public class DefaultViewProvider: NSObject, ViewProvider {
    private weak var registrar: FlutterPluginRegistrar?

    public init(registrar: FlutterPluginRegistrar) {
        self.registrar = registrar
    }

    public var viewController: UIViewController? {
        return registrar?.messenger() as? UIViewController
            ?? UIApplication.shared.delegate?.window??.rootViewController
    }
}

// MARK: - ImagePickerPlugin

@objc(FLTImagePickerPlugin)
public class ImagePickerPlugin: NSObject, FlutterPlugin, FLTImagePickerApi {

    private var imagePickerControllerOverrides: [UIImagePickerController]?
    private let viewProvider: ViewProvider
    private var callContext: ImagePickerMethodCallContext?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = ImagePickerPlugin(viewProvider: DefaultViewProvider(registrar: registrar))
        SetUpFLTImagePickerApi(registrar.messenger(), instance)
    }

    init(viewProvider: ViewProvider) {
        self.viewProvider = viewProvider
        super.init()
    }

    func setImagePickerControllerOverrides(_ controllers: [UIImagePickerController]) {
        imagePickerControllerOverrides = controllers
    }

    private func createImagePickerController() -> UIImagePickerController {
        if let overrides = imagePickerControllerOverrides, !overrides.isEmpty {
            let controller = overrides[0]
            imagePickerControllerOverrides?.removeFirst()
            return controller
        }
        return UIImagePickerController()
    }

    private func cameraDevice(for source: FLTSourceSpecification) -> UIImagePickerController.CameraDevice {
        switch source.camera {
        case .front:
            return .front
        case .rear:
            return .rear
        @unknown default:
            return .rear
        }
    }

    // MARK: - Launcher Methods

    private func launchPHPicker(with context: ImagePickerMethodCallContext) {
        let config = PHPickerConfiguration(photoLibrary: .shared())
        var updatedConfig = config
        updatedConfig.selectionLimit = context.maxItemCount
        updatedConfig.preferredAssetRepresentationMode = .current

        var filters: [PHPickerFilter] = []
        if context.includeImages {
            filters.append(.images)
        }
        if context.includeVideo {
            filters.append(.videos)
        }
        updatedConfig.filter = .any(of: filters)

        let pickerViewController = PHPickerViewController(configuration: updatedConfig)
        pickerViewController.delegate = self
        pickerViewController.presentationController?.delegate = self
        self.callContext = context

        showPhotoLibrary(with: pickerViewController)
    }

    private func launchUIImagePicker(with source: FLTSourceSpecification, context: ImagePickerMethodCallContext) {
        let imagePickerController = createImagePickerController()
        imagePickerController.modalPresentationStyle = .currentContext
        imagePickerController.delegate = self

        var mediaTypes: [String] = []
        if context.includeImages {
            mediaTypes.append(kUTTypeImage as String)
        }
        if context.includeVideo {
            mediaTypes.append(kUTTypeMovie as String)
            imagePickerController.videoQuality = .typeHigh
        }
        imagePickerController.mediaTypes = mediaTypes

        if context.maxDuration > 0 {
            imagePickerController.videoMaximumDuration = context.maxDuration
        }

        self.callContext = context

        switch source.type {
        case .camera:
            checkCameraAuthorization(with: imagePickerController, camera: cameraDevice(for: source))
        case .gallery:
            if context.requestFullMetadata {
                checkPhotoAuthorization(with: imagePickerController)
            } else {
                showPhotoLibrary(with: imagePickerController)
            }
        @unknown default:
            sendCallResult(error: FlutterError(code: "invalid_source", message: "Invalid image source.", details: nil))
        }
    }

    // MARK: - FLTImagePickerApi

    public func pickImage(
        withSource source: FLTSourceSpecification,
        maxSize: FLTMaxSize,
        quality imageQuality: NSNumber?,
        fullMetadata: Bool,
        completion: @escaping (String?, FlutterError?) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext { paths, error in
            if let paths = paths, paths.count > 1 {
                completion(nil, FlutterError(code: "invalid_result", message: "Incorrect number of return paths provided", details: nil))
                return
            }
            completion(paths?.first, error)
        }
        context.includeImages = true
        context.maxSize = maxSize
        context.imageQuality = imageQuality
        context.maxItemCount = 1
        context.requestFullMetadata = fullMetadata

        if source.type == .gallery {
            launchPHPicker(with: context)
        } else {
            launchUIImagePicker(with: source, context: context)
        }
    }

    public func pickMultiImage(
        withMaxSize maxSize: FLTMaxSize,
        quality imageQuality: NSNumber?,
        fullMetadata: Bool,
        limit: NSNumber?,
        completion: @escaping ([String]?, FlutterError?) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext(result: completion)
        context.includeImages = true
        context.maxSize = maxSize
        context.imageQuality = imageQuality
        context.requestFullMetadata = fullMetadata
        context.maxItemCount = limit?.intValue ?? 0

        launchPHPicker(with: context)
    }

    public func pickMedia(
        withMediaSelectionOptions options: FLTMediaSelectionOptions,
        completion: @escaping ([String]?, FlutterError?) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext(result: completion)
        context.maxSize = options.maxSize
        context.imageQuality = options.imageQuality
        context.requestFullMetadata = options.requestFullMetadata
        context.includeImages = true
        context.includeVideo = true

        if !options.allowMultiple {
            context.maxItemCount = 1
        } else if let limit = options.limit {
            context.maxItemCount = limit.intValue
        }

        launchPHPicker(with: context)
    }

    public func pickVideo(
        withSource source: FLTSourceSpecification,
        maxDuration maxDurationSeconds: NSNumber?,
        completion: @escaping (String?, FlutterError?) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext { paths, error in
            if let paths = paths, paths.count > 1 {
                completion(nil, FlutterError(code: "invalid_result", message: "Incorrect number of return paths provided", details: nil))
                return
            }
            completion(paths?.first, error)
        }
        context.includeVideo = true
        context.maxItemCount = 1
        context.maxDuration = maxDurationSeconds?.doubleValue ?? 0

        if source.type == .gallery {
            launchPHPicker(with: context)
        } else {
            launchUIImagePicker(with: source, context: context)
        }
    }

    public func pickMultiVideo(
        withMaxDuration maxDurationSeconds: NSNumber?,
        limit: NSNumber?,
        completion: @escaping ([String]?, FlutterError?) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext(result: completion)
        context.includeVideo = true
        context.maxItemCount = limit?.intValue ?? 0
        context.maxDuration = maxDurationSeconds?.doubleValue ?? 0

        launchPHPicker(with: context)
    }

    // MARK: - Helper Methods

    private func cancelInProgressCall() {
        if callContext != nil {
            sendCallResult(error: FlutterError(code: "multiple_request", message: "Cancelled by a second request", details: nil))
            callContext = nil
        }
    }

    private func showCamera(_ device: UIImagePickerController.CameraDevice, with imagePickerController: UIImagePickerController) {
        objc_sync_enter(self)
        defer { objc_sync_exit(self) }

        if imagePickerController.isBeingPresented {
            return
        }

        if UIImagePickerController.isSourceTypeAvailable(.camera) &&
            UIImagePickerController.isCameraDeviceAvailable(device) {
            imagePickerController.sourceType = .camera
            imagePickerController.cameraDevice = device
            viewProvider.viewController?.present(imagePickerController, animated: true)
        } else {
            let alert = UIAlertController(
                title: NSLocalizedString("Error", comment: "Alert title when camera unavailable"),
                message: NSLocalizedString("Camera not available.", comment: "Alert message when camera unavailable"),
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(
                title: NSLocalizedString("OK", comment: "Alert button when camera unavailable"),
                style: .default
            ))
            viewProvider.viewController?.present(alert, animated: true)
            sendCallResult(pathList: nil)
        }
    }

    private func checkCameraAuthorization(with imagePickerController: UIImagePickerController, camera device: UIImagePickerController.CameraDevice) {
        let status = AVCaptureDevice.authorizationStatus(for: .video)

        switch status {
        case .authorized:
            showCamera(device, with: imagePickerController)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted {
                        self?.showCamera(device, with: imagePickerController)
                    } else {
                        self?.errorNoCameraAccess(.denied)
                    }
                }
            }
        case .denied, .restricted:
            errorNoCameraAccess(status)
        @unknown default:
            errorNoCameraAccess(status)
        }
    }

    private func checkPhotoAuthorization(with imagePickerController: UIImagePickerController) {
        let status = PHPhotoLibrary.authorizationStatus()

        switch status {
        case .notDetermined:
            PHPhotoLibrary.requestAuthorization { [weak self] newStatus in
                DispatchQueue.main.async {
                    if newStatus == .authorized {
                        self?.showPhotoLibrary(with: imagePickerController)
                    } else {
                        self?.errorNoPhotoAccess(newStatus)
                    }
                }
            }
        case .authorized, .limited:
            showPhotoLibrary(with: imagePickerController)
        case .denied, .restricted:
            errorNoPhotoAccess(status)
        @unknown default:
            errorNoPhotoAccess(status)
        }
    }

    private func errorNoCameraAccess(_ status: AVAuthorizationStatus) {
        switch status {
        case .restricted:
            sendCallResult(error: FlutterError(code: "camera_access_restricted", message: "The user is not allowed to use the camera.", details: nil))
        default:
            sendCallResult(error: FlutterError(code: "camera_access_denied", message: "The user did not allow camera access.", details: nil))
        }
    }

    private func errorNoPhotoAccess(_ status: PHAuthorizationStatus) {
        switch status {
        case .restricted:
            sendCallResult(error: FlutterError(code: "photo_access_restricted", message: "The user is not allowed to use the photo.", details: nil))
        default:
            sendCallResult(error: FlutterError(code: "photo_access_denied", message: "The user did not allow photo access.", details: nil))
        }
    }

    private func showPhotoLibrary(with pickerViewController: PHPickerViewController) {
        viewProvider.viewController?.present(pickerViewController, animated: true)
    }

    private func showPhotoLibrary(with imagePickerController: UIImagePickerController) {
        imagePickerController.sourceType = .photoLibrary
        viewProvider.viewController?.present(imagePickerController, animated: true)
    }

    private func getDesiredImageQuality(_ imageQuality: NSNumber?) -> NSNumber {
        guard let quality = imageQuality else { return 1 }
        let intValue = quality.intValue
        if intValue < 0 || intValue > 100 {
            return 1
        }
        return NSNumber(value: Float(intValue) / 100.0)
    }

    // MARK: - Result Handling

    private func sendCallResult(pathList: [String]?) {
        guard let context = callContext else { return }

        if let list = pathList, list.contains(where: { $0.isEmpty }) {
            context.result(nil, FlutterError(code: "create_error", message: "pathList's items should not be null", details: nil))
        } else {
            context.result(pathList ?? [], nil)
        }
        callContext = nil
    }

    private func sendCallResult(error: FlutterError) {
        guard let context = callContext else { return }
        context.result(nil, error)
        callContext = nil
    }

    // MARK: - Image Saving

    private func saveImage(with originalImageData: NSData?, image: UIImage, maxWidth: NSNumber?, maxHeight: NSNumber?, imageQuality: NSNumber) {
        let savedPath = ImagePickerPhotoAssetUtil.saveImage(
            withOriginalImageData: originalImageData as Data?,
            image: image,
            maxWidth: maxWidth,
            maxHeight: maxHeight,
            imageQuality: imageQuality
        )
        sendCallResult(pathList: savedPath != nil ? [savedPath!] : nil)
    }

    private func saveImage(with pickerInfo: [String: Any]?, image: UIImage, imageQuality: NSNumber) {
        let savedPath = ImagePickerPhotoAssetUtil.saveImage(with: pickerInfo, image: image, imageQuality: imageQuality)
        sendCallResult(pathList: savedPath != nil ? [savedPath!] : nil)
    }
}

// MARK: - UIAdaptivePresentationControllerDelegate

extension ImagePickerPlugin: UIAdaptivePresentationControllerDelegate {
    public func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        sendCallResult(pathList: nil)
    }
}

// MARK: - PHPickerViewControllerDelegate

extension ImagePickerPlugin: PHPickerViewControllerDelegate {
    public func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)

        if results.isEmpty {
            sendCallResult(pathList: nil)
            return
        }

        let saveQueue = OperationQueue()
        saveQueue.name = "Flutter Save Image Queue"
        saveQueue.qualityOfService = .userInitiated

        guard let currentCallContext = callContext else { return }

        let maxWidth = currentCallContext.maxSize?.width
        let maxHeight = currentCallContext.maxSize?.height
        let imageQuality = currentCallContext.imageQuality
        let desiredImageQuality = getDesiredImageQuality(imageQuality)
        let requestFullMetadata = currentCallContext.requestFullMetadata

        var pathList: [String?] = Array(repeating: nil, count: results.count)
        var saveError: FlutterError?

        let sendListOperation = BlockOperation { [weak self] in
            if let error = saveError {
                self?.sendCallResult(error: error)
            } else {
                let validPaths = pathList.compactMap { $0 }
                if validPaths.count == pathList.count {
                    self?.sendCallResult(pathList: validPaths)
                } else {
                    self?.sendCallResult(error: FlutterError(code: "create_error", message: "Failed to save some images", details: nil))
                }
            }
        }

        for (index, result) in results.enumerated() {
            let saveOperation = PHPickerSaveImageToPathOperation(
                result: result,
                maxHeight: maxHeight,
                maxWidth: maxWidth,
                desiredImageQuality: desiredImageQuality,
                fullMetadata: requestFullMetadata
            ) { savedPath, error in
                if let path = savedPath {
                    pathList[index] = path
                } else {
                    saveError = error
                }
            }
            sendListOperation.addDependency(saveOperation)
            saveQueue.addOperation(saveOperation)
        }

        OperationQueue.main.addOperation(sendListOperation)
    }
}

// MARK: - UIImagePickerControllerDelegate

extension ImagePickerPlugin: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    public func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        let videoURL = info[.mediaURL] as? URL
        picker.dismiss(animated: true)

        guard callContext != nil else { return }

        if let videoURL = videoURL {
            if let destination = ImagePickerPhotoAssetUtil.saveVideo(from: videoURL) {
                sendCallResult(pathList: [destination.path])
            } else {
                sendCallResult(error: FlutterError(code: "flutter_image_picker_copy_video_error", message: "Could not cache the video file.", details: nil))
            }
        } else {
            var image = info[.editedImage] as? UIImage
            if image == nil {
                image = info[.originalImage] as? UIImage
            }

            guard let finalImage = image else {
                sendCallResult(pathList: nil)
                return
            }

            let maxWidth = callContext?.maxSize?.width
            let maxHeight = callContext?.maxSize?.height
            let imageQuality = callContext?.imageQuality
            let desiredImageQuality = getDesiredImageQuality(imageQuality)

            var scaledImage = finalImage
            if maxWidth != nil || maxHeight != nil {
                scaledImage = ImagePickerImageUtil.scaledImage(finalImage, maxWidth: maxWidth, maxHeight: maxHeight, isMetadataAvailable: true)
            }

            var originalAsset: PHAsset?
            if callContext?.requestFullMetadata == true {
                originalAsset = info[.phAsset] as? PHAsset
            }

            if originalAsset == nil {
                let infoDict = info.reduce(into: [String: Any]()) { result, pair in
                    result[pair.key.rawValue] = pair.value
                }
                saveImage(with: infoDict, image: scaledImage, imageQuality: desiredImageQuality)
            } else {
                let options = PHImageRequestOptions()
                PHImageManager.default().requestImageDataAndOrientation(for: originalAsset!, options: options) { [weak self] imageData, _, _, _ in
                    self?.saveImage(with: imageData as NSData?, image: scaledImage, maxWidth: maxWidth, maxHeight: maxHeight, imageQuality: desiredImageQuality)
                }
            }
        }
    }

    public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        sendCallResult(pathList: nil)
    }
}
