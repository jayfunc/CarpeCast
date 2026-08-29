import SwiftUI
import AppKit

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
                                networkManager.disconnect(clearTarget: true)
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
    @AppStorage("senderDiscoveryPort") private var senderDiscoveryPort: Int = 5003
    @AppStorage("language") private var language: String = "System"
    @AppStorage("theme") private var theme: String = "System"
    @State private var isShowingAllowedSources = false

    var body: some View {
        if isShowingAllowedSources {
            AllowedSourcesView {
                isShowingAllowedSources = false
            }
        } else {
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
                        Text("v1.0.4 (\(BuildInfo.commitHash))")
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

                    Section(header: Text(localized("Playback Sources")).font(.headline)) {
                        Button {
                            isShowingAllowedSources = true
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(localized("Allowed Sources"))
                                Text(localized("Choose which apps can sync playback."))
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    .padding(.bottom, 15)

                    Section(header: Text(localized("Network Ports")).font(.headline)) {
                        TextField(localized("Discovery Port"), value: $discoveryPort, formatter: NumberFormatter())
                            .onChange(of: discoveryPort) { _ in restartNetworking() }
                        TextField(localized("Sender Discovery Port"), value: $senderDiscoveryPort, formatter: NumberFormatter())
                            .onChange(of: senderDiscoveryPort) { _ in restartNetworking() }

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
                .padding(.leading, 16)
                .padding(.trailing, 16)
                .frame(maxWidth: 500, alignment: .top)
                .padding(.bottom, 20)
            }
        }
    }
    }
    
    private func restartNetworking() {
        NetworkManager.shared.restartNetworking()
    }
    
    private func localized(_ key: String) -> String { getLocalizedString(key, language: language) }
}

private struct SourceApplication: Identifiable, Hashable {
    let bundleIdentifier: String
    let name: String

    var id: String { bundleIdentifier }
}

struct AllowedSourcesView: View {
    let onBack: () -> Void
    @AppStorage("allowAllSources") private var allowAllSources = true
    @AppStorage("language") private var language: String = "System"
    @State private var applications: [SourceApplication] = []
    @State private var selectedSources = Set(UserDefaults.standard.stringArray(forKey: "allowedSources") ?? [])
    @State private var searchText = ""

    private var filteredApplications: [SourceApplication] {
        guard !searchText.isEmpty else { return applications }
        return applications.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.bundleIdentifier.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button(action: onBack) {
                    Label(localized("Back"), systemImage: "chevron.left")
                }
                .buttonStyle(PlainButtonStyle())

                Text(localized("Allowed Sources"))
                    .font(.title2)
                    .fontWeight(.semibold)

                Spacer()

                Button(localized("Clear Selection"), action: clearSelection)
                    .disabled(allowAllSources || selectedSources.isEmpty)
            }

            Toggle(localized("Allow All Sources"), isOn: Binding(
                get: { allowAllSources },
                set: updateAllowAllSources
            ))
            Text(localized("When disabled, only selected apps can sync playback."))
                .font(.caption)
                .foregroundColor(.secondary)

            TextField(localized("Search applications"), text: $searchText)

            List(filteredApplications) { application in
                Toggle(application.name, isOn: Binding(
                    get: { allowAllSources || selectedSources.contains(application.bundleIdentifier) },
                    set: { isSelected in
                        updateSource(application.bundleIdentifier, isSelected: isSelected)
                    }
                ))
            }
        }
        .padding(24)
        .onAppear {
            applications = installedApplications()
        }
    }

    private func updateAllowAllSources(_ isEnabled: Bool) {
        allowAllSources = isEnabled
        selectedSources = isEnabled ? [] : Set(applications.map(\.bundleIdentifier))
        saveSelectedSources()
    }

    private func updateSource(_ bundleIdentifier: String, isSelected: Bool) {
        if allowAllSources {
            allowAllSources = false
            selectedSources = Set(applications.map(\.bundleIdentifier))
        }

        if isSelected {
            selectedSources.insert(bundleIdentifier)
        } else {
            selectedSources.remove(bundleIdentifier)
        }
        saveSelectedSources()
    }

    private func clearSelection() {
        allowAllSources = false
        selectedSources.removeAll()
        saveSelectedSources()
    }

    private func saveSelectedSources() {
        UserDefaults.standard.set(Array(selectedSources), forKey: "allowedSources")
    }

    private func installedApplications() -> [SourceApplication] {
        let fileManager = FileManager.default
        let applicationDirectories = [
            URL(fileURLWithPath: "/Applications"),
            URL(fileURLWithPath: "/System/Applications"),
            fileManager.homeDirectoryForCurrentUser.appendingPathComponent("Applications")
        ]

        var discovered = [String: SourceApplication]()
        for directory in applicationDirectories {
            guard let enumerator = fileManager.enumerator(
                at: directory,
                includingPropertiesForKeys: [.isDirectoryKey],
                options: [.skipsHiddenFiles, .skipsPackageDescendants]
            ) else {
                continue
            }

            for case let url as URL in enumerator where url.pathExtension == "app" {
                guard let bundle = Bundle(url: url),
                      let bundleIdentifier = bundle.bundleIdentifier else {
                    continue
                }
                let name = bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ??
                    bundle.object(forInfoDictionaryKey: "CFBundleName") as? String ??
                    url.deletingPathExtension().lastPathComponent
                discovered[bundleIdentifier] = SourceApplication(bundleIdentifier: bundleIdentifier, name: name)
            }
        }

        return discovered.values.sorted {
            $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
    }

    private func localized(_ key: String) -> String { getLocalizedString(key, language: language) }
}

