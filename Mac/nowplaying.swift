import Foundation

// 动态加载 macOS 隐藏的私有框架 MediaRemote
let bundleURL = NSURL(fileURLWithPath: "/System/Library/PrivateFrameworks/MediaRemote.framework")
guard let bundle = CFBundleCreate(kCFAllocatorDefault, bundleURL) else { exit(1) }

// 获取私有函数指针
guard let pointer = CFBundleGetFunctionPointerForName(bundle, "MRMediaRemoteGetNowPlayingInfo" as CFString) else { exit(1) }

// 定义函数签名 (异步回调)
typealias MRMediaRemoteGetNowPlayingInfoFunction = @convention(c) (DispatchQueue, @escaping ([String: Any]) -> Void) -> Void
let MRMediaRemoteGetNowPlayingInfo = unsafeBitCast(pointer, to: MRMediaRemoteGetNowPlayingInfoFunction.self)

let group = DispatchGroup()
group.enter()

var trackTitle = ""
var trackArtist = ""

// 请求系统当前的播放信息
MRMediaRemoteGetNowPlayingInfo(DispatchQueue.global()) { info in
    // 提取歌名和歌手
    trackTitle = (info["kMRMediaRemoteNowPlayingInfoTitle"] as? String) ?? ""
    trackArtist = (info["kMRMediaRemoteNowPlayingInfoArtist"] as? String) ?? ""
    let album = (info["kMRMediaRemoteNowPlayingInfoAlbum"] as? String) ?? ""
    let rate = (info["kMRMediaRemoteNowPlayingInfoPlaybackRate"] as? Double) ?? 0.0
    let isPlaying = rate > 0.0 ? "true" : "false"
    let duration = (info["kMRMediaRemoteNowPlayingInfoDuration"] as? Double) ?? 0.0
    let position = (info["kMRMediaRemoteNowPlayingInfoElapsedTime"] as? Double) ?? 0.0
    
    group.leave()
}

// 等待回调完成
group.wait()

// 输出给 Python 解析，格式为：歌名|||歌手|||专辑|||是否播放|||进度|||总时长
if !trackTitle.isEmpty {
    print("\(trackTitle)|||\(trackArtist)|||\(album)|||\(isPlaying)|||\(position)|||\(duration)")
}
