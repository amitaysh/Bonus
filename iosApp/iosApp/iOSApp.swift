import SwiftUI

@main
struct iOSApp: App {
    init() {
        UITestLaunch.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
