using System;
using CommunityToolkit.Mvvm.ComponentModel;

namespace CarpeCast.Models;

public partial class DeviceModel : ObservableObject
{
    public System.Net.IPEndPoint? Endpoint { get; set; }

    [ObservableProperty]
    public partial string IPAddress { get; set; } = string.Empty;

    public int CommandPort { get; set; }
    public DateTime LastSeen { get; set; } = DateTime.Now;

    [ObservableProperty]
    public partial string DeviceName { get; set; } = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IconGlyph))]
    public partial string DeviceType { get; set; } = string.Empty;

    public string IconGlyph
    {
        get
        {
            if (string.IsNullOrEmpty(DeviceType)) return "\uE8EA";
            if (DeviceType.Contains("Desktop", StringComparison.OrdinalIgnoreCase)) return "\uE977";
            if (DeviceType.Contains("Tablet", StringComparison.OrdinalIgnoreCase)) return "\uE70A";
            return "\uE8EA";
        }
    }

    [ObservableProperty]
    public partial string OsVersion { get; set; } = string.Empty;

    public MediaState? LastMediaState { get; set; }

    [ObservableProperty]
    public partial bool IsConnected { get; set; } = false;
}
