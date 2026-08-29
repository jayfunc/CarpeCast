import Foundation

class UDPSocket {
    private var socketFD: Int32 = -1
    private var isListening = false
    
    func bind(port: UInt16) -> Bool {
        socketFD = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        if socketFD < 0 { return false }
        
        var reuse: Int32 = 1
        setsockopt(socketFD, SOL_SOCKET, SO_REUSEADDR, &reuse, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(socketFD, SOL_SOCKET, SO_REUSEPORT, &reuse, socklen_t(MemoryLayout<Int32>.size))
        
        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = port.bigEndian
        addr.sin_addr.s_addr = INADDR_ANY.bigEndian
        
        let bindResult = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.bind(socketFD, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        
        return bindResult == 0
    }
    
    func localPort() -> UInt16 {
        var addr = sockaddr_in()
        var len = socklen_t(MemoryLayout<sockaddr_in>.size)
        let result = withUnsafeMutablePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { ptr in
                getsockname(socketFD, ptr, &len)
            }
        }
        if result == 0 {
            return UInt16(bigEndian: addr.sin_port)
        }
        return 0
    }
    
    func startReceiving(handler: @escaping (String, String) -> Void) {
        isListening = true
        DispatchQueue.global().async {
            var buffer = [UInt8](repeating: 0, count: 65535)
            while self.isListening {
                var senderAddr = sockaddr_in()
                var senderAddrLen = socklen_t(MemoryLayout<sockaddr_in>.size)
                
                let bytesRead = withUnsafeMutablePointer(to: &senderAddr) {
                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                        recvfrom(self.socketFD, &buffer, buffer.count, 0, $0, &senderAddrLen)
                    }
                }
                
                if bytesRead > 0 {
                    let data = Data(buffer[0..<Int(bytesRead)])
                    if let string = String(data: data, encoding: .utf8) {
                        let ip = String(cString: inet_ntoa(senderAddr.sin_addr))
                        handler(string, ip)
                    }
                }
            }
        }
    }
    
    func send(string: String, to ip: String, port: UInt16) {
        let data = string.data(using: .utf8) ?? Data()
        send(data: data, to: ip, port: port)
    }
    
    func send(data: Data, to ip: String, port: UInt16) {
        if socketFD < 0 {
            socketFD = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        }
        
        data.withUnsafeBytes { ptr in
            var addr = sockaddr_in()
            addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
            addr.sin_family = sa_family_t(AF_INET)
            addr.sin_port = port.bigEndian
            inet_pton(AF_INET, ip, &addr.sin_addr)
            
            withUnsafePointer(to: &addr) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                    sendto(self.socketFD, ptr.baseAddress, data.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
                }
            }
        }
    }
    
    func enableBroadcast() {
        if socketFD < 0 {
            socketFD = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        }
        var broadcast: Int32 = 1
        setsockopt(socketFD, SOL_SOCKET, SO_BROADCAST, &broadcast, socklen_t(MemoryLayout<Int32>.size))
    }
    
    func close() {
        isListening = false
        if socketFD >= 0 {
            Darwin.close(socketFD)
            socketFD = -1
        }
    }
}
