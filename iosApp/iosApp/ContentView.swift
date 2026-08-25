import SwiftUI

/// Hosts the shared Compose Multiplatform UI (see RootScreen.kt in :shared).
/// This is where ported screens become visible on iOS as the port proceeds —
/// see docs/features/ios-port-plan.md for status.
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose has its own keyboard handler
    }
}

#Preview {
    ContentView()
}
