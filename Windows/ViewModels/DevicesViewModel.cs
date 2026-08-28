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
    public ObservableCollection<DeviceModel> ConnectedDevices { get; } = new();

    private void UpdateConnectedDevices()
    {
        var connected = Devices.Where(d => d.IsConnected).ToList();
        
        var toRemove = ConnectedDevices.Where(d => !connected.Contains(d)).ToList();
        foreach(var item in toRemove) ConnectedDevices.Remove(item);

        foreach(var item in connected)
        {
            if(!ConnectedDevices.Contains(item)) ConnectedDevices.Add(item);
        }
    }

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
        _networkService.SenderDiscovered += NetworkService_SenderDiscovered;
        _networkService.SenderLost += NetworkService_SenderLost;
    }

    private string? TargetConnectedDeviceIP
    {
        get => Windows.Storage.ApplicationData.Current.LocalSettings.Values["TargetConnectedDeviceIP"] as string;
        set
        {
            if (value == null)
                Windows.Storage.ApplicationData.Current.LocalSettings.Values.Remove("TargetConnectedDeviceIP");
            else
                Windows.Storage.ApplicationData.Current.LocalSettings.Values["TargetConnectedDeviceIP"] = value;
        }
    }

    private void NetworkService_SenderDiscovered(object? sender, SenderDiscoveredEventArgs e)
    {
        _dispatcherQueue.TryEnqueue(async () =>
        {
            var existing = Devices.FirstOrDefault(d => d.IPAddress == e.Sender.IPAddress);
            if (existing == null)
            {
                e.Sender.IsConnected = false;
                Devices.Add(e.Sender);
                OnPropertyChanged(nameof(NoDeviceVisibility));
                UpdateConnectedDevices();
            }
            else
            {
                existing.DeviceName = e.Sender.DeviceName;
                existing.DeviceType = e.Sender.DeviceType;
                existing.OsVersion = e.Sender.OsVersion;
                existing.CommandPort = e.Sender.CommandPort;
                UpdateConnectedDevices();
            }

            if (ActiveDevice == null && TargetConnectedDeviceIP == e.Sender.IPAddress)
            {
                await ConnectToSender(e.Sender);
            }
        });
    }

    private void NetworkService_SenderLost(object? sender, SenderDiscoveredEventArgs e)
    {
        _dispatcherQueue.TryEnqueue(() =>
        {
            var existing = Devices.FirstOrDefault(d => d.IPAddress == e.Sender.IPAddress);
            if (existing != null)
            {
                if (ActiveDevice == existing) ActiveDevice = null;
                Devices.Remove(existing);
                OnPropertyChanged(nameof(NoDeviceVisibility));
                UpdateConnectedDevices();
            }
        });
    }

    [RelayCommand]
    private async Task ConnectToSender(DeviceModel device)
    {
        if (device != null && !string.IsNullOrEmpty(device.IPAddress))
        {
            TargetConnectedDeviceIP = device.IPAddress;
            var endpoint = new System.Net.IPEndPoint(System.Net.IPAddress.Parse(device.IPAddress), device.CommandPort);
            await _networkService.SendCommandToEndpointAsync("CONNECT_REQUEST", endpoint);
        }
    }

    private void NetworkService_MediaStateReceived(object? sender, MediaStateReceivedEventArgs e)
    {
        _dispatcherQueue.TryEnqueue(() =>
        {
            if (e.SenderEndpoint == null) return;

            var existingDevice = Devices.FirstOrDefault(d => d.IPAddress == e.SenderIp);

            if (e.IsDisconnect)
            {
                if (existingDevice != null)
                {
                    existingDevice.IsConnected = false;
                    existingDevice.LastMediaState = null;
                    if (ActiveDevice == existingDevice)
                    {
                        ActiveDevice = null;
                        TargetConnectedDeviceIP = null;
                    }
                    UpdateConnectedDevices();
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
                    LastMediaState = e.State,
                    IsConnected = true
                };
                Devices.Add(newDevice);
                OnPropertyChanged(nameof(NoDeviceVisibility));
                UpdateConnectedDevices();

                if (ConnectedDevices.Count == 1 && _networkService.ActiveEndpoint == null)
                {
                    ActiveDevice = newDevice;
                }
            }
            else
            {
                existingDevice.IsConnected = true;
                existingDevice.Endpoint = e.SenderEndpoint;
                existingDevice.DeviceName = e.State.RemoteDeviceName;
                existingDevice.DeviceType = e.State.RemoteDeviceType;
                existingDevice.OsVersion = e.State.RemoteOsVersion;
                existingDevice.LastMediaState = e.State;
                existingDevice.CommandPort = e.CommandPort;
                UpdateConnectedDevices();
                
                if (ActiveDevice == existingDevice)
                {
                    _networkService.ActiveEndpoint = e.SenderEndpoint;
                }
                else if (ActiveDevice == null) 
                {
                    ActiveDevice = existingDevice;
                }
            }
        });
    }

    [RelayCommand]
    private async Task DisconnectDevice(DeviceModel device)
    {
        if (device != null && !string.IsNullOrEmpty(device.IPAddress))
        {
            TargetConnectedDeviceIP = null;
            var endpoint = new System.Net.IPEndPoint(System.Net.IPAddress.Parse(device.IPAddress), device.CommandPort);
            await _networkService.SendCommandToEndpointAsync("DISCONNECT_REQUEST", endpoint);
            
            device.IsConnected = false;
            UpdateConnectedDevices();
            if (ActiveDevice == device)
            {
                ActiveDevice = null;
            }
        }
    }
}
