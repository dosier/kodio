import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Edge to edge: Compose draws behind the status bar and home indicator and
        // applies the safe-area insets itself (statusBarsPadding, bottom bar insets).
        // Keyboard insets are handled by Compose too.
        ComposeView()
            .ignoresSafeArea()
    }
}



