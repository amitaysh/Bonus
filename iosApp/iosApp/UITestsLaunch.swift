import Foundation

@objc final class UITestLaunch: NSObject {
    @objc static func configure() {
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: "-uiTestScreen"), index + 1 < args.count else {
            // No override passed on this launch: clear any stale value left behind by a
            // previous test launch, so normal app starts always begin at the main menu.
            UserDefaults.standard.removeObject(forKey: "uiTestScreen")
            return
        }
        UserDefaults.standard.set(args[index + 1], forKey: "uiTestScreen")
    }
}
