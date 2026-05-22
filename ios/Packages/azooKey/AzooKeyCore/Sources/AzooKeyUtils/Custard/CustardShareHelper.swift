import CustardKit
import Foundation
import SwiftUtils

/// Helper for uploading a `Custard` JSON to the share‑link API
public struct CustardShareHelper {
    /// Base URL of the Cloudflare Workers API (without trailing slash)
    private static let baseURL = URL(string: "https://custard.azookey.com")!

    public enum ShareError: Error, LocalizedError {
        case encodeFailed
        case invalidResponse
        case sizeLimitExceeded
        case serverError(status: Int, message: String?)

        public var errorDescription: String? {
            switch self {
            case .encodeFailed:
                return "カスタムタブのエンコードに失敗しました。"
            case .invalidResponse:
                return "サーバーからの応答を解釈できませんでした。"
            case .sizeLimitExceeded:
                return "ファイルサイズが 5 MB を超えているため、共有できません。"
            case let .serverError(status, message):
                return "共有に失敗しました (HTTP \(status)). \(message ?? "")"
            }
        }
    }

    private static let maxRetries = 5
    private static let defaultRetryAfter: TimeInterval = 3

    /// Uploads the given `Custard` to the server and returns the absolute share URL.
    /// - Parameter custard: The `Custard` object to share.
    /// - Returns: A fully‑qualified URL that anyone can open to download the JSON.
    public static func upload(_ custard: Custard) async throws -> (url: URL, deleteToken: String) {
        // 1) Encode
        let encoder = JSONEncoder()
        encoder.outputFormatting = .withoutEscapingSlashes
        guard let body = try? encoder.encode(custard) else {
            throw ShareError.encodeFailed
        }

        var attempt = 0
        while true {
            attempt += 1

            // Build request each time (cannot reuse)
            var request = URLRequest(url: baseURL.appendingPathComponent("/api/share"))
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body

            // Send
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw ShareError.invalidResponse
            }

            // Success
            if http.statusCode == 201 {
                let apiResp = try JSONDecoder().decode(ShareAPIResponse.self, from: data)
                let url = baseURL.appendingPathComponent(apiResp.url)
                return (url, apiResp.delete_token)
            }

            // Rate limit → retry
            if http.statusCode == 429, attempt < Self.maxRetries {
                let retryAfterSec = TimeInterval(http.value(forHTTPHeaderField: "Retry-After") ?? "")
                    .flatMap { Double($0) } ?? Self.defaultRetryAfter
                try await Task.sleep(nanoseconds: UInt64(retryAfterSec * 1_000_000_000))
                continue
            }

            // 413 Payload Too Large
            if http.statusCode == 413 {
                throw ShareError.sizeLimitExceeded
            }
            // Other error or retries exhausted
            let message = String(data: data, encoding: .utf8)
            throw ShareError.serverError(status: http.statusCode, message: message)
        }
    }

    // MARK: - Update (PUT)

    /// Replaces an existing shared tab with a new `Custard` JSON.
    /// - Parameters:
    ///   - custard: The new `Custard` object to upload.
    ///   - shareURL: The original share URL that was returned by `upload(_: )` (e.g. `https://custard.azookey.com/tab/<uuid>`).
    ///   - updateToken: The token that the server issued at upload time (`delete_token`), required for authentication.
    /// - Throws: `ShareError` variants on failure.
    public static func update(_ custard: Custard, shareURL: URL, updateToken: String) async throws {
        // --- Encode JSON ---
        let encoder = JSONEncoder()
        encoder.outputFormatting = .withoutEscapingSlashes
        guard let body = try? encoder.encode(custard) else {
            throw ShareError.encodeFailed
        }

        // --- Validate and convert share URL -> PUT endpoint (/api/tab/{uuid}) ---
        guard checkShareLink(shareURL),
              var components = URLComponents(url: shareURL, resolvingAgainstBaseURL: false) else {
            throw ShareError.invalidResponse
        }
        components.path = components.path.replacingOccurrences(of: "/tab/", with: "/api/tab/")
        guard let putURL = components.url else {
            throw ShareError.invalidResponse
        }

        var attempt = 0
        while true {
            attempt += 1

            // Build fresh request each retry
            var request = URLRequest(url: putURL)
            request.httpMethod = "PUT"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(updateToken, forHTTPHeaderField: "X-Token")
            request.httpBody = body

            // Execute
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw ShareError.invalidResponse
            }

            // --- Success ---
            if http.statusCode == 204 {
                return
            }

            // --- Rate‑limit: exponential-style back‑off ---
            if http.statusCode == 429, attempt < Self.maxRetries {
                let retryAfterSec = TimeInterval(http.value(forHTTPHeaderField: "Retry-After") ?? "")
                    .flatMap { Double($0) } ?? Self.defaultRetryAfter
                try await Task.sleep(nanoseconds: UInt64(retryAfterSec * 1_000_000_000))
                continue
            }

            // --- Specific errors ---
            if http.statusCode == 413 {
                throw ShareError.sizeLimitExceeded
            }

            // --- Other HTTP error ---
            let message = String(data: data, encoding: .utf8)
            throw ShareError.serverError(status: http.statusCode, message: message)
        }
    }

    /// Checks whether the given URL points to a valid share‑link for this app.
    /// - Parameter url: The URL provided by the user.
    /// - Returns: `true` if the URL’s host matches `baseURL` and the path matches `/api/tab/<uuid>`.
    public static func checkShareLink(_ url: URL) -> Bool {
        // Must have same host (subdomain + workers.dev or custom)
        guard url.scheme == "https",
              url.host == baseURL.host else {
            return false
        }

        // Path must be /api/tab/{uuid}
        let pattern = #"^/tab/[0-9a-fA-F\-]{36}$"#
        return url.path.range(of: pattern, options: .regularExpression) != nil
    }

    /// Verifies that the share link is valid **and** the tab still exists on the server.
    /// Performs a lightweight `HEAD` request (falls back to `GET` if HEAD not allowed).
    /// - Returns: `true` if the server responds with 200 OK.
    public static func verifyShareLink(_ url: URL) async -> Bool {
        // 1) quick client-side check
        guard checkShareLink(url) else { return false }

        // Use the lightweight /api/info/{id} endpoint instead of /api/tab/{id}
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return false
        }
        components.path = components.path
            .replacingOccurrences(of: "/tab/", with: "/api/meta/")
        guard let infoURL = components.url else { return false }
        print(#function, infoURL)

        var req = URLRequest(url: infoURL)
        req.httpMethod = "GET"
        req.timeoutInterval = 5

        do {
            let (_, response) = try await URLSession.shared.data(for: req)
            print(response)
            if let http = response as? HTTPURLResponse {
                if http.statusCode == 200 {
                    return true
                }
                if http.statusCode == 405 {
                    var getReq = URLRequest(url: infoURL)
                    getReq.httpMethod = "GET"
                    let (_, getResp) = try await URLSession.shared.data(for: getReq)
                    return (getResp as? HTTPURLResponse)?.statusCode == 200
                }
            }
        } catch {
            // network fail -> treat as invalid
            debug(#function, error)
        }
        return false
    }

    struct ShareAPIResponse: Decodable {
        let url: String
        /// Deletion token returned by the API (30‑day lifetime).
        let delete_token: String
    }
}
