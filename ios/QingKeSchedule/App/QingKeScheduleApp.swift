import SwiftUI

@main
struct QingKeScheduleApp: App {
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 12) {
                Image(systemName: "calendar")
                    .font(.system(size: 44))
                    .foregroundStyle(.tint)
                    .accessibilityHidden(true)

                Text("青课")
                    .font(.title.bold())
                    .accessibilityIdentifier("app-title")

                Text("iOS 工程已就绪")
                    .foregroundStyle(.secondary)
            }
            .padding()
        }
    }
}
