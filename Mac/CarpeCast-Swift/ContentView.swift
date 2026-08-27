import SwiftUI

struct DevicesView: View {
    @ObservedObject var networkManager = NetworkManager.shared
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Discovered Devices")
                .font(.headline)
                .padding(.bottom, 10)
            
            List(networkManager.discoveredDevices, id: \.self) { device in
                HStack {
                    VStack(alignment: .leading) {
                        Text(device.name).fontWeight(.bold)
                        Text("\(device.ip):\(device.port) - \(device.type)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    if networkManager.connectedDevice == device {
                        Text("Connected").foregroundColor(.green)
                        Button("Disconnect") {
                            networkManager.disconnect()
                        }
                    } else {
                        Button("Connect") {
                            networkManager.connectToDevice(device)
                        }
                    }
                }
                .padding(.vertical, 5)
            }
            .listStyle(PlainListStyle())
            
            Spacer()
        }
        .padding()
    }
}

struct SettingsView: View {
    @AppStorage("deviceName") private var deviceName: String = Host.current().localizedName ?? "Mac"
    @AppStorage("discoveryPort") private var discoveryPort: Int = 5001
    @AppStorage("commandPort") private var commandPort: Int = 5002
    @AppStorage("language") private var language: String = "System"
    
    var body: some View {
        Form {
            Section(header: Text(localized("General")).font(.headline)) {
                TextField(localized("Device Name"), text: $deviceName)
                    .onChange(of: deviceName) { _ in restartNetworking() }
                
                Picker(localized("Language"), selection: $language) {
                    Text("System").tag("System")
                    Text("English").tag("en")
                    Text("简体中文").tag("zh-Hans")
                }
                .pickerStyle(SegmentedPickerStyle())
            }
            .padding(.bottom, 15)
            
            Section(header: Text(localized("Network Ports")).font(.headline)) {
                TextField(localized("Discovery Port"), value: $discoveryPort, formatter: NumberFormatter())
                    .onChange(of: discoveryPort) { _ in restartNetworking() }
                
                TextField(localized("Command Port"), value: $commandPort, formatter: NumberFormatter())
                    .onChange(of: commandPort) { _ in restartNetworking() }
                
                Text(localized("Note: Port changes require network service restart."))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            HStack {
                Spacer()
                Button(localized("Restart Network Service")) {
                    restartNetworking()
                }
            }
            .padding(.top, 20)
        }
        .padding(30)
        .frame(maxWidth: 500, maxHeight: .infinity, alignment: .top)
    }
    
    private func restartNetworking() {
        NetworkManager.shared.restartNetworking()
    }
    
    private func localized(_ key: String) -> String {
        let lang = language == "System" ? Locale.current.languageCode ?? "en" : language
        if lang.starts(with: "zh") {
            switch key {
            case "General": return "通用"
            case "Device Name": return "设备名称"
            case "Language": return "语言"
            case "Network Ports": return "网络端口"
            case "Discovery Port": return "发现端口"
            case "Command Port": return "命令端口"
            case "Note: Port changes require network service restart.": return "注意：修改端口后需要重启网络服务以生效。"
            case "Restart Network Service": return "重启网络服务"
            default: return key
            }
        }
        return key
    }
}

struct ContentView: View {
    @AppStorage("language") private var language: String = "System"
    
    var body: some View {
        TabView {
            PlayerView()
                .tabItem { Text(localized("Now Playing")) }
            DevicesView()
                .tabItem { Text(localized("Devices")) }
            SettingsView()
                .tabItem { Text(localized("Settings")) }
        }
        .frame(minWidth: 400, minHeight: 500)
    }
    
    private func localized(_ key: String) -> String {
        let lang = language == "System" ? Locale.current.languageCode ?? "en" : language
        if lang.starts(with: "zh") {
            switch key {
            case "Now Playing": return "播放控制"
            case "Devices": return "设备状态"
            case "Settings": return "设置"
            default: return key
            }
        }
        return key
    }
}

@main
struct CarpeCastApp: App {
    // Initialize network manager and media manager early
    @StateObject var networkManager = NetworkManager.shared
    @StateObject var mediaManager = MediaManager.shared
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
