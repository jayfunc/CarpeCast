import Foundation
import AppKit

class MediaManager: ObservableObject {
    static let shared = MediaManager()
    private static let allowAllSourcesKey = "allowAllSources"
    private static let allowedSourcesKey = "allowedSources"
    
    @Published var title: String = ""
    @Published var artist: String = ""
    @Published var album: String = ""
    @Published var isPlaying: Bool = false
    @Published var position: Double = 0.0
    @Published var duration: Double = 0.0
    @Published var albumArtBase64: String = ""
    @Published var lastFetchMethod: String = ""
    @Published var sourceBundleIdentifier: String?
    
    private var updateTimer: Timer?

    private static func isSourceAllowed(_ bundleIdentifier: String?) -> Bool {
        if UserDefaults.standard.object(forKey: allowAllSourcesKey) == nil ||
            UserDefaults.standard.bool(forKey: allowAllSourcesKey) {
            return true
        }

        guard let bundleIdentifier else {
            return false
        }

        return Set(UserDefaults.standard.stringArray(forKey: allowedSourcesKey) ?? []).contains(bundleIdentifier)
    }
    
    init() {
        startPolling()
    }
    
    func startPolling() {
        updateTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.fetchGlobalTrackInfo()
        }
    }
    
    func fetchGlobalTrackInfo() {
        getMediaRemoteInfo { [weak self] result in
            guard let self = self else { return }
            DispatchQueue.main.async {
                if let res = result {
                    self.parseAndApplyInfo(res, method: "MediaRemote")
                } else {
                    self.getAppleScriptFallback { asResult in
                        DispatchQueue.main.async {
                            if let asRes = asResult {
                                self.parseAndApplyInfo(asRes, method: "AppleScript")
                            } else {
                                self.clearInfo()
                            }
                        }
                    }
                }
            }
        }
    }
    
    private func parseAndApplyInfo(_ raw: String, method: String) {
        if method == "AppleScript" {
            let parts = raw.components(separatedBy: "|||")
            if parts.count >= 6 {
                let sourceBundleIdentifier = parts.count >= 7 ? parts[6] : nil
                guard Self.isSourceAllowed(sourceBundleIdentifier) else {
                    clearInfo()
                    return
                }

                self.sourceBundleIdentifier = sourceBundleIdentifier
                self.title = parts[0]
                self.artist = parts[1]
                self.album = parts[2]
                self.isPlaying = (parts[3] == "true")
                
                let posStr = parts[4].replacingOccurrences(of: ",", with: ".")
                self.position = (Double(posStr) ?? 0.0) * 1000.0
                
                let durStr = parts[5].replacingOccurrences(of: ",", with: ".")
                self.duration = (Double(durStr) ?? 0.0) * 1000.0
                
                self.albumArtBase64 = parts.count >= 8 ? parts[7] : ""
                self.lastFetchMethod = method
            }
        } else {
            // MediaRemote Adapter JSON
            guard let data = raw.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] else {
                return
            }
            
            let sourceBundleIdentifier = json["parentApplicationBundleIdentifier"] as? String ??
                json["bundleIdentifier"] as? String
            guard Self.isSourceAllowed(sourceBundleIdentifier) else {
                clearInfo()
                return
            }

            self.sourceBundleIdentifier = sourceBundleIdentifier
            self.title = json["title"] as? String ?? ""
            self.artist = json["artist"] as? String ?? ""
            self.album = json["album"] as? String ?? ""
            
            if let playingNum = json["playing"] as? NSNumber {
                self.isPlaying = playingNum.boolValue
            } else {
                self.isPlaying = false
            }
            
            self.position = (json["elapsedTimeNow"] as? Double ?? json["elapsedTime"] as? Double ?? 0.0) * 1000.0
            self.duration = (json["duration"] as? Double ?? 0.0) * 1000.0
            
            if let artwork = json["artworkData"] as? String {
                self.albumArtBase64 = artwork
            } else {
                self.albumArtBase64 = ""
            }
            
            self.lastFetchMethod = method
        }
    }
    
    private func clearInfo() {
        self.title = ""
        self.artist = ""
        self.album = ""
        self.isPlaying = false
        self.position = 0.0
        self.duration = 0.0
        self.albumArtBase64 = ""
        self.lastFetchMethod = ""
        self.sourceBundleIdentifier = nil
    }
    
    // MARK: - MediaRemote Fetch
    private func getMediaRemoteInfo(completion: @escaping (String?) -> Void) {
        DispatchQueue.global().async {
            let perlScriptURL = Bundle.main.bundleURL.appendingPathComponent("Contents/MacOS/mediaremote-adapter.pl")
            let frameworkURL = Bundle.main.bundleURL.appendingPathComponent("Contents/Frameworks/MediaRemoteAdapter.framework")
            
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/perl")
            // Use 'get' with '--now'
            process.arguments = [perlScriptURL.path, frameworkURL.path, "get", "--now"]
            
            let pipe = Pipe()
            process.standardOutput = pipe
            process.standardError = pipe
            
            do {
                try process.run()
                let data = pipe.fileHandleForReading.readDataToEndOfFile()
                process.waitUntilExit()
                
                if let output = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) {
                    if output == "null" || output.isEmpty {
                        completion(nil)
                    } else if output.starts(with: "{") {
                        completion(output)
                    } else {
                        completion(nil)
                    }
                } else {
                    completion(nil)
                }
            } catch {
                completion(nil)
            }
        }
    }
    
    // MARK: - AppleScript Fetch
    private func getAppleScriptFallback(completion: @escaping (String?) -> Void) {
        DispatchQueue.global().async {
            let script = """
            set track_name to ""
            set track_artist to ""
            set track_album to ""
            set is_playing to "false"
            set track_duration to 0.0
            set track_position to 0.0
            set source_bundle_id to ""

            try
                if application "Spotify" is running then
                    tell application "Spotify"
                        if player state is playing or player state is paused then
                            set track_name to name of current track
                            set track_artist to artist of current track
                            set track_album to album of current track
                            if player state is playing then
                                set is_playing to "true"
                            end if
                            set track_duration to (duration of current track) / 1000.0
                            set track_position to player position
                            set source_bundle_id to "com.spotify.client"
                        end if
                    end tell
                end if

                if track_name is "" and application "Music" is running then
                    -- Removed Apple Music polling to prevent random track skipping bug
                end if
            on error
                -- ignore
            end try

            if track_name is not "" then
                return track_name & "|||" & track_artist & "|||" & track_album & "|||" & is_playing & "|||" & track_position & "|||" & track_duration & "|||" & source_bundle_id
            else
                return ""
            end if
            """
            
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
            process.arguments = ["-e", script]
            let pipe = Pipe()
            process.standardOutput = pipe
            
            do {
                try process.run()
                let data = pipe.fileHandleForReading.readDataToEndOfFile()
                process.waitUntilExit()
                let output = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
                if let out = output, !out.isEmpty {
                    completion(out)
                } else {
                    completion(nil)
                }
            } catch {
                completion(nil)
            }
        }
    }
    
    // MARK: - Control Commands
    func sendCommand(_ cmd: String) {
        if cmd == "PLAY" || cmd == "PAUSE" || cmd == "TOGGLE_PLAY" {
            executeAppleScript(command: "playpause")
        } else if cmd == "NEXT" {
            executeAppleScript(command: "next track")
        } else if cmd == "PREV" {
            executeAppleScript(command: "previous track")
        } else if cmd.starts(with: "SEEK:") {
            let posMs = cmd.dropFirst("SEEK:".count)
            if let ms = Double(posMs) {
                let sec = ms / 1000.0
                executeAppleScriptSeek(position: sec)
            }
        }
    }
    
    private func executeAppleScript(command: String) {
        let script = """
        try
            if application "Spotify" is running then
                tell application "Spotify" to \(command)
            else if application "Music" is running then
                tell application "Music" to \(command)
            end if
        end try
        """
        runOsascript(script)
    }
    
    private func executeAppleScriptSeek(position: Double) {
        let script = """
        try
            if application "Spotify" is running then
                tell application "Spotify" to set player position to \(position)
            else if application "Music" is running then
                tell application "Music" to set player position to \(position)
            end if
        end try
        """
        runOsascript(script)
    }
    
    private func runOsascript(_ script: String) {
        DispatchQueue.global().async {
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
            process.arguments = ["-e", script]
            try? process.run()
        }
    }
}
