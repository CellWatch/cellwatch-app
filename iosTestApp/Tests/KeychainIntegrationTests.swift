import XCTest
import sharedKit

final class KeychainIntegrationTests: XCTestCase {
    private let keyIdPrefix = "ios-test-app-keychain"

    override func setUp() {
        super.setUp()
        SecureKeyStore.shared.initialize(platformContext: nil)
    }

    func testGetOrCreateIsStable() {
        let keyId = uniqueKeyId("stable")
        SecureKeyStore.shared.deleteKey(keyId: keyId)

        let first = SecureKeyStore.shared.getOrCreateKeyBase64(keyId: keyId)
        let second = SecureKeyStore.shared.getOrCreateKeyBase64(keyId: keyId)

        XCTAssertEqual(first, second)
    }

    func testDeleteRotatesKey() {
        let keyId = uniqueKeyId("rotate")
        SecureKeyStore.shared.deleteKey(keyId: keyId)

        let first = SecureKeyStore.shared.getOrCreateKeyBase64(keyId: keyId)
        SecureKeyStore.shared.deleteKey(keyId: keyId)
        let second = SecureKeyStore.shared.getOrCreateKeyBase64(keyId: keyId)

        XCTAssertNotEqual(first, second)
    }

    func testSecureEncryptorRoundTrip() {
        let keyId = uniqueKeyId("encrypt")
        SecureKeyStore.shared.deleteKey(keyId: keyId)

        let plaintext = "ios-test-app-secret"
        let ciphertext = SecureEncryptor.shared.encrypt(
            plaintext: plaintext,
            keyId: keyId,
            associatedData: "aad"
        )
        let decrypted = SecureEncryptor.shared.decrypt(
            envelope: ciphertext,
            keyId: keyId,
            associatedData: "aad"
        )

        XCTAssertEqual(plaintext, decrypted)
    }

    private func uniqueKeyId(_ suffix: String) -> String {
        "\(keyIdPrefix)-\(suffix)-\(UUID().uuidString)"
    }
}