struct ContentView: View {
    @AppStorage("language") private var language: String = "System"
    @AppStorage("theme") private var theme: String = "System"
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            PlayerView()
                .tabItem { Text(localized("Now Playing")) }
                .tag(0)
            DevicesView()
                .tabItem { Text(localized("Devices")) }
                .tag(1)
            SettingsView()
                .tabItem { Text(localized("Settings")) }
                .tag(2)
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
        case "Playback Sources": return "播放來源"
        case "Allowed Sources": return "允許的播放來源"
        case "Choose which apps can sync playback.": return "選擇可同步播放狀態的應用程式。"
        case "Allow All Sources": return "允許所有播放來源"
        case "When disabled, only selected apps can sync playback.": return "關閉後，只有已選擇的應用程式可以同步播放狀態。"
        case "Applications": return "應用程式"
        case "Search applications": return "搜尋應用程式"
        case "Clear Selection": return "清除選取"
        case "Back": return "返回"
        case "Theme": return "主題"
        case "Device Name": return "設備名稱"
        case "Language": return "語言"
        case "Network Ports": return "網路通訊埠"
        case "Discovery Port": return "發現通訊埠"
        case "Sender Discovery Port": return "發送端發現通訊埠"
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
        case "Playback Sources": return "播放源"
        case "Allowed Sources": return "允许的播放源"
        case "Choose which apps can sync playback.": return "选择可同步播放状态的应用。"
        case "Allow All Sources": return "允许所有播放源"
        case "When disabled, only selected apps can sync playback.": return "关闭后，只有选中的应用可以同步播放状态。"
        case "Applications": return "应用"
        case "Search applications": return "搜索应用"
        case "Clear Selection": return "清除选择"
        case "Back": return "返回"
        case "Theme": return "主题"
        case "Device Name": return "设备名称"
        case "Language": return "语言"
        case "Network Ports": return "网络端口"
        case "Discovery Port": return "发现端口"
        case "Sender Discovery Port": return "发送端发现端口"
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
        case "Playback Sources": return "再生ソース"
        case "Allowed Sources": return "許可された再生ソース"
        case "Choose which apps can sync playback.": return "再生状態を同期するアプリを選択します。"
        case "Allow All Sources": return "すべての再生ソースを許可"
        case "When disabled, only selected apps can sync playback.": return "無効にすると、選択したアプリだけが再生状態を同期できます。"
        case "Applications": return "アプリケーション"
        case "Search applications": return "アプリを検索"
        case "Clear Selection": return "選択を解除"
        case "Back": return "戻る"
        case "Theme": return "テーマ"
        case "Device Name": return "デバイス名"
        case "Language": return "言語"
        case "Network Ports": return "ネットワークポート"
        case "Discovery Port": return "検出ポート"
        case "Sender Discovery Port": return "送信側検出ポート"
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
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class AppDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate {
    var statusItem: NSStatusItem!
    weak var mainWindow: NSWindow?
    
    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        if let button = statusItem.button {
            let appIcon = NSImage(named: "AppIcon") ?? NSImage(named: NSImage.applicationIconName)
            if let icon = appIcon?.copy() as? NSImage {
                icon.size = NSSize(width: 18, height: 18)
                button.image = icon
            } else {
                button.image = NSImage(systemSymbolName: "play.circle", accessibilityDescription: "CarpeCast")
                button.image?.isTemplate = true
            }
        }
        
        let menu = NSMenu()
        let showItem = NSMenuItem(title: "CarpeCast", action: #selector(showWindow), keyEquivalent: "")
        showItem.target = self
        menu.addItem(showItem)
        menu.addItem(NSMenuItem.separator())
        let quitItem = NSMenuItem(title: "Quit", action: #selector(quitApp), keyEquivalent: "q")
        quitItem.target = self
        menu.addItem(quitItem)
        statusItem.menu = menu
        
        DispatchQueue.main.async {
            if let window = NSApp.windows.first(where: { $0.className.contains("AppKitWindow") }) {
                self.mainWindow = window
                window.delegate = self
            }
        }
    }
    
    @objc func showWindow() {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
        mainWindow?.makeKeyAndOrderFront(nil)
    }
    
    @objc func quitApp() {
        NSApplication.shared.terminate(nil)
    }
    
    func windowShouldClose(_ sender: NSWindow) -> Bool {
        sender.orderOut(nil)
        NSApp.setActivationPolicy(.accessory)
        return false
    }
    
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        if !flag {
            showWindow()
        }
        return true
    }
}
