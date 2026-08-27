import AppKit
import Foundation

@_cdecl("run_helper")
public func run_helper() {
    let bundlePath = "/System/Library/PrivateFrameworks/MediaRemote.framework"
    guard let bundle = CFBundleCreate(kCFAllocatorDefault, NSURL(fileURLWithPath: bundlePath)) else {
        print("ERROR: Cannot load MediaRemote")
        return
    }

    typealias MRMediaRemoteGetNowPlayingInfoFunction = @convention(c) (DispatchQueue, @escaping ([String: Any]?) -> Void) -> Void
    guard let pointer = CFBundleGetFunctionPointerForName(bundle, "MRMediaRemoteGetNowPlayingInfo" as CFString) else {
        print("ERROR: Cannot find function pointer")
        return
    }

    let MRMediaRemoteGetNowPlayingInfo = unsafeBitCast(pointer, to: MRMediaRemoteGetNowPlayingInfoFunction.self)

    try? "Perl injected and MRMediaRemoteGetNowPlayingInfo loaded".write(toFile: "/tmp/perl_debug.log", atomically: true, encoding: .utf8)

    let group = DispatchGroup()
    group.enter()

    MRMediaRemoteGetNowPlayingInfo(DispatchQueue.main) { infoOpt in
        defer { group.leave() }
        
        guard let info = infoOpt else {
            try? "infoOpt is nil".write(toFile: "/tmp/perl_debug.log", atomically: true, encoding: .utf8)
            print("nil")
            return
        }
        
        try? "infoOpt received: \(info.keys)".write(toFile: "/tmp/perl_debug.log", atomically: true, encoding: .utf8)
        
        let title = (info["kMRMediaRemoteNowPlayingInfoTitle"] as? String) ?? ""
        let artist = (info["kMRMediaRemoteNowPlayingInfoArtist"] as? String) ?? ""
        let album = (info["kMRMediaRemoteNowPlayingInfoAlbum"] as? String) ?? ""
        
        // Playback rate
        var isPlaying = false
        if let rate = info["kMRMediaRemoteNowPlayingInfoPlaybackRate"] as? Double, rate > 0 {
            isPlaying = true
        }
        
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
        
        if title.isEmpty {
            print("nil")
        } else {
            print("\(title)|||\(artist)|||\(album)|||\(isPlaying)|||\(position)|||\(duration)|||\(albumArtBase64)")
        }
    }

    // Timeout after 1.5 seconds if MediaRemote doesn't respond
    _ = group.wait(timeout: .now() + 1.5)
}
