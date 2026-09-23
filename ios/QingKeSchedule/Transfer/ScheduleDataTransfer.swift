import CoreTransferable
import Foundation
import UniformTypeIdentifiers

struct ScheduleImportPreview: Equatable, Sendable {
    let data: ScheduleDataDTO

    var semesterName: String {
        data.semester?.name ?? "未设置学期"
    }

    var courseCount: Int {
        data.courses.count
    }

    var summary: String {
        "学期：\(semesterName)\n课程：\(courseCount) 门\n更新时间：\(data.updatedAt)"
    }
}

struct ScheduleExportDocument: Transferable, Equatable, Sendable {
    let fileName: String
    let contents: Data

    static var transferRepresentation: some TransferRepresentation {
        DataRepresentation(exportedContentType: .json) { document in
            document.contents
        }
        .suggestedFileName { document in
            document.fileName
        }
    }
}

enum ScheduleDataTransferError: Error, Equatable, LocalizedError {
    case fileTooLarge(maximumBytes: Int)
    case malformedFile
    case unsupportedSchemaVersion(Int?)
    case invalidData([ScheduleValidationIssue])
    case missingSemester

    var errorDescription: String? {
        switch self {
        case .fileTooLarge(let maximumBytes):
            return "文件过大，请选择小于 \(maximumBytes / 1_048_576) MB 的课表 JSON。"
        case .malformedFile:
            return "文件不是有效的轻课课表 JSON。"
        case .unsupportedSchemaVersion(let version):
            if let version {
                return "暂不支持版本 \(version) 的课表文件。"
            }
            return "课表文件缺少有效的 schemaVersion。"
        case .invalidData(let issues):
            return issues.first?.message ?? "课表内容未通过校验。"
        case .missingSemester:
            return "请先设置学期，再导出课表备份。"
        }
    }
}

enum ScheduleDataTransfer {
    static let maximumImportBytes = 5 * 1_048_576

    static func previewImport(
        contents: Data,
        calendar: Calendar
    ) throws -> ScheduleImportPreview {
        guard contents.count <= maximumImportBytes else {
            throw ScheduleDataTransferError.fileTooLarge(maximumBytes: maximumImportBytes)
        }

        guard
            let object = try? JSONSerialization.jsonObject(with: contents),
            let dictionary = object as? [String: Any]
        else {
            throw ScheduleDataTransferError.malformedFile
        }
        guard let version = dictionary["schemaVersion"] as? Int else {
            throw ScheduleDataTransferError.unsupportedSchemaVersion(nil)
        }
        guard version == ScheduleDataDTO.supportedSchemaVersion else {
            throw ScheduleDataTransferError.unsupportedSchemaVersion(version)
        }
        guard hasOnlyVersion1Fields(dictionary) else {
            throw ScheduleDataTransferError.malformedFile
        }

        let decoded: ScheduleDataDTO
        do {
            decoded = try JSONDecoder().decode(ScheduleDataDTO.self, from: contents)
        } catch {
            throw ScheduleDataTransferError.malformedFile
        }

        let issues = ScheduleValidator.validate(decoded, calendar: calendar)
        guard issues.isEmpty else {
            throw ScheduleDataTransferError.invalidData(issues)
        }
        return ScheduleImportPreview(data: decoded)
    }

    private static func hasOnlyVersion1Fields(_ root: [String: Any]) -> Bool {
        guard hasOnlyKeys(root, allowed: ["schemaVersion", "semester", "courses", "updatedAt"]) else {
            return false
        }

        if let semester = root["semester"], !(semester is NSNull) {
            guard
                let semester = semester as? [String: Any],
                hasOnlyKeys(semester, allowed: ["id", "name", "startDate", "totalWeeks", "periods"]),
                let periods = semester["periods"] as? [Any]
            else {
                return false
            }
            for period in periods {
                guard
                    let period = period as? [String: Any],
                    hasOnlyKeys(period, allowed: ["number", "startTime", "endTime"])
                else {
                    return false
                }
            }
        }

        guard let courses = root["courses"] as? [Any] else {
            return false
        }
        for course in courses {
            guard
                let course = course as? [String: Any],
                hasOnlyKeys(course, allowed: ["id", "name", "teacher", "color", "schedules"]),
                let schedules = course["schedules"] as? [Any]
            else {
                return false
            }
            for schedule in schedules {
                guard
                    let schedule = schedule as? [String: Any],
                    hasOnlyKeys(
                        schedule,
                        allowed: [
                            "id", "dayOfWeek", "startPeriod", "endPeriod", "startWeek",
                            "endWeek", "repeat", "classroom",
                        ]
                    )
                else {
                    return false
                }
            }
        }

        return true
    }

    private static func hasOnlyKeys(
        _ object: [String: Any],
        allowed: Set<String>
    ) -> Bool {
        Set(object.keys).isSubset(of: allowed)
    }

    static func exportDocument(
        data: ScheduleDataDTO,
        exportedAt: Date = Date(),
        calendar: Calendar = ScheduleRules.gregorianCalendar()
    ) throws -> ScheduleExportDocument {
        guard data.semester != nil else {
            throw ScheduleDataTransferError.missingSemester
        }

        let issues = ScheduleValidator.validate(data, calendar: calendar)
        guard issues.isEmpty else {
            throw ScheduleDataTransferError.invalidData(issues)
        }

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        var contents = try encoder.encode(data)
        contents.append(0x0A)

        return ScheduleExportDocument(
            fileName: "qingke-schedule-\(dateStamp(for: exportedAt, calendar: calendar)).json",
            contents: contents
        )
    }

    private static func dateStamp(for date: Date, calendar: Calendar) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return String(
            format: "%04d-%02d-%02d",
            components.year ?? 0,
            components.month ?? 0,
            components.day ?? 0
        )
    }
}
