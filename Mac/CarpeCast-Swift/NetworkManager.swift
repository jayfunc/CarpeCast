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
    
    private let discoveryPort: UInt16 = 5001
    private let commandPort: UInt16 = 5002
    
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
        connectedDevice = device
        if syncSocket == nil {
            syncSocket = UDPSocket()
        }
    }
    
    func disconnect() {
        if let device = connectedDevice, let sock = syncSocket {
            let dict: [String: Any] = ["command": "DISCONNECT"]
            if let data = try? JSONSerialization.data(withJSONObject: dict, options: []) {
                sock.send(data: data, to: device.ip, port: UInt16(device.port))
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
                        if let idx = self?.discoveredDevices.firstIndex(where: { $0.ip == ip }) {
                            self?.discoveredDevices[idx].name = name
                            self?.discoveredDevices[idx].port = port
                            self?.discoveredDevices[idx].type = type
                            self?.discoveredDevices[idx].lastSeen = Date()
                        } else {
                            self?.discoveredDevices.append(DiscoveredDevice(ip: ip, name: name, port: port, type: type))
                        }
                    }
                }
            }
        } else {
            print("Failed to bind discovery socket")
        }
    }
    
    private func startCleanupTimer() {
        cleanupTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let now = Date()
            self.discoveredDevices.removeAll { device in
                let isLost = now.timeIntervalSince(device.lastSeen) > 10.0
                if isLost && self.connectedDevice?.ip == device.ip {
                    self.disconnect()
                }
                return isLost
            }
        }
    }
    
    private func startCommandListener() {
        commandSocket = UDPSocket()
        if commandSocket?.bind(port: commandPort) == true {
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
                        self.disconnect()
                    }
                } else {
                    MediaManager.shared.sendCommand(cmd)
                }
            }
        } else {
            print("Failed to bind command socket")
        }
    }
    
    private func startBroadcasting() {
        broadcastSocket = UDPSocket()
        broadcastSocket?.enableBroadcast()
        
        broadcastTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let msg = "CARPECAST_SENDER:\(self.deviceName):\(self.commandPort):Desktop:macOS"
            self.broadcastSocket?.send(string: msg, to: "255.255.255.255", port: 5003)
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
            if !m.albumArtBase64.isEmpty {
                if let data = Data(base64Encoded: m.albumArtBase64, options: .ignoreUnknownCharacters),
                   let image = NSImage(data: data) {
                    let targetSize = NSSize(width: 150, height: 150)
                    let newImage = NSImage(size: targetSize)
                    newImage.lockFocus()
                    image.draw(in: NSRect(origin: .zero, size: targetSize),
                               from: NSRect(origin: .zero, size: image.size),
                               operation: .copy,
                               fraction: 1.0)
                    newImage.unlockFocus()
                    if let tiff = newImage.tiffRepresentation,
                       let bitmap = NSBitmapImageRep(data: tiff),
                       let jpeg = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.5]) {
                        dict["albumArt"] = jpeg.base64EncodedString()
                    }
                }
            }
            
            if let data = try? JSONSerialization.data(withJSONObject: dict, options: []) {
                sock.send(data: data, to: device.ip, port: UInt16(device.port))
            }
        }
    }
}
