import SwiftUI
import AppKit

struct PlayerView: View {
    @ObservedObject var mediaManager = MediaManager.shared
    @State private var isDragging = false
    @State private var dragPosition: Double = 0.0
    
    var body: some View {
        ZStack {
            // Background Image
            GeometryReader { geo in
                if let base64 = mediaManager.albumArtBase64.components(separatedBy: ",").last,
                   let data = Data(base64Encoded: base64, options: .ignoreUnknownCharacters),
                   let nsImage = NSImage(data: data) {
                    Image(nsImage: nsImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                        .mask(
                            LinearGradient(
                                gradient: Gradient(colors: [Color.white, Color.white, Color.clear]),
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .opacity(0.8)
                } else {
                    VStack {
                        Image(systemName: "music.note")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 100, height: 100)
                            .foregroundColor(.gray.opacity(0.3))
                    }
                    .frame(width: geo.size.width, height: geo.size.height)
                }
            }
            .edgesIgnoringSafeArea(.all)
            
            VStack {
                Spacer()
                
                VStack(alignment: .leading, spacing: 5) {
                    if let sourceApplication {
                        Button(action: {
                            NSWorkspace.shared.open(sourceApplication.url)
                        }) {
                            HStack(spacing: 6) {
                                Image(nsImage: sourceApplication.icon)
                                    .resizable()
                                    .frame(width: 16, height: 16)
                                Text(sourceApplication.name)
                                    .font(.caption)
                                    .fontWeight(.semibold)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color(NSColor.windowBackgroundColor).opacity(0.6))
                            .clipShape(Capsule())
                        }
                        .buttonStyle(PlainButtonStyle())
                        .padding(.bottom, 11)
                    }

                    Text(mediaManager.title.isEmpty ? "Not Playing" : mediaManager.title)
                        .font(.title)
                        .fontWeight(.bold)
                        .lineLimit(1)
                    
                    Text(mediaManager.artist.isEmpty ? "Artist" : mediaManager.artist)
                        .font(.title3)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                    
                    Text(mediaManager.album.isEmpty ? "Album" : mediaManager.album)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 30)
                
                // Progress Slider
                VStack(spacing: 5) {
                    Slider(
                        value: Binding(
                            get: { self.isDragging ? self.dragPosition : self.mediaManager.position },
                            set: { newValue in
                                self.dragPosition = newValue
                            }
                        ),
                        in: 0...max(mediaManager.duration, 1),
                        onEditingChanged: { editing in
                            self.isDragging = editing
                            if !editing {
                                mediaManager.sendCommand("SEEK:\(self.dragPosition)")
                            }
                        }
                    )
                    
                    HStack {
                        Text(formatTime(ms: isDragging ? dragPosition : mediaManager.position))
                        Spacer()
                        Text(formatTime(ms: mediaManager.duration))
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                }
                .padding(.horizontal, 30)
                .padding(.vertical, 10)
                
                // Controls
                HStack(spacing: 40) {
                    Button(action: { mediaManager.sendCommand("PREV") }) {
                        Image(systemName: "backward.fill").font(.title2)
                    }.buttonStyle(PlainButtonStyle())
                    
                    Button(action: { mediaManager.sendCommand("PLAY") }) { // TOGGLE
                        Image(systemName: mediaManager.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 50))
                    }.buttonStyle(PlainButtonStyle())
                    
                    Button(action: { mediaManager.sendCommand("NEXT") }) {
                        Image(systemName: "forward.fill").font(.title2)
                    }.buttonStyle(PlainButtonStyle())
                }
                .padding(.bottom, 40)
            }
        }
    }
    
    private func formatTime(ms: Double) -> String {
        let totalSeconds = Int(ms / 1000)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%d:%02d", minutes, seconds)
    }

    private var sourceApplication: (name: String, icon: NSImage, url: URL)? {
        guard let bundleIdentifier = mediaManager.sourceBundleIdentifier,
              let url = NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleIdentifier),
              let bundle = Bundle(url: url) else {
            return nil
        }

        let name = bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ??
            bundle.object(forInfoDictionaryKey: "CFBundleName") as? String ??
            url.deletingPathExtension().lastPathComponent
        return (name, NSWorkspace.shared.icon(forFile: url.path), url)
    }
}
