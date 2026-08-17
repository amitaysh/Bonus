import Foundation

@objc final class UITestLaunch: NSObject {
    @objc static func configure() {
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: "-uiTestScreen"), index + 1 < args.count else { return }
        UserDefaults.standard.set(args[index + 1], forKey: "uiTestScreen")
    }
}
