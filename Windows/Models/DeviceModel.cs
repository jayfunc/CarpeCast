using System;
using CommunityToolkit.Mvvm.ComponentModel;

namespace CarpeCast.Models;

public partial class DeviceModel : ObservableObject
{
    public System.Net.IPEndPoint? Endpoint { get; set; }

    [ObservableProperty]
    public partial string IPAddress { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string DeviceName { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string DeviceType { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string OsVersion { get; set; } = string.Empty;

    public MediaState? LastMediaState { get; set; }
}
