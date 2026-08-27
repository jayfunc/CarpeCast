import SwiftUI

struct DevicesView: View {
    @ObservedObject var networkManager = NetworkManager.shared
    @AppStorage("language") private var language: String = "System"
    
    var body: some View {
        VStack(alignment: .leading) {
            Text(localized("Discovered Devices"))
                .font(.headline)
                .padding(.bottom, 10)
            
            if networkManager.discoveredDevices.isEmpty {
                VStack(spacing: 20) {
                    Spacer()
                    ProgressView()
                    Text(localized("Waiting for Windows client to connect..."))
                        .foregroundColor(.secondary)
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(networkManager.discoveredDevices, id: \.ip) { device in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(device.name).fontWeight(.bold)
                            Text("\(device.ip):\(device.port) - \(device.type)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        if networkManager.connectedDevice?.ip == device.ip {
                            Text(localized("Connected")).foregroundColor(.green)
                            Button(localized("Disconnect")) {
                                networkManager.disconnect()
                            }
                        } else {
                            Button(localized("Connect")) {
                                networkManager.connectToDevice(device)
                            }
                        }
                    }
                    .padding(.vertical, 5)
                }
                .listStyle(PlainListStyle())
            }
            
            Spacer()
        }
        .padding()
    }
    
    private func localized(_ key: String) -> String { getLocalizedString(key, language: language) }
}

struct SettingsView: View {
    @AppStorage("deviceName") private var deviceName: String = Host.current().localizedName ?? "Mac"
    @AppStorage("discoveryPort") private var discoveryPort: Int = 5001
    @AppStorage("language") private var language: String = "System"
    @AppStorage("theme") private var theme: String = "System"
    
