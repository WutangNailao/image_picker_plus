#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html
#
Pod::Spec.new do |s|
  s.name             = 'image_picker_plus_ios'
  s.version          = '0.0.6'
  s.summary          = 'Flutter plugin that shows an image picker.'
  s.description      = <<-DESC
A Flutter plugin for picking images from the image library, and taking new pictures with the camera.
Downloaded by pub (not CocoaPods).
                       DESC
  s.homepage         = 'https://github.com/WutangNailao/image_picker_plus'
  s.license          = { :type => 'BSD', :file => '../LICENSE' }
  s.author           = { 'Flutter Dev Team' => 'flutter-dev@googlegroups.com' }
  s.source           = { :http => 'https://github.com/WutangNailao/image_picker_plus' }
  s.documentation_url = 'https://pub.dev/packages/image_picker_plus_ios'
  s.source_files = 'image_picker_ios/Sources/image_picker_ios/**/*.swift'
  s.dependency 'Flutter'
  s.platform = :ios, '14.0'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
  s.swift_version = '5.0'
  s.resource_bundles = {'image_picker_plus_ios_privacy' => ['image_picker_ios/Sources/image_picker_ios/Resources/PrivacyInfo.xcprivacy']}
end
