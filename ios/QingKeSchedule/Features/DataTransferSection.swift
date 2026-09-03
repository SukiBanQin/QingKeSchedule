import SwiftUI
import UniformTypeIdentifiers

struct DataTransferSection: View {
    @Bindable var state: ScheduleAppState

    @State private var importerPresented = false
    @State private var pendingImport: ScheduleImportPreview?
    @State private var confirmationPresented = false
    @State private var importError: String?
    @State private var statusMessage: String?

    var body: some View {
        Section {
            Button {
                importerPresented = true
            } label: {
                Label("从文件导入课表", systemImage: "square.and.arrow.down")
            }
            .accessibilityIdentifier("schedule-import")

            if let exportDocument = try? state.exportDocument() {
                ShareLink(
                    item: exportDocument,
                    preview: SharePreview(
                        exportDocument.fileName,
                        image: Image(systemName: "doc.text")
                    )
                ) {
                    Label("分享课表备份", systemImage: "square.and.arrow.up")
                }
                .accessibilityIdentifier("schedule-export")
            } else {
                Label("设置学期后可导出备份", systemImage: "info.circle")
                    .foregroundStyle(.secondary)
            }

            if let statusMessage {
                Label(statusMessage, systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                    .accessibilityIdentifier("schedule-import-success")
            }

            uiTestingImportButton
        } header: {
            Text("数据备份与迁移")
        } footer: {
            Text("导入会先校验并要求确认；确认后将替换当前课表。卸载 App 可能清除本地数据，请定期导出备份。")
        }
        .fileImporter(
            isPresented: $importerPresented,
            allowedContentTypes: [.json]
        ) { result in
            switch result {
            case .success(let url):
                importFile(at: url)
            case .failure(let error):
                if (error as? CocoaError)?.code != .userCancelled {
                    importError = error.localizedDescription
                }
            }
        }
        .alert(
            "替换当前课表？",
            isPresented: $confirmationPresented
        ) {
            Button("替换当前课表", role: .destructive) {
                confirmImport()
            }
            Button("取消", role: .cancel) {
                pendingImport = nil
            }
        } message: {
            Text(pendingImport?.summary ?? "")
        }
        .alert(
            "无法导入课表",
            isPresented: Binding(
                get: { importError != nil },
                set: { isPresented in
                    if !isPresented { importError = nil }
                }
            )
        ) {
            Button("好") { importError = nil }
        } message: {
            Text(importError ?? "未知错误")
        }
    }

    @ViewBuilder
    private var uiTestingImportButton: some View {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-testing-transfer-controls") {
            Button("载入测试导入文件") {
                guard
                    let raw = ProcessInfo.processInfo.environment["UI_TEST_IMPORT_JSON"],
                    let contents = raw.data(using: .utf8)
                else {
                    importError = "测试导入内容缺失"
                    return
                }
                prepareImport(contents)
            }
            .accessibilityIdentifier("schedule-import-test-file")
        }
        #endif
    }

    private func importFile(at url: URL) {
        let hasSecurityScope = url.startAccessingSecurityScopedResource()
        defer {
            if hasSecurityScope { url.stopAccessingSecurityScopedResource() }
        }

        do {
            let fileSize = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize
            if let fileSize, fileSize > ScheduleDataTransfer.maximumImportBytes {
                throw ScheduleDataTransferError.fileTooLarge(
                    maximumBytes: ScheduleDataTransfer.maximumImportBytes
                )
            }
            prepareImport(try Data(contentsOf: url, options: .mappedIfSafe))
        } catch {
            importError = error.localizedDescription
        }
    }

    private func prepareImport(_ contents: Data) {
        do {
            pendingImport = try state.previewImport(contents: contents)
            statusMessage = nil
            confirmationPresented = true
        } catch {
            importError = error.localizedDescription
        }
    }

    private func confirmImport() {
        guard let pendingImport else { return }
        if state.confirmImport(pendingImport) {
            statusMessage = "已导入 \(pendingImport.courseCount) 门课程"
        }
        self.pendingImport = nil
    }
}
