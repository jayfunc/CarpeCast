import Foundation
import AppKit

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
            completion("\(title)|||\(artist)|||\(album)|||\(isPlaying)|||\(position)|||\(duration)|||\(albumArtBase64)|||MediaRemote")
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
