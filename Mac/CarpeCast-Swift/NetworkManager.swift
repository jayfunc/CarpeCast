import Foundation
import AppKit

struct DiscoveredDevice: Identifiable, Hashable {
    let id = UUID()
    let ip: String
    var name: String
    var port: Int
    var type: String
    var lastSeen: Date = Date()
}

class NetworkManager: ObservableObject {
    static let shared = NetworkManager()
    
    @Published var discoveredDevices: [DiscoveredDevice] = []
    @Published var connectedDevice: DiscoveredDevice? = nil
    
    let deviceName = Host.current().localizedName ?? "Mac"
    
    private var discoverySocket: UDPSocket?
    private var commandSocket: UDPSocket?
    private var broadcastSocket: UDPSocket?
    private var syncSocket: UDPSocket?
    
    // Album art caching: only compress + send when track changes
    private var lastSentTrackKey: String = ""
    private var lastProcessedAlbumArtSource: String = ""
    private var cachedAlbumArtBase64: String = ""
    private let albumArtChunkSize = 1_000
    private var albumArtTransferId: String?
    private var albumArtRetriesRemaining = 0
    
    private var discoveryPort: UInt16 {
        let port = UInt16(UserDefaults.standard.integer(forKey: "discoveryPort"))
        return port == 0 ? 5001 : port
    }
    private var senderDiscoveryPort: UInt16 {
        let port = UInt16(UserDefaults.standard.integer(forKey: "senderDiscoveryPort"))
        return port == 0 ? 5003 : port
    }
    private var commandPort: UInt16 = 0
    
    private var broadcastTimer: Timer?
    private var syncTimer: Timer?
    private var cleanupTimer: Timer?
    
    init() {
        startDiscoveryListener()
        startCommandListener()
        startBroadcasting()
        startSyncing()
        startCleanupTimer()
    }
    
    func restartNetworking() {
        discoverySocket?.close()
        commandSocket?.close()
        broadcastSocket?.close()
        syncSocket?.close()
        
        broadcastTimer?.invalidate()
        syncTimer?.invalidate()
        cleanupTimer?.invalidate()
        
        startDiscoveryListener()
        startCommandListener()
        startBroadcasting()
        startSyncing()
        startCleanupTimer()
    }
    
    func connectToDevice(_ device: DiscoveredDevice) {
        UserDefaults.standard.set(device.ip, forKey: "targetConnectedDeviceIP")
        connectedDevice = device
        if syncSocket == nil {
            syncSocket = UDPSocket()
        }
        // Reset art cache so the next sync packet includes art for the new receiver
        lastSentTrackKey = ""
        lastProcessedAlbumArtSource = ""
        cachedAlbumArtBase64 = ""
        albumArtTransferId = nil
        albumArtRetriesRemaining = 0
    }
    
    func disconnect(clearTarget: Bool = true) {
        if clearTarget {
            UserDefaults.standard.removeObject(forKey: "targetConnectedDeviceIP")
            if let device = connectedDevice, let sock = syncSocket {
                let dict: [String: Any] = ["command": "DISCONNECT"]
                if let data = try? JSONSerialization.data(withJSONObject: dict, options: []) {
                    sock.send(data: data, to: device.ip, port: UInt16(device.port))
                }
            }
        }
        connectedDevice = nil
        syncSocket?.close()
        syncSocket = nil
    }
    
    private func startDiscoveryListener() {
        discoverySocket = UDPSocket()
        if discoverySocket?.bind(port: discoveryPort) == true {
            discoverySocket?.startReceiving { [weak self] (msg, ip) in
                if msg.hasPrefix("CARPECAST_RECEIVER:") {
                    let parts = msg.components(separatedBy: ":")
                    let name = parts.count > 1 ? parts[1] : "PC"
                    let port = parts.count > 2 ? (Int(parts[2]) ?? 5000) : 5000
                    let type = parts.count > 3 ? parts[3] : "Desktop"
                    
                    DispatchQueue.main.async {
                        var currentDevice: DiscoveredDevice
                        if let idx = self?.discoveredDevices.firstIndex(where: { $0.ip == ip }) {
                            self?.discoveredDevices[idx].name = name
                            self?.discoveredDevices[idx].port = port
                            self?.discoveredDevices[idx].type = type
                            self?.discoveredDevices[idx].lastSeen = Date()
                            currentDevice = (self?.discoveredDevices[idx])!
                        } else {
                            currentDevice = DiscoveredDevice(ip: ip, name: name, port: port, type: type)
                            self?.discoveredDevices.append(currentDevice)
                        }
                        
                        // Auto-reconnect if it matches target
                        if let targetIp = UserDefaults.standard.string(forKey: "targetConnectedDeviceIP"),
                           targetIp == ip, self?.connectedDevice == nil {
                            self?.connectToDevice(currentDevice)
                        }
                    }
                }
            }
        } else {
            cLog("Failed to bind discovery socket")
        }
    }
    
