using System;
using CommunityToolkit.Mvvm.ComponentModel;

namespace WindowsMediaReceiver.Models;

public partial class DeviceModel : ObservableObject
{
    [ObservableProperty]
    public partial string IPAddress { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string DeviceName { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string DeviceType { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string OsVersion { get; set; } = string.Empty;

}
