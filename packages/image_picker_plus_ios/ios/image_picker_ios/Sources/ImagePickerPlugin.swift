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

typealias FlutterResultAdapter = ([PickedMedia]?, Error?) -> Void

// MARK: - ImagePickerMethodCallContext

public class ImagePickerMethodCallContext {
    let result: FlutterResultAdapter
    var maxSize: MaxSize?
    var imageQuality: Int64?
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
        let activeSceneRootViewController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first(where: { $0.activationState == .foregroundActive })?
            .windows
            .first(where: \.isKeyWindow)?
            .rootViewController
        let fallbackSceneRootViewController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
        let baseViewController =
            registrar?.viewController
            ?? activeSceneRootViewController
            ?? fallbackSceneRootViewController
            ?? UIApplication.shared.delegate?.window??.rootViewController

        return DefaultViewProvider.topViewController(from: baseViewController)
    }

    private static func topViewController(from viewController: UIViewController?) -> UIViewController? {
        if let navigationController = viewController as? UINavigationController {
            return topViewController(from: navigationController.visibleViewController)
        }
        if let tabBarController = viewController as? UITabBarController {
            return topViewController(from: tabBarController.selectedViewController)
        }
        if let presentedViewController = viewController?.presentedViewController {
            return topViewController(from: presentedViewController)
        }
        return viewController
    }
}

// MARK: - ImagePickerPlugin

public class ImagePickerPlugin: NSObject, FlutterPlugin, ImagePickerApi {

    private var imagePickerControllerOverrides: [UIImagePickerController]?
    private let viewProvider: ViewProvider
    private var callContext: ImagePickerMethodCallContext?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = ImagePickerPlugin(viewProvider: DefaultViewProvider(registrar: registrar))
        ImagePickerApiSetup.setUp(binaryMessenger: registrar.messenger(), api: instance)
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

    private func cameraDevice(for source: SourceSpecification) -> UIImagePickerController.CameraDevice {
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

    private func present(_ presentedViewController: UIViewController) -> Bool {
        guard let hostViewController = viewProvider.viewController else {
            return false
        }

        hostViewController.present(presentedViewController, animated: true)
        return true
    }

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

        _ = showPhotoLibrary(with: pickerViewController)
    }

