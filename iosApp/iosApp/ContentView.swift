import SwiftUI
import Shared

/// Thin SwiftUI wrapper that hosts the shared Compose Multiplatform UI
/// (`SharedApp()` in the KMP `shared` module, exposed via `MainViewController()`).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Respect the top safe area (status bar / notch) so game UI (e.g. the
            // player score panels in BoardScreen) doesn't render underneath the
            // status bar clock, while still going edge-to-edge at the bottom.
            .ignoresSafeArea(edges: .bottom)
    }
}
