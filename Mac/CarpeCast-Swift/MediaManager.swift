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
        registerForNotifications()
        startPolling()
    }
    
    private func registerForNotifications() {
        let bundleURL = NSURL(fileURLWithPath: "/System/Library/PrivateFrameworks/MediaRemote.framework")
        guard let bundle = CFBundleCreate(kCFAllocatorDefault, bundleURL),
              let pointer = CFBundleGetFunctionPointerForName(bundle, "MRMediaRemoteRegisterForNowPlayingNotifications" as CFString) else {
            return
        }
        typealias RegisterFunc = @convention(c) (DispatchQueue) -> Void
        let register = unsafeBitCast(pointer, to: RegisterFunc.self)
        register(DispatchQueue.main)
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
        let bundleURL = NSURL(fileURLWithPath: "/System/Library/PrivateFrameworks/MediaRemote.framework")
        guard let bundle = CFBundleCreate(kCFAllocatorDefault, bundleURL),
              let pointer = CFBundleGetFunctionPointerForName(bundle, "MRMediaRemoteGetNowPlayingInfo" as CFString) else {
            completion(nil)
            return
        }

        typealias MRMediaRemoteGetNowPlayingInfoFunction = @convention(c) (DispatchQueue, @escaping ([String: Any]) -> Void) -> Void
        let MRMediaRemoteGetNowPlayingInfo = unsafeBitCast(pointer, to: MRMediaRemoteGetNowPlayingInfoFunction.self)

        MRMediaRemoteGetNowPlayingInfo(DispatchQueue.global()) { info in
            let title = (info["kMRMediaRemoteNowPlayingInfoTitle"] as? String) ?? ""
            let artist = (info["kMRMediaRemoteNowPlayingInfoArtist"] as? String) ?? ""
            let album = (info["kMRMediaRemoteNowPlayingInfoAlbum"] as? String) ?? ""
            let rate = (info["kMRMediaRemoteNowPlayingInfoPlaybackRate"] as? Double) ?? 0.0
            let isPlaying = rate > 0.0 ? "true" : "false"
            let duration = (info["kMRMediaRemoteNowPlayingInfoDuration"] as? Double) ?? 0.0
            let position = (info["kMRMediaRemoteNowPlayingInfoElapsedTime"] as? Double) ?? 0.0

            var albumArtBase64 = ""
            if let artworkData = info["kMRMediaRemoteNowPlayingInfoArtworkData"] as? Data {
                if let image = NSImage(data: artworkData) {
                    let maxDim: CGFloat = 500.0
                    var size = image.size
                    if size.width > maxDim || size.height > maxDim {
                        let ratio = min(maxDim / size.width, maxDim / size.height)
                        size.width = round(size.width * ratio)
                        size.height = round(size.height * ratio)
                        let resized = NSImage(size: size)
                        resized.lockFocus()
                        image.draw(in: NSRect(origin: .zero, size: size), from: .zero, operation: .copy, fraction: 1.0)
                        resized.unlockFocus()
                        
                        if let tiff = resized.tiffRepresentation, let bitmap = NSBitmapImageRep(data: tiff) {
                            if let jpegData = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.5]) {
                                albumArtBase64 = jpegData.base64EncodedString()
                            }
                        }
                    } else {
                        if let tiff = image.tiffRepresentation, let bitmap = NSBitmapImageRep(data: tiff) {
                            if let jpegData = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.5]) {
                                albumArtBase64 = jpegData.base64EncodedString()
                            }
                        }
                    }
                }
            }

            if !title.isEmpty {
                completion("\(title)|||\(artist)|||\(album)|||\(isPlaying)|||\(position)|||\(duration)|||\(albumArtBase64)")
            } else {
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
