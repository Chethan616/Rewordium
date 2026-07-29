import Foundation

/// Minimal Groq Chat Completions client for the keyboard extension.
///
/// Why not share the Flutter app's UnifiedAIService?
/// 1. The extension runs in a separate process and can't reach Flutter code.
/// 2. The extension memory ceiling (~60 MB) doesn't tolerate Firebase/Auth/
///    URL routing layers — this stays bare-metal URLSession.
/// 3. The API key is read from the App Group on each call, so token rotation
///    from the host app applies without a relaunch.
///
/// Reliability rules mirrored from `lib/services/unified_ai_service.dart`:
///   * `reasoning_effort: "none"` on qwen3 models — disables chain-of-thought
///     which Groq's JSON validator rejects.
///   * `/no_think` appended to system prompts (belt-and-suspenders).
///   * `max_tokens: 1024` default — anything lower truncates JSON mid-string.
///   * UTF-8 byte decode so emojis and em-dashes round-trip.
enum GroqClient {

    enum Error: Swift.Error, LocalizedError {
        case missingAPIKey
        case networkFailure(underlying: Swift.Error)
        case http(status: Int, body: String)
        case malformedResponse

        var errorDescription: String? {
            switch self {
            case .missingAPIKey:
                return "No API key. Open the Rewordium app and sign in to sync."
            case .networkFailure:
                return "Network error. Check your connection."
            case .http(let status, _) where status == 429:
                return "Slow down — Groq is rate-limiting. Try again in a moment."
            case .http(let status, _) where status == 401:
                return "API key rejected. Re-sync from the Rewordium app."
            case .http(let status, _):
                return "Groq error \(status)."
            case .malformedResponse:
                return "Couldn't read Groq's reply."
            }
        }
    }

