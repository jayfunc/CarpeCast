import Foundation
import Network

struct DiscoveredDevice: Identifiable, Hashable {
    let id = UUID()
    let ip: String
    let name: String
    let port: Int
    let type: String
}

class NetworkManager: ObservableObject {
    static let shared = NetworkManager()
    
    @Published var discoveredDevices: [DiscoveredDevice] = []
    @Published var connectedDevice: DiscoveredDevice? = nil
    
    private var discoveryListener: NWListener?
    private var commandListener: NWListener?
    
    private var broadcastConnection: NWConnection?
    private var syncConnection: NWConnection?
    
    private var broadcastTimer: Timer?
    private var syncTimer: Timer?
    
    private let discoveryPort: NWEndpoint.Port = 5001
    private let commandPort: NWEndpoint.Port = 5002
    
    init() {
        startDiscoveryListener()
        startCommandListener()
        startBroadcasting()
        startSyncing()
    }
    
    func connectToDevice(_ device: DiscoveredDevice) {
        connectedDevice = device
        
        let endpoint = NWEndpoint.hostPort(host: NWEndpoint.Host(device.ip), port: NWEndpoint.Port(integerLiteral: UInt16(device.port)))
        syncConnection = NWConnection(to: endpoint, using: .udp)
        syncConnection?.start(queue: .global())
    }
    
    func disconnect() {
        connectedDevice = nil
        syncConnection?.cancel()
        syncConnection = nil
    }
    
    private func startDiscoveryListener() {
        do {
            let params = NWParameters.udp
            params.allowLocalEndpointReuse = true
            discoveryListener = try NWListener(using: params, on: discoveryPort)
            discoveryListener?.newConnectionHandler = { [weak self] connection in
                connection.start(queue: .global())
                self?.receiveDiscovery(connection: connection)
            }
            discoveryListener?.start(queue: .global())
        } catch {
            print("Failed to start discovery listener")
        }
    }
    
    private func receiveDiscovery(connection: NWConnection) {
        connection.receiveMessage { [weak self] (data, context, isComplete, error) in
            if let data = data, let msg = String(data: data, encoding: .utf8) {
                if msg.hasPrefix("CARPECAST_RECEIVER:") {
                    let parts = msg.components(separatedBy: ":")
                    let name = parts.count > 1 ? parts[1] : "PC"
                    let port = parts.count > 2 ? (Int(parts[2]) ?? 5000) : 5000
                    let type = parts.count > 3 ? parts[3] : "Desktop"
                    
                    let ipStr = "\(connection.endpoint)".components(separatedBy: ":").first ?? ""
                    
                    DispatchQueue.main.async {
                        if !(self?.discoveredDevices.contains(where: { $0.ip == ipStr }) ?? true) {
                            self?.discoveredDevices.append(DiscoveredDevice(ip: ipStr, name: name, port: port, type: type))
                        }
                    }
                }
            }
            if error == nil {
                self?.receiveDiscovery(connection: connection)
            }
        }
    }
    
    private func startCommandListener() {
        do {
            let params = NWParameters.udp
            params.allowLocalEndpointReuse = true
            commandListener = try NWListener(using: params, on: commandPort)
            commandListener?.newConnectionHandler = { [weak self] connection in
                connection.start(queue: .global())
                self?.receiveCommand(connection: connection)
            }
            commandListener?.start(queue: .global())
        } catch {
            print("Failed to start command listener")
        }
    }
    
    private func receiveCommand(connection: NWConnection) {
        connection.receiveMessage { [weak self] (data, context, isComplete, error) in
            guard let self = self else { return }
            if let data = data, let cmd = String(data: data, encoding: .utf8) {
                if cmd == "CONNECT_REQUEST" {
                    let ipStr = "\(connection.endpoint)".components(separatedBy: ":").first ?? ""
                    DispatchQueue.main.async {
                        // Find the device or create a dummy one to connect back
                        let dev = self.discoveredDevices.first(where: { $0.ip == ipStr }) ??
                            DiscoveredDevice(ip: ipStr, name: "Remote PC", port: 5000, type: "Desktop")
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
            if error == nil {
                self.receiveCommand(connection: connection)
            }
        }
    }
    
    private func startBroadcasting() {
        let endpoint = NWEndpoint.hostPort(host: "255.255.255.255", port: 5003)
        let params = NWParameters.udp
        params.allowLocalEndpointReuse = true
        broadcastConnection = NWConnection(to: endpoint, using: params)
        broadcastConnection?.start(queue: .global())
        
        broadcastTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            let msg = "CARPECAST_SENDER:Mac:5002:Desktop:macOS"
            self?.broadcastConnection?.send(content: msg.data(using: .utf8), completion: .contentProcessed({ _ in }))
        }
    }
    
    private func startSyncing() {
        syncTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self, let conn = self.syncConnection else { return }
            
            let m = MediaManager.shared
            var dict: [String: Any] = [
                "title": m.title,
                "artist": m.artist,
                "album": m.album,
                "isPlaying": m.isPlaying,
                "position": m.position,
                "duration": m.duration,
                "commandPort": 5002,
                "deviceName": "Mac",
                "deviceType": "Desktop",
                "osVersion": "macOS"
            ]
            if !m.albumArtBase64.isEmpty {
                dict["albumArt"] = m.albumArtBase64
            }
            
            if let data = try? JSONSerialization.data(withJSONObject: dict, options: []) {
                conn.send(content: data, completion: .contentProcessed({ _ in }))
            }
        }
    }
}
