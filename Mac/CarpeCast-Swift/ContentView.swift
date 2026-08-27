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
    var body: some View {
        VStack {
            Text("Settings")
                .font(.title)
            Text("Future configuration options will go here.")
                .foregroundColor(.secondary)
        }
        .padding()
    }
}

struct ContentView: View {
    var body: some View {
        TabView {
            PlayerView()
                .tabItem { Text("Now Playing") }
            DevicesView()
                .tabItem { Text("Devices") }
            SettingsView()
                .tabItem { Text("Settings") }
        }
        .frame(minWidth: 400, minHeight: 500)
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
