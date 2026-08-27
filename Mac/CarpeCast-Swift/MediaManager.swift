import Foundation
import AppKit

class MediaManager: ObservableObject {
    static let shared = MediaManager()
    
    @Published var title: String = ""
    @Published var artist: String = ""
    @Published var album: String = ""
    @Published var isPlaying: Bool = false
    @Published var position: Double = 0.0
    @Published var duration: Double = 0.0
    @Published var albumArtBase64: String = ""
    @Published var lastFetchMethod: String = ""
    
    private var updateTimer: Timer?
    
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
        let parts = raw.components(separatedBy: "|||")
        if parts.count >= 6 {
            self.title = parts[0]
            self.artist = parts[1]
            self.album = parts[2]
            self.isPlaying = (parts[3] == "true")
            self.position = (Double(parts[4]) ?? 0.0) * 1000.0
            self.duration = (Double(parts[5]) ?? 0.0) * 1000.0
            self.albumArtBase64 = parts.count >= 7 ? parts[6] : ""
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
    }
    
    // MARK: - MediaRemote Fetch
    private func getMediaRemoteInfo(completion: @escaping (String?) -> Void) {
        DispatchQueue.global().async {
            let perlScriptURL = Bundle.main.bundleURL.appendingPathComponent("Contents/MacOS/nowplaying_wrapper.pl")
            let dylibURL = Bundle.main.bundleURL.appendingPathComponent("Contents/MacOS/libmac_nowplaying.dylib")
            
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/perl")
            process.arguments = [perlScriptURL.path, dylibURL.path]
            
            let pipe = Pipe()
            process.standardOutput = pipe
            process.standardError = pipe
            
            do {
                try process.run()
                process.waitUntilExit()
                
                let data = pipe.fileHandleForReading.readDataToEndOfFile()
                if let output = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) {
                    if output == "nil" || output.isEmpty {
                        completion(nil)
                    } else {
                        completion(output)
                    }
                } else {
                    completion(nil)
                }
            } catch {
                print("Failed to run mac_nowplaying: \(error)")
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
                        end if
                    end tell
                end if

                if track_name is "" and application "Music" is running then
                    tell application "Music"
                        if player state is playing or player state is paused then
                            set track_name to name of current track
                            set track_artist to artist of current track
                            set track_album to album of current track
                            if player state is playing then
                                set is_playing to "true"
                            end if
                            set track_duration to duration of current track
                            set track_position to player position
                        end if
                    end tell
                end if
            on error
                -- ignore
            end try

            if track_name is not "" then
                return track_name & "|||" & track_artist & "|||" & track_album & "|||" & is_playing & "|||" & track_position & "|||" & track_duration
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
                process.waitUntilExit()
                let data = pipe.fileHandleForReading.readDataToEndOfFile()
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
