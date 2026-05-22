import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate {

  /// Retained by the AppDelegate so the method channel stays alive for the
  /// lifetime of the host app.
  private let keyboardSettingsBridge = KeyboardSettingsBridge()

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)

    if let controller = window?.rootViewController as? FlutterViewController {
      keyboardSettingsBridge.register(with: controller.binaryMessenger)
    }

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
