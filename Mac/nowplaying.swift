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

var finalResult: String? = nil

// 尝试全局获取
getMediaRemoteInfo { result in
    finalResult = result
    // 收到回调后停止主循环
    CFRunLoopStop(CFRunLoopGetMain())
}

// 【关键修复】: MediaRemote 底层依赖 XPC 通信，如果主线程被 Semaphore 或 DispatchGroup 阻塞，回调永远不会触发！
// 必须启动 RunLoop 允许主线程处理系统消息。设置最多等待 1.5 秒超时。
RunLoop.main.run(until: Date(timeIntervalSinceNow: 1.5))

if let res = finalResult {
    print(res)
}
