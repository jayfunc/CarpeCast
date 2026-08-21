using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CarpeCast.Models;
using CarpeCast.Services;
using Microsoft.UI.Dispatching;

namespace CarpeCast.ViewModels;

public partial class DevicesViewModel : ObservableObject
{
    private readonly INetworkService _networkService;
    private readonly DispatcherQueue _dispatcherQueue;

    public ObservableCollection<DeviceModel> Devices { get; } = new();

    public Microsoft.UI.Xaml.Visibility NoDeviceVisibility => Devices.Count == 0 ? Microsoft.UI.Xaml.Visibility.Visible : Microsoft.UI.Xaml.Visibility.Collapsed;

    public event EventHandler? ActiveDeviceChanged;

    [ObservableProperty]
    public partial DeviceModel? ActiveDevice { get; set; }

    partial void OnActiveDeviceChanged(DeviceModel? value)
    {
        _networkService.ActiveEndpoint = value?.Endpoint;
        ActiveDeviceChanged?.Invoke(this, EventArgs.Empty);
    }

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
            if (e.SenderEndpoint == null) return;

            var existingDevice = Devices.FirstOrDefault(d => d.Endpoint != null && d.Endpoint.Equals(e.SenderEndpoint));

            if (e.IsDisconnect)
            {
                if (existingDevice != null)
                {
                    if (ActiveDevice == existingDevice) ActiveDevice = null;
                    Devices.Remove(existingDevice);
                    OnPropertyChanged(nameof(NoDeviceVisibility));
                }
                return;
            }

            if (existingDevice == null)
            {
                var newDevice = new DeviceModel
                {
                    Endpoint = e.SenderEndpoint,
                    IPAddress = e.SenderIp,
                    DeviceName = e.State.RemoteDeviceName,
                    DeviceType = e.State.RemoteDeviceType,
                    OsVersion = e.State.RemoteOsVersion,
                    LastMediaState = e.State
                };
                Devices.Add(newDevice);
                OnPropertyChanged(nameof(NoDeviceVisibility));

                if (Devices.Count == 1 && _networkService.ActiveEndpoint == null)
                {
                    ActiveDevice = newDevice;
                }
            }
            else
            {
                existingDevice.DeviceName = e.State.RemoteDeviceName;
                existingDevice.DeviceType = e.State.RemoteDeviceType;
                existingDevice.OsVersion = e.State.RemoteOsVersion;
                existingDevice.LastMediaState = e.State;
            }
        });
    }

    [RelayCommand]
    private async Task DisconnectDevice(DeviceModel device)
    {
        if (device?.Endpoint != null)
        {
            await _networkService.SendCommandToEndpointAsync("DISCONNECT", device.Endpoint);
            if (ActiveDevice == device) ActiveDevice = null;
            Devices.Remove(device);
            OnPropertyChanged(nameof(NoDeviceVisibility));
        }
    }
}
