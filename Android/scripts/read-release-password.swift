import Foundation
import Security

guard CommandLine.arguments.count == 3 else {
    fputs("Expected a Keychain service and account.\n", stderr)
    exit(2)
}

let query: [String: Any] = [
    kSecClass as String: kSecClassGenericPassword,
    kSecAttrService as String: CommandLine.arguments[1],
    kSecAttrAccount as String: CommandLine.arguments[2],
    kSecReturnData as String: true,
    kSecMatchLimit as String: kSecMatchLimitOne,
]
var item: CFTypeRef?
let status = SecItemCopyMatching(query as CFDictionary, &item)
guard status == errSecSuccess, let password = item as? Data else {
    fputs("Could not read the release password from the macOS login Keychain.\n", stderr)
    exit(1)
}

// The caller captures stdout directly into a shell variable; never print the value to logs.
FileHandle.standardOutput.write(password)
