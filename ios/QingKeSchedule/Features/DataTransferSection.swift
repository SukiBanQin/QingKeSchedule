import SwiftUI
import UniformTypeIdentifiers

struct DataTransferSection: View {
    @Bindable var state: ScheduleAppState

    @State private var importerPresented = false
    var body: some View {
        TerminalFormSection(
            index: "05",
            title: "数据备份",
            detail: "TRANSFER",
            footer: "JSON 导入会先校验并要求确认；确认后将替换当前课表。卸载 App 可能清除本地数据，请定期导出备份。"
        ) {
            VStack(alignment: .leading, spacing: 6) {
                Label("仅支持青课 JSON 备份文件", systemImage: "doc.badge.gearshape")
                    .font(.headline)
                Text("请选择扩展名为 .json 的青课课表备份；暂不支持 Excel（.xlsx / .xls）文件。")
                    .font(.caption)
                    .foregroundStyle(QingKeTheme.textSecondary)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("schedule-import-format")
            .padding(.vertical, 12)

            TerminalFormDivider()

            Button {
                importerPresented = true
            } label: {
                Label("从 JSON 文件导入课表", systemImage: "square.and.arrow.down")
                    .terminalControl()
            }
            .accessibilityIdentifier("schedule-import")

            if let exportDocument = try? state.exportDocument() {
                TerminalFormDivider()
                ShareLink(
                    item: exportDocument,
                    preview: SharePreview(
                        exportDocument.fileName,
                        image: Image(systemName: "doc.text")
                    )
                ) {
                    Label("分享课表备份", systemImage: "square.and.arrow.up")
                }
                .terminalControl()
                .accessibilityIdentifier("schedule-export")
            } else {
                TerminalFormDivider()
                Label("设置学期后可导出备份", systemImage: "info.circle")
                    .foregroundStyle(QingKeTheme.textSecondary)
                    .terminalControl()
            }

            if let statusMessage = state.importStatusMessage {
                TerminalFormDivider()
                Label(statusMessage, systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                    .terminalControl()
                    .accessibilityIdentifier("schedule-import-success")
            }

            uiTestingImportButton
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
                    state.presentImportFailure(error.localizedDescription)
                }
            }
        }
    }

    @ViewBuilder
    private var uiTestingImportButton: some View {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-testing-transfer-controls") {
            Button {
                guard
                    let raw = ProcessInfo.processInfo.environment["UI_TEST_IMPORT_JSON"],
                    let contents = raw.data(using: .utf8)
                else {
                    state.presentImportFailure("测试导入内容缺失")
                    return
                }
                prepareImport(contents)
            } label: {
                Text("载入测试导入文件")
                    .terminalControl()
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
            state.presentImportFailure(error.localizedDescription)
        }
    }

    private func prepareImport(_ contents: Data) {
        state.prepareImport(contents: contents)
    }
}

struct ScheduleImportPromptView: View {
    @Bindable var state: ScheduleAppState

    var body: some View {
        ZStack {
            QingKeTheme.scrim
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 18) {
                TerminalBrandHeader(
                    code: state.importFailure == nil ? "IMPORT / VERIFY" : "IMPORT / ERROR"
                )
                TerminalStatusTag(
                    text: state.importFailure == nil ? "REPLACE DATA" : "INVALID FILE",
                    tint: state.importFailure == nil ? QingKeTheme.signal : QingKeTheme.danger,
                    contentColor: QingKeTheme.textOnAccent
                )
                Text(state.importFailure == nil ? "替换当前课表？" : "无法导入课表")
                    .font(.system(size: 32, weight: .black))
                Text(state.importFailure ?? state.pendingImportPreview?.summary ?? "")
                    .foregroundStyle(QingKeTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                if state.importFailure == nil {
                    Button {
                        state.confirmPreparedImport()
                    } label: {
                        Text("替换当前课表")
                            .font(.headline)
                            .foregroundStyle(QingKeTheme.textOnAccent)
                            .frame(maxWidth: .infinity, minHeight: 56)
                            .background(QingKeTheme.signal)
                    }
                    .buttonStyle(.plain)

                    Button {
                        state.dismissImportPrompt()
                    } label: {
                        Text("取消")
                            .font(.headline)
                            .frame(maxWidth: .infinity, minHeight: 52)
                            .overlay { Rectangle().stroke(QingKeTheme.border, lineWidth: 1) }
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                } else {
                    Button {
                        state.dismissImportPrompt()
                    } label: {
                        Text("好")
                            .font(.headline)
                            .foregroundStyle(QingKeTheme.textOnInverse)
                            .frame(maxWidth: .infinity, minHeight: 56)
                            .background(QingKeTheme.inverseSurface)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
            .background { TerminalAcrylicSurface(level: .elevated) }
            .overlay { Rectangle().stroke(QingKeTheme.panelEdge, lineWidth: 1) }
            .padding(20)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("schedule-import-prompt")
        }
    }
}
