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
            // In landscape (the app is landscape-locked), the device's top/bottom
            // safe-area edges correspond to the iPad's left/right physical edges,
            // where the front camera notch/bezel sits - respect those (leading/
            // trailing) so buttons aren't hidden behind it, while still going
            // edge-to-edge on the actual top/bottom of the screen.
            .ignoresSafeArea(edges: [.top, .bottom])
    }
}