    private func startCleanupTimer() {
        cleanupTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let now = Date()
            self.discoveredDevices.removeAll { device in
                let isLost = now.timeIntervalSince(device.lastSeen) > 10.0
                if isLost && self.connectedDevice?.ip == device.ip {
                    self.disconnect(clearTarget: false)
                }
                return isLost
            }
        }
    }
    
    private func startCommandListener() {
        commandSocket = UDPSocket()
        if commandSocket?.bind(port: 0) == true {
            self.commandPort = commandSocket?.localPort() ?? 0
            commandSocket?.startReceiving { [weak self] (cmd, ip) in
                guard let self = self else { return }
                if cmd == "CONNECT_REQUEST" {
                    DispatchQueue.main.async {
                        let dev = self.discoveredDevices.first(where: { $0.ip == ip }) ??
                            DiscoveredDevice(ip: ip, name: "Remote PC", port: 5000, type: "Desktop")
                        self.connectToDevice(dev)
                    }
                } else if cmd == "DISCONNECT_REQUEST" {
                    DispatchQueue.main.async {
                        self.disconnect(clearTarget: true)
                    }
                } else {
                    MediaManager.shared.sendCommand(cmd)
                }
            }
        } else {
            cLog("Failed to bind command socket")
        }
    }
    
    private func startBroadcasting() {
        broadcastSocket = UDPSocket()
        broadcastSocket?.enableBroadcast()
        
        broadcastTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let msg = "CARPECAST_SENDER:\(self.deviceName):\(self.commandPort):Desktop:macOS"
            self.broadcastSocket?.send(string: msg, to: "255.255.255.255", port: self.senderDiscoveryPort)
        }
    }
    
    private func startSyncing() {
        syncTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self, let device = self.connectedDevice, let sock = self.syncSocket else { return }
            
            let m = MediaManager.shared
            var dict: [String: Any] = [
                "title": m.title,
                "artist": m.artist,
                "album": m.album,
                "isPlaying": m.isPlaying,
                "position": m.position,
                "duration": m.duration,
                "commandPort": Int(self.commandPort),
                "deviceName": self.deviceName,
                "deviceType": "Desktop",
                "osVersion": "macOS"
            ]
            let trackKey = "\(m.title)|\(m.artist)|\(m.album)"
            let trackChanged = trackKey != self.lastSentTrackKey
            // MediaRemote can publish a new track before its artwork is available.
            // Reprocess when the artwork arrives later without treating transient
            // empty artwork on an unchanged track as a request to clear the image.
            let artworkArrived = !m.albumArtBase64.isEmpty &&
                m.albumArtBase64 != self.lastProcessedAlbumArtSource
            if trackChanged || artworkArrived {
                // Track changed - recompress art and attach it this packet
                self.lastSentTrackKey = trackKey
                self.lastProcessedAlbumArtSource = m.albumArtBase64
                self.cachedAlbumArtBase64 = ""
                self.albumArtTransferId = nil
                self.albumArtRetriesRemaining = 0
                
                NSLog("[CarpeCast] Track changed: %@, albumArtBase64 length: %d", trackKey, m.albumArtBase64.count)
                
                if !m.albumArtBase64.isEmpty,
                   let artData = Data(base64Encoded: m.albumArtBase64, options: .ignoreUnknownCharacters) {
                    
                    NSLog("[CarpeCast] Art data decoded, bytes: %d", artData.count)
                    
                    // Use CGImageSource for reliable decoding of any format (JPEG/PNG/HEIC etc.)
                    let options: [CFString: Any] = [kCGImageSourceShouldCache: false]
                    if let imageSource = CGImageSourceCreateWithData(artData as CFData, options as CFDictionary),
                       let cgImage = CGImageSourceCreateImageAtIndex(imageSource, 0, nil) {
                        
                        NSLog("[CarpeCast] CGImage created: %dx%d", cgImage.width, cgImage.height)
                        
                        let targetPixels = 500
                        let colorSpace = CGColorSpaceCreateDeviceRGB()
                        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)
                        
                        if let ctx = CGContext(data: nil,
                                              width: targetPixels,
                                              height: targetPixels,
                                              bitsPerComponent: 8,
                                              bytesPerRow: 0,
                                              space: colorSpace,
                                              bitmapInfo: bitmapInfo.rawValue) {
                            ctx.interpolationQuality = .high
                            ctx.draw(cgImage, in: CGRect(x: 0, y: 0, width: targetPixels, height: targetPixels))
                            
                            if let resizedCGImage = ctx.makeImage() {
                                let bitmapRep = NSBitmapImageRep(cgImage: resizedCGImage)
                                var compression: CGFloat = 0.8
                                while compression >= 0.1 {
                                    if let jpeg = bitmapRep.representation(using: .jpeg, properties: [.compressionFactor: compression]) {
                                        let base64 = jpeg.base64EncodedString()
                                        if base64.utf8.count < 55000 {
                                            self.cachedAlbumArtBase64 = base64
                                            NSLog("[CarpeCast] Art compressed at quality %f, base64 length: %d", compression, base64.count)
                                            break
                                        }
                                    }
                                    compression -= 0.1
                                }
                            }
                        }
                    } else {
                        NSLog("[CarpeCast] Failed to create CGImageSource or CGImage from art data")
                    }
                } else {
                    NSLog("[CarpeCast] albumArtBase64 is empty or failed to decode")
                }
                
                // Small UDP packets avoid IP fragmentation, which otherwise causes the
                // receiver to discard the whole album-art payload when one fragment drops.
                if self.cachedAlbumArtBase64.isEmpty {
                    dict["albumArt"] = ""
                    NSLog("[CarpeCast] Sending empty albumArt")
                } else {
                    self.albumArtTransferId = UUID().uuidString
                    // Re-send with the same ID so a receiver that starts mid-transfer,
                    // or loses a UDP packet, can finish assembling the cached chunks.
                    self.albumArtRetriesRemaining = 3
                }
            }

            if self.albumArtRetriesRemaining > 0,
               let transferId = self.albumArtTransferId,
               !self.cachedAlbumArtBase64.isEmpty {
                let artBytes = Array(self.cachedAlbumArtBase64.utf8)
                let chunkCount = (artBytes.count + self.albumArtChunkSize - 1) / self.albumArtChunkSize

                for chunkIndex in 0..<chunkCount {
                    let start = chunkIndex * self.albumArtChunkSize
                    let end = min(start + self.albumArtChunkSize, artBytes.count)
                    var chunkPacket = dict
                    chunkPacket["albumArtTransferId"] = transferId
                    chunkPacket["albumArtChunkIndex"] = chunkIndex
                    chunkPacket["albumArtChunkCount"] = chunkCount
                    chunkPacket["albumArtChunk"] = String(decoding: artBytes[start..<end], as: UTF8.self)

                    if let data = try? JSONSerialization.data(withJSONObject: chunkPacket, options: []) {
                        sock.send(data: data, to: device.ip, port: UInt16(device.port))
                    }
                }

                self.albumArtRetriesRemaining -= 1
                NSLog("[CarpeCast] Sending albumArt in %d chunks, total length: %d, retries remaining: %d", chunkCount, self.cachedAlbumArtBase64.count, self.albumArtRetriesRemaining)
                return
            }
            // If track has not changed, omit albumArt key entirely - Windows keeps the cached image
            
            if let data = try? JSONSerialization.data(withJSONObject: dict, options: []) {
                sock.send(data: data, to: device.ip, port: UInt16(device.port))
            }
        }
    }
}
