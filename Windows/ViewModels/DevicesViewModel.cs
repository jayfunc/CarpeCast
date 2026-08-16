using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WindowsMediaReceiver.Models;
using WindowsMediaReceiver.Services;
using Microsoft.UI.Dispatching;

namespace WindowsMediaReceiver.ViewModels;

public partial class DevicesViewModel : ObservableObject
{
    private readonly INetworkService _networkService;
    private readonly DispatcherQueue _dispatcherQueue;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(NoDeviceVisibility))]
    public partial DeviceModel? CurrentDevice { get; set; }

    public Microsoft.UI.Xaml.Visibility NoDeviceVisibility => CurrentDevice == null ? Microsoft.UI.Xaml.Visibility.Visible : Microsoft.UI.Xaml.Visibility.Collapsed;

    public DevicesViewModel(INetworkService networkService)
    {
        _networkService = networkService;
        _dispatcherQueue = DispatcherQueue.GetForCurrentThread();

        _networkService.MediaStateReceived += NetworkService_MediaStateReceived;
    }

    private void NetworkService_MediaStateReceived(object? sender, MediaStateReceivedEventArgs e)
    {
        _dispatcherQueue.TryEnqueue(() =>
        {
            if (e.IsDisconnect)
            {
                CurrentDevice = null;
                return;
            }

            if (CurrentDevice == null || CurrentDevice.IPAddress != e.SenderIp)
            {
                CurrentDevice = new DeviceModel
                {
                    IPAddress = e.SenderIp,
                    DeviceName = e.State.RemoteDeviceName,
                    DeviceType = e.State.RemoteDeviceType,
                    OsVersion = e.State.RemoteOsVersion
                };
            }
            else
            {
                CurrentDevice.DeviceName = e.State.RemoteDeviceName;
                CurrentDevice.DeviceType = e.State.RemoteDeviceType;
                CurrentDevice.OsVersion = e.State.RemoteOsVersion;
            }
        });
    }

    [RelayCommand]
    private async Task Disconnect()
    {
        await _networkService.SendCommandAsync("DISCONNECT");
        CurrentDevice = null;
    }
}
