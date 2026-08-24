import Foundation

// 1. 尝试使用 MediaRemote 获取全局媒体信息
func getMediaRemoteInfo(completion: @escaping (String?) -> Void) {
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

        if !title.isEmpty {
            completion("\(title)|||\(artist)|||\(album)|||\(isPlaying)|||\(position)|||\(duration)|||MediaRemote")
        } else {
            completion(nil)
        }
    }
}

// 2. 降级方案：使用 AppleScript 直连 Apple Music 和 Spotify
// 这是为了防止 Parallels Desktop 等虚拟机抢占 macOS 全局媒体焦点导致 MediaRemote 返回空
func getAppleScriptInfo() -> String? {
    let scriptSource = """
    set track_name to ""
    set track_artist to ""
    set track_album to ""
    set is_playing to "false"
    set track_duration to 0.0
    set track_position to 0.0

    tell application "System Events"
        set spotifyRunning to (exists process "Spotify")
        set musicRunning to (exists process "Music")
    end tell

    if spotifyRunning then
        tell application "Spotify"
            if player state is playing then
                set track_name to name of current track
                set track_artist to artist of current track
                set track_album to album of current track
                set is_playing to "true"
                set track_duration to (duration of current track) / 1000.0
                set track_position to player position
            end if
        end tell
    end if

    if track_name is "" and musicRunning then
        tell application "Music"
            if player state is playing then
                set track_name to name of current track
                set track_artist to artist of current track
                set track_album to album of current track
                set is_playing to "true"
                set track_duration to duration of current track
                set track_position to player position
            end if
        end tell
    end if

    if track_name is not "" then
        return track_name & "|||" & track_artist & "|||" & track_album & "|||" & is_playing & "|||" & track_position & "|||" & track_duration & "|||AppleScript"
    else
        return ""
    end if
    """
    
    var error: NSDictionary?
    if let scriptObject = NSAppleScript(source: scriptSource) {
        let output = scriptObject.executeAndReturnError(&error)
        if let stringValue = output.stringValue, !stringValue.isEmpty {
            return stringValue
        }
    }
    return nil
}

let group = DispatchGroup()
group.enter()
var finalResult: String? = nil

// 先尝试全局获取
getMediaRemoteInfo { result in
    finalResult = result
    group.leave()
}

// 最多等待 1 秒，防止死锁
_ = group.wait(timeout: .now() + 1.0)

// 如果全局抓取成功，直接打印
if let res = finalResult {
    print(res)
} else {
    // 否则启用 AppleScript 强行抓取
    if let fallback = getAppleScriptInfo() {
        print(fallback)
    }
}
