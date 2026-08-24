<p align="center">
  <img src="Windows/Assets/StoreLogo.scale-200.png" alt="CarpeCast logo" width="128">
</p>

<h1 align="center">CarpeCast</h1>

<p align="center">
  <a href="README.md">English</a> | <a href="README.zh-CN.md">简体中文</a>
</p>

CarpeCast is a two-part local-network application that syncs media playback from an Android device to Windows. The Windows app displays the currently playing track and its progress, and can remotely play, pause, skip to the previous track, or skip to the next track on Android.

## Download

- **Windows**: [Microsoft Store](https://apps.microsoft.com/detail/9PNM741WNTGZ)
- **Android**: [Google Play](https://play.google.com/store/apps/details?id=com.jayfunc.carpecast)

## Features

- Automatically discovers Windows receivers on the same local network
- Syncs title, artist, album, playback state, and progress
- Controls Android media sessions from Windows: play/pause, previous, and next
- Supports Windows System Media Transport Controls (SMTC)
- Lets Android users choose which media applications may be synced
- Provides device name, port, theme, and language settings on both platforms

## How It Works

1. The Windows receiver broadcasts its details over UDP every two seconds.
2. After discovering a receiver, Android reads authorized media sessions and sends playback state to it.
3. Windows displays the state and sends user playback commands back to Android over UDP.

All traffic stays on the local network; no cloud service is required.

## Getting Started

1. Connect the Android device and Windows PC to the same local network.
2. Start CarpeCast on Windows and leave it running.
3. Install and open CarpeCast on Android.
4. Follow the prompt to grant CarpeCast **Notification access** in Android system settings.
5. Select the discovered PC in the Android app's **Devices** page. Start playing media to see and control it from Windows.

If the devices are not discovered, make sure their discovery ports match and that Windows Firewall allows CarpeCast to use private networks.

## Default Ports

| Purpose | Default port | Description |
| --- | ---: | --- |
| Device discovery | UDP 5001 | Broadcast by Windows and received by Android |
| Media state | UDP 5000 | Sent from Android to Windows |
| Playback commands | Dynamic | Sent from Windows to Android (port is assigned dynamically) |

The Windows app can change its discovery and media-data ports. The Android app can change its discovery port. Keep the discovery ports aligned on both devices.

## Development and Build

### Android

**Requirements:** Android Studio (or Gradle 8.7), JDK 17, and Android SDK 35. The app supports Android 8.0 (API 26) and later.

Open the `Android` directory in Android Studio to run or build the app. To build from the command line:

```powershell
cd Android
gradle assembleDebug
```

The debug APK is written to `Android\app\build\outputs\apk\debug\app-debug.apk`.

### Windows

**Requirements:** Windows 10 version 1809 or later, .NET 10 SDK, and Visual Studio 2022 with the Windows App SDK workload.

Open `Windows\CarpeCast.slnx` in Visual Studio to run the app, or use:

```powershell
dotnet build Windows\CarpeCast.csproj
dotnet run --project Windows\CarpeCast.csproj
```

### Mac (Experimental)

**Requirements:** GitHub account (for cloud building) or a local macOS environment with `swiftc` and Python 3.

The Mac sender uses the private `MediaRemote` framework to globally capture track information from any player (Apple Music, Spotify, Chrome, etc.) and sends it to the Windows receiver over the local network. 

To build the executable without a Mac:
1. Push the `Mac` directory and the `.github` workflows to your GitHub repository.
2. GitHub Actions will automatically compile the Swift helper and package the Python script with a graphical UI using PyInstaller.
3. Download the `CarpeCast-Mac` executable from the **Actions** tab.

*Note: The Mac sender includes a GUI and will automatically discover Windows receivers on your local network via UDP. Mac users may need to grant execution permissions (`chmod +x`) upon first run.*

## Project Layout

```text
Android/    Android sender: discovers receivers, reads media sessions, and handles remote commands
Mac/        macOS sender (Experimental): globally captures media state
Windows/    WinUI 3 receiver: displays media state and sends playback commands
```

## Notes

- Android notification access is required to read media sessions from other apps.
- Some media applications may not expose complete metadata, album art, or playback controls.
- Avoid port conflicts with other applications. When changing a port, update the corresponding setting on the other device.

## License

CarpeCast is licensed under the [GNU General Public License v3.0](LICENSE).