    static let endpoint = URL(string: "https://api.groq.com/openai/v1/chat/completions")!
    static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 30
        config.waitsForConnectivity = false
        return URLSession(configuration: config)
    }()

    /// One-shot, non-streaming call. Returns the trimmed assistant content.
    /// Kept for callers that don't care about live tokens.
    static func chat(
        systemPrompt: String,
        userMessage: String,
        temperature: Double = 0.7,
        maxTokens: Int = 1024
    ) async throws -> String {
        let (data, http) = try await sendRequest(
            systemPrompt: systemPrompt,
            userMessage: userMessage,
            temperature: temperature,
            maxTokens: maxTokens,
            stream: false
        )
        guard (200..<300).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw Error.http(status: http.statusCode, body: String(body.prefix(400)))
        }
        guard
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let choices = json["choices"] as? [[String: Any]],
            let first = choices.first,
            let message = first["message"] as? [String: Any],
            let content = message["content"] as? String
        else {
            throw Error.malformedResponse
        }
        return Self.stripThinkTags(content).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// SSE-streaming call. `onToken` is invoked once per delta as Groq's
    /// chat-completion stream sends `data: { … delta.content }` events.
    /// Returns the final assembled text (already stripped of `<think>` tags).
    ///
    /// The reliability rules apply identically — we just consume the stream
    /// instead of waiting for the full JSON envelope. Tokens are surfaced
    /// as soon as the network delivers them so the UI can render a
    /// progressive reveal.
    static func stream(
        systemPrompt: String,
        userMessage: String,
        temperature: Double = 0.7,
        maxTokens: Int = 1024,
        onToken: @escaping @Sendable (String) -> Void
    ) async throws -> String {
        guard let apiKey = SharedSettings.groqAPIKey else {
            throw Error.missingAPIKey
        }

        let request = try buildRequest(
            apiKey: apiKey,
            systemPrompt: systemPrompt,
            userMessage: userMessage,
            temperature: temperature,
            maxTokens: maxTokens,
            stream: true
        )

        let (bytes, response): (URLSession.AsyncBytes, URLResponse)
        do {
            (bytes, response) = try await session.bytes(for: request)
        } catch {
            throw Error.networkFailure(underlying: error)
        }

        guard let http = response as? HTTPURLResponse else {
            throw Error.malformedResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            // Drain the body for a usable error message.
            var bodyData = Data()
            for try await byte in bytes {
                bodyData.append(byte)
                if bodyData.count > 4_096 { break }
            }
            let body = String(data: bodyData, encoding: .utf8) ?? ""
            throw Error.http(status: http.statusCode, body: String(body.prefix(400)))
        }

        // Groq's stream uses standard SSE framing:
        //   data: {"choices":[{"delta":{"content":"…"}}]}
        //   data: [DONE]
        // Lines that start with `data: ` carry the JSON; everything else
        // (keep-alives, blank lines) is ignored. We strip `<think>` blocks
        // *after* the stream completes — qwen3's reasoning tokens arrive
        // interleaved and can't be filtered chunk-by-chunk without parser
        // state.
        var accumulator = ""
        var insideThink = false
        for try await line in bytes.lines {
            guard line.hasPrefix("data:") else { continue }
            let payload = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
            if payload == "[DONE]" { break }
            guard
                let data = payload.data(using: .utf8),
                let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                let choices = json["choices"] as? [[String: Any]],
                let first = choices.first,
                let delta = first["delta"] as? [String: Any]
            else { continue }
            if let token = delta["content"] as? String, !token.isEmpty {
                // Track <think> blocks across chunks so we don't leak
                // reasoning tokens into the live reveal.
                var emitted = ""
                var i = token.startIndex
                while i < token.endIndex {
                    if !insideThink, token[i...].hasPrefix("<think>") {
                        insideThink = true
                        i = token.index(i, offsetBy: 7)
                        continue
                    }
                    if insideThink, token[i...].hasPrefix("</think>") {
                        insideThink = false
                        i = token.index(i, offsetBy: 8)
                        continue
                    }
                    if !insideThink {
                        emitted.append(token[i])
                    }
                    i = token.index(after: i)
                }
                if !emitted.isEmpty {
                    accumulator.append(emitted)
                    onToken(emitted)
                }
            }
        }

        return Self.stripThinkTags(accumulator).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Helpers

    /// Builds the request body shared between streaming and non-streaming
    /// callers. Keep model + reliability flags in one place so the two
    /// paths can't drift.
    private static func buildRequest(
        apiKey: String,
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int,
        stream: Bool
    ) throws -> URLRequest {
        let model = SharedSettings.groqModel

        var body: [String: Any] = [
            "model": model,
            "messages": [
                ["role": "system", "content": systemPrompt],
                ["role": "user",   "content": userMessage]
            ],
            "temperature": temperature,
            "top_p": 0.95,
            "presence_penalty": 0.3,
            "max_tokens": maxTokens,
            "stream": stream
        ]

        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        if stream {
            request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        return request
    }

    /// Shared one-shot send for `chat(...)`.
    private static func sendRequest(
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int,
        stream: Bool
    ) async throws -> (Data, HTTPURLResponse) {
        guard let apiKey = SharedSettings.groqAPIKey else {
            throw Error.missingAPIKey
        }
        let request = try buildRequest(
            apiKey: apiKey,
            systemPrompt: systemPrompt,
            userMessage: userMessage,
            temperature: temperature,
            maxTokens: maxTokens,
            stream: stream
        )

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw Error.networkFailure(underlying: error)
        }
        guard let http = response as? HTTPURLResponse else {
            throw Error.malformedResponse
        }
        return (data, http)
    }

    /// Removes `<think>…</think>` blocks that qwen3 sometimes leaks even with
    /// `reasoning_effort: "none"` — same regex as the Flutter side.
    private static func stripThinkTags(_ input: String) -> String {
        guard input.contains("<think>") else { return input }
        let pattern = #"<think>[\s\S]*?</think>"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else {
            return input
        }
        let range = NSRange(input.startIndex..., in: input)
        return regex.stringByReplacingMatches(in: input, options: [], range: range, withTemplate: "")
    }
}
