import SwiftUI
import WeaverseShared

/// Walking-skeleton screen: proves the Kotlin/Native shared framework links
/// and runs from Swift before any real app UI moves over. See
/// docs/features/ios-port-plan.md for what comes after this.
struct ContentView: View {
    private let platformName = Platform().name

    var body: some View {
        VStack(spacing: 16) {
            Text("Weaverse")
                .font(.largeTitle)
                .bold()
            Text("iOS shell — shared Kotlin module linked")
                .font(.subheadline)
                .foregroundColor(.secondary)
            Text("Running on \(platformName)")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