    private func launchUIImagePicker(with source: SourceSpecification, context: ImagePickerMethodCallContext) {
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
            sendCallResult(error: PigeonError(code: "invalid_source", message: "Invalid image source.", details: nil))
        }
    }

    // MARK: - ImagePickerApi

    func pickImage(
        withSource source: SourceSpecification,
        maxSize: MaxSize,
        quality imageQuality: Int64?,
        fullMetadata: Bool,
        completion: @escaping (Result<PickedMedia?, Error>) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext { paths, error in
            if let paths = paths, paths.count > 1 {
                completion(.failure(PigeonError(code: "invalid_result", message: "Incorrect number of return paths provided", details: nil)))
                return
            }
            if let error = error {
                completion(.failure(error))
            } else {
                completion(.success(paths?.first))
            }
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

    func pickMultiImage(
        withMaxSize maxSize: MaxSize,
        quality imageQuality: Int64?,
        fullMetadata: Bool,
        limit: Int64?,
        completion: @escaping (Result<[PickedMedia], Error>) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext { paths, error in
            if let error = error {
                completion(.failure(error))
            } else {
                completion(.success(paths ?? []))
            }
        }
        context.includeImages = true
        context.maxSize = maxSize
        context.imageQuality = imageQuality
        context.requestFullMetadata = fullMetadata
        context.maxItemCount = limit.map(Int.init) ?? 0

        launchPHPicker(with: context)
    }

    func pickMedia(
        withMediaSelectionOptions options: MediaSelectionOptions,
        completion: @escaping (Result<[PickedMedia], Error>) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext { paths, error in
            if let error = error {
                completion(.failure(error))
            } else {
                completion(.success(paths ?? []))
            }
        }
        context.maxSize = options.maxSize
        context.imageQuality = options.imageQuality
        context.requestFullMetadata = options.requestFullMetadata
        context.includeImages = true
        context.includeVideo = true

        if !options.allowMultiple {
            context.maxItemCount = 1
        } else if let limit = options.limit {
            context.maxItemCount = Int(limit)
        }

        launchPHPicker(with: context)
    }

    func pickVideo(
        withSource source: SourceSpecification,
        maxDuration maxDurationSeconds: Int64?,
        completion: @escaping (Result<PickedMedia?, Error>) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext { paths, error in
            if let paths = paths, paths.count > 1 {
                completion(.failure(PigeonError(code: "invalid_result", message: "Incorrect number of return paths provided", details: nil)))
                return
            }
            if let error = error {
                completion(.failure(error))
            } else {
                completion(.success(paths?.first))
            }
        }
        context.includeVideo = true
        context.maxItemCount = 1
        context.maxDuration = maxDurationSeconds.map(Double.init) ?? 0

        if source.type == .gallery {
            launchPHPicker(with: context)
        } else {
            launchUIImagePicker(with: source, context: context)
        }
    }

    func pickMultiVideo(
        withMaxDuration maxDurationSeconds: Int64?,
        limit: Int64?,
        completion: @escaping (Result<[PickedMedia], Error>) -> Void
    ) {
        cancelInProgressCall()

        let context = ImagePickerMethodCallContext { paths, error in
            if let error = error {
                completion(.failure(error))
            } else {
                completion(.success(paths ?? []))
            }
        }
        context.includeVideo = true
        context.maxItemCount = limit.map(Int.init) ?? 0
        context.maxDuration = maxDurationSeconds.map(Double.init) ?? 0

        launchPHPicker(with: context)
    }

    // MARK: - Helper Methods

    private func cancelInProgressCall() {
        if callContext != nil {
            sendCallResult(error: PigeonError(code: "multiple_request", message: "Cancelled by a second request", details: nil))
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
            guard present(imagePickerController) else { return }
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
            _ = present(alert)
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
            sendCallResult(error: PigeonError(code: "camera_access_restricted", message: "The user is not allowed to use the camera.", details: nil))
        default:
            sendCallResult(error: PigeonError(code: "camera_access_denied", message: "The user did not allow camera access.", details: nil))
        }
    }

    private func errorNoPhotoAccess(_ status: PHAuthorizationStatus) {
        switch status {
        case .restricted:
            sendCallResult(error: PigeonError(code: "photo_access_restricted", message: "The user is not allowed to use the photo.", details: nil))
        default:
            sendCallResult(error: PigeonError(code: "photo_access_denied", message: "The user did not allow photo access.", details: nil))
        }
    }

    private func showPhotoLibrary(with pickerViewController: PHPickerViewController) -> Bool {
        return present(pickerViewController)
    }

    private func showPhotoLibrary(with imagePickerController: UIImagePickerController) -> Bool {
        imagePickerController.sourceType = .photoLibrary
        return present(imagePickerController)
    }

    private func getDesiredImageQuality(_ imageQuality: Int64?) -> NSNumber? {
        guard let quality = imageQuality else { return nil }
        let intValue = Int(quality)
        if intValue < 0 || intValue > 100 {
            return nil
        }
        return NSNumber(value: Float(intValue) / 100.0)
    }

    // MARK: - Result Handling

    private func sendCallResult(pathList: [PickedMedia]?) {
        guard let context = callContext else { return }

        if let list = pathList, list.contains(where: { $0.path.isEmpty }) {
            context.result(nil, PigeonError(code: "create_error", message: "pathList's items should not be null", details: nil))
        } else {
            context.result(pathList ?? [], nil)
        }
        callContext = nil
    }

    private func sendCallResult(error: Error) {
        guard let context = callContext else { return }
        context.result(nil, error)
        callContext = nil
    }

    // MARK: - Image Saving

    private func saveImage(
        with originalImageData: NSData?,
        image: UIImage,
        maxWidth: Double?,
        maxHeight: Double?,
        imageQuality: NSNumber?,
        localIdentifier: String?
    ) {
        let savedPath = ImagePickerPhotoAssetUtil.saveImage(
            withOriginalImageData: originalImageData as Data?,
            image: image,
            maxWidth: maxWidth.map(NSNumber.init(value:)),
            maxHeight: maxHeight.map(NSNumber.init(value:)),
            imageQuality: imageQuality
        )
        sendCallResult(
            pathList: savedPath != nil
                ? [PickedMedia(path: savedPath!, localIdentifier: localIdentifier)]
                : nil
        )
    }

    private func saveImage(
        with pickerInfo: [String: Any]?,
        image: UIImage,
        imageQuality: NSNumber?,
        localIdentifier: String?
    ) {
        let savedPath = ImagePickerPhotoAssetUtil.saveImage(with: pickerInfo, image: image, imageQuality: imageQuality)
        sendCallResult(
            pathList: savedPath != nil
                ? [PickedMedia(path: savedPath!, localIdentifier: localIdentifier)]
                : nil
        )
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
        saveQueue.maxConcurrentOperationCount = 10

        guard let currentCallContext = callContext else { return }

        let maxWidth = currentCallContext.maxSize?.width
        let maxHeight = currentCallContext.maxSize?.height
        let imageQuality = currentCallContext.imageQuality
        let desiredImageQuality = getDesiredImageQuality(imageQuality)
        let requestFullMetadata = currentCallContext.requestFullMetadata

        var pathList: [PickedMedia?] = Array(repeating: nil, count: results.count)
        var saveError: Error?

        let sendListOperation = BlockOperation { [weak self] in
            if let error = saveError {
                self?.sendCallResult(error: error)
            } else {
                let validResults = pathList.compactMap { $0 }
                if validResults.count == pathList.count {
                    self?.sendCallResult(pathList: validResults)
                } else {
                    self?.sendCallResult(error: PigeonError(code: "create_error", message: "Failed to save some images", details: nil))
                }
            }
        }

        for (index, result) in results.enumerated() {
            let saveOperation = PHPickerSaveImageToPathOperation(
                result: result,
                maxHeight: maxHeight.map(NSNumber.init(value:)),
                maxWidth: maxWidth.map(NSNumber.init(value:)),
                desiredImageQuality: desiredImageQuality,
                fullMetadata: requestFullMetadata
            ) { savedPath, error in
                if let path = savedPath {
                    let resolvedLocalIdentifier = resolveLocalIdentifier(
                        from: result.assetIdentifier
                    )
                    pathList[index] = PickedMedia(
                        path: path,
                        localIdentifier: resolvedLocalIdentifier
                    )
                } else if let error = error {
                    saveError = PigeonError(
                        code: error.code,
                        message: error.message,
                        details: error.details
                    )
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
        let localIdentifier = (info[.phAsset] as? PHAsset)?.localIdentifier
        picker.dismiss(animated: true)

        guard callContext != nil else { return }

        if let videoURL = videoURL {
            if let destination = ImagePickerPhotoAssetUtil.saveVideo(from: videoURL) {
                sendCallResult(
                    pathList: [PickedMedia(path: destination.path, localIdentifier: localIdentifier)]
                )
            } else {
                sendCallResult(error: PigeonError(code: "flutter_image_picker_copy_video_error", message: "Could not cache the video file.", details: nil))
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
                scaledImage = ImagePickerImageUtil.scaledImage(
                    finalImage,
                    maxWidth: maxWidth.map(NSNumber.init(value:)),
                    maxHeight: maxHeight.map(NSNumber.init(value:)),
                    isMetadataAvailable: true
                )
            }

            var originalAsset: PHAsset?
            if callContext?.requestFullMetadata == true {
                originalAsset = info[.phAsset] as? PHAsset
            }

            if originalAsset == nil {
                let infoDict = info.reduce(into: [String: Any]()) { result, pair in
                    result[pair.key.rawValue] = pair.value
                }
                saveImage(
                    with: infoDict,
                    image: scaledImage,
                    imageQuality: desiredImageQuality,
                    localIdentifier: localIdentifier
                )
            } else {
                let options = PHImageRequestOptions()
                PHImageManager.default().requestImageDataAndOrientation(for: originalAsset!, options: options) { [weak self] imageData, _, _, _ in
                    self?.saveImage(
                        with: imageData as NSData?,
                        image: scaledImage,
                        maxWidth: maxWidth,
                        maxHeight: maxHeight,
                        imageQuality: desiredImageQuality,
                        localIdentifier: originalAsset?.localIdentifier
                    )
                }
            }
        }
    }

    public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        sendCallResult(pathList: nil)
    }
}

private func resolveLocalIdentifier(from assetIdentifier: String?) -> String? {
    return assetIdentifier
}
