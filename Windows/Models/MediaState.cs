using CommunityToolkit.Mvvm.ComponentModel;

namespace CarpeCast.Models;

public partial class MediaState : ObservableObject
{
    [ObservableProperty]
    public partial string Title { get; set; } = "No Media Playing";

    [ObservableProperty]
    public partial string Artist { get; set; } = "Unknown Artist";

    [ObservableProperty]
    public partial string Album { get; set; } = "Unknown Album";

    [ObservableProperty]
    public partial bool IsPlaying { get; set; } = false;

    [ObservableProperty]
    public partial double Position { get; set; } = 0;

    [ObservableProperty]
    public partial double Duration { get; set; } = 0;

    [ObservableProperty]
    public partial string RemoteDeviceName { get; set; } = "Device";

    [ObservableProperty]
    public partial string RemoteDeviceType { get; set; } = "Phone";

    [ObservableProperty]
    public partial string RemoteOsVersion { get; set; } = "Android";
}