    var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }
    
    var body: some View {
        ScrollView {
            VStack {
                VStack(spacing: 10) {
                    Image(nsImage: NSImage(named: "AppIcon") ?? NSImage())
                        .resizable()
                        .frame(width: 80, height: 80)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    
                    Text("CarpeCast")
                        .font(.title2)
                        .fontWeight(.bold)
                    Text("v\(appVersion)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding(.top, 20)
                
                Form {
                    Section(header: Text(localized("General")).font(.headline)) {
                        TextField(localized("Device Name"), text: $deviceName)
                            .onChange(of: deviceName) { _ in restartNetworking() }
                        
                        Picker(localized("Language"), selection: $language) {
                            Text(localized("System")).tag("System")
                            Text("English").tag("en")
                            Text("简体中文").tag("zh-Hans")
                            Text("繁體中文").tag("zh-Hant")
                            Text("日本語").tag("ja")
                        }
                        .pickerStyle(MenuPickerStyle())
                        
                        Picker(localized("Theme"), selection: $theme) {
                            Text(localized("System")).tag("System")
                            Text(localized("Light")).tag("Light")
                            Text(localized("Dark")).tag("Dark")
                        }
                        .pickerStyle(SegmentedPickerStyle())
                    }
                    .padding(.bottom, 15)
                    
                    Section(header: Text(localized("Network Ports")).font(.headline)) {
                        TextField(localized("Discovery Port"), value: $discoveryPort, formatter: NumberFormatter())
                            .onChange(of: discoveryPort) { _ in restartNetworking() }
                        
                        Text(localized("Note: Port changes require network service restart."))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    
                    Section(header: Text(localized("System Permissions")).font(.headline).padding(.top, 15)) {
                        Text(localized("Please grant Automation permissions in System Settings -> Privacy & Security to allow CarpeCast to control playback."))
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    
                    Section(header: Text(localized("Recommended")).font(.headline).padding(.top, 15)) {
                        Link(localized("Download CarpeCast for Windows"), destination: URL(string: "https://github.com/jayfunc/CarpeCast")!)
                        Link(localized("Download BetterLyrics"), destination: URL(string: "https://github.com/jayfunc/BetterLyrics")!)
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
            }
            .frame(maxWidth: 500, alignment: .top)
            .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private func restartNetworking() {
        NetworkManager.shared.restartNetworking()
    }
    
    private func localized(_ key: String) -> String { getLocalizedString(key, language: language) }
}

struct ContentView: View {
    @AppStorage("language") private var language: String = "System"
    @AppStorage("theme") private var theme: String = "System"
    
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
        .preferredColorScheme(theme == "Light" ? .light : (theme == "Dark" ? .dark : nil))
    }
    
    private func localized(_ key: String) -> String { getLocalizedString(key, language: language) }
}

func getLocalizedString(_ key: String, language: String) -> String {
    let lang = language == "System" ? Locale.current.languageCode ?? "en" : language
    if lang.starts(with: "zh-Hant") || lang == "zh-HK" || lang == "zh-TW" {
        switch key {
        case "Now Playing": return "播放控制"
        case "Devices": return "設備狀態"
        case "Settings": return "設定"
        case "General": return "一般"
        case "Theme": return "主題"
        case "Device Name": return "設備名稱"
        case "Language": return "語言"
        case "Network Ports": return "網路通訊埠"
        case "Discovery Port": return "發現通訊埠"
        case "Note: Port changes require network service restart.": return "注意：修改通訊埠後需要重啟網路服務以生效。"
        case "Restart Network Service": return "重啟網路服務"
        case "Discovered Devices": return "已發現設備"
        case "Connected": return "已連接"
        case "Connect": return "連接"
        case "Disconnect": return "斷開"
        case "Waiting for Windows client to connect...": return "正在等待 Windows 端連接..."
        case "System Permissions": return "系統權限"
        case "Please grant Automation permissions in System Settings -> Privacy & Security to allow CarpeCast to control playback.": return "請在「系統設定 -> 隱私權與安全性 -> 自動化」中授予權限，以允許 CarpeCast 控制播放。"
        case "Recommended": return "推薦"
        case "Download CarpeCast for Windows": return "下載 Windows 版 CarpeCast"
        case "Download BetterLyrics": return "下載 BetterLyrics"
        case "Light": return "淺色"
        case "Dark": return "深色"
        case "System": return "跟隨系統"
        default: return key
        }
    } else if lang.starts(with: "zh") {
        switch key {
        case "Now Playing": return "播放控制"
        case "Devices": return "设备状态"
        case "Settings": return "设置"
        case "General": return "通用"
        case "Theme": return "主题"
        case "Device Name": return "设备名称"
        case "Language": return "语言"
        case "Network Ports": return "网络端口"
        case "Discovery Port": return "发现端口"
        case "Note: Port changes require network service restart.": return "注意：修改端口后需要重启网络服务以生效。"
        case "Restart Network Service": return "重启网络服务"
        case "Discovered Devices": return "已发现设备"
        case "Connected": return "已连接"
        case "Connect": return "连接"
        case "Disconnect": return "断开"
        case "Waiting for Windows client to connect...": return "正在等待 Windows 端连接..."
        case "System Permissions": return "系统权限"
        case "Please grant Automation permissions in System Settings -> Privacy & Security to allow CarpeCast to control playback.": return "请在“系统设置 -> 隐私与安全性 -> 自动化”中授予权限，以允许 CarpeCast 控制媒体播放。"
        case "Recommended": return "推荐板块"
        case "Download CarpeCast for Windows": return "下载 Windows 版 CarpeCast"
        case "Download BetterLyrics": return "下载 BetterLyrics"
        case "Light": return "浅色"
        case "Dark": return "深色"
        case "System": return "跟随系统"
        default: return key
        }
    } else if lang.starts(with: "ja") {
        switch key {
        case "Now Playing": return "再生コントロール"
        case "Devices": return "デバイスの状態"
        case "Settings": return "設定"
        case "General": return "一般"
        case "Theme": return "テーマ"
        case "Device Name": return "デバイス名"
        case "Language": return "言語"
        case "Network Ports": return "ネットワークポート"
        case "Discovery Port": return "検出ポート"
        case "Note: Port changes require network service restart.": return "注意：ポートを変更した後は、ネットワークサービスを再起動してください。"
        case "Restart Network Service": return "ネットワークサービスを再起動"
        case "Discovered Devices": return "検出されたデバイス"
        case "Connected": return "接続済み"
        case "Connect": return "接続"
        case "Disconnect": return "切断"
        case "Waiting for Windows client to connect...": return "Windowsクライアントの接続を待機中..."
        case "System Permissions": return "システム権限"
        case "Please grant Automation permissions in System Settings -> Privacy & Security to allow CarpeCast to control playback.": return "CarpeCastによる再生制御を許可するには、「システム設定 -> プライバシーとセキュリティ -> オートメーション」で権限を付与してください。"
        case "Recommended": return "おすすめ"
        case "Download CarpeCast for Windows": return "Windows版CarpeCastをダウンロード"
        case "Download BetterLyrics": return "BetterLyricsをダウンロード"
        case "Light": return "ライト"
        case "Dark": return "ダーク"
        case "System": return "システム"
        default: return key
        }
    }
    return key
}

@main
struct CarpeCastApp: App {
    @StateObject var networkManager = NetworkManager.shared
    @StateObject var mediaManager = MediaManager.shared
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
