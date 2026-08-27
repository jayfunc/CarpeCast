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
    public ObservableCollection<DeviceModel> AvailableSenders { get; } = new();

    public Microsoft.UI.Xaml.Visibility NoDeviceVisibility => Devices.Count == 0 ? Microsoft.UI.Xaml.Visibility.Visible : Microsoft.UI.Xaml.Visibility.Collapsed;
    public Microsoft.UI.Xaml.Visibility NoAvailableSendersVisibility => AvailableSenders.Count == 0 ? Microsoft.UI.Xaml.Visibility.Visible : Microsoft.UI.Xaml.Visibility.Collapsed;

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
        _networkService.SenderDiscovered += NetworkService_SenderDiscovered;
        _networkService.SenderLost += NetworkService_SenderLost;
    }

    private void NetworkService_SenderDiscovered(object? sender, SenderDiscoveredEventArgs e)
    {
        _dispatcherQueue.TryEnqueue(() =>
        {
            var existing = AvailableSenders.FirstOrDefault(d => d.IPAddress == e.Sender.IPAddress);
            if (existing == null)
            {
                AvailableSenders.Add(e.Sender);
                OnPropertyChanged(nameof(NoAvailableSendersVisibility));
            }
        });
    }

    private void NetworkService_SenderLost(object? sender, SenderDiscoveredEventArgs e)
    {
        _dispatcherQueue.TryEnqueue(() =>
        {
            var existing = AvailableSenders.FirstOrDefault(d => d.IPAddress == e.Sender.IPAddress);
            if (existing != null)
            {
                AvailableSenders.Remove(existing);
                OnPropertyChanged(nameof(NoAvailableSendersVisibility));
            }
        });
    }

    [RelayCommand]
    private async Task ConnectToSender(DeviceModel device)
    {
        if (device != null && !string.IsNullOrEmpty(device.IPAddress))
        {
            var endpoint = new System.Net.IPEndPoint(System.Net.IPAddress.Parse(device.IPAddress), device.CommandPort);
            await _networkService.SendCommandToEndpointAsync("CONNECT_REQUEST", endpoint);
        }
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
                    CommandPort = e.CommandPort,
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
                existingDevice.CommandPort = e.CommandPort;
            }
        });
    }

    [RelayCommand]
    private async Task DisconnectDevice(DeviceModel device)
    {
        if (device != null && !string.IsNullOrEmpty(device.IPAddress))
        {
            var endpoint = new System.Net.IPEndPoint(System.Net.IPAddress.Parse(device.IPAddress), device.CommandPort);
            await _networkService.SendCommandToEndpointAsync("DISCONNECT_REQUEST", endpoint);
            
            if (ActiveDevice == device) ActiveDevice = null;
            Devices.Remove(device);
            OnPropertyChanged(nameof(NoDeviceVisibility));
        }
    }
}
