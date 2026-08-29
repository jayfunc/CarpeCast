using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using CarpeCast.Models;

namespace CarpeCast.Services;

public class NetworkService : INetworkService
{
    private sealed class AlbumArtTransfer
    {
        public int ChunkCount { get; }
        public string?[] Chunks { get; }

        public AlbumArtTransfer(int chunkCount)
        {
            ChunkCount = chunkCount;
            Chunks = new string?[chunkCount];
        }

        public bool AddChunk(int index, string chunk)
        {
            Chunks[index] = chunk;
            return Chunks.All(part => part != null);
        }

        public string GetAlbumArt() => string.Concat(Chunks!);
    }

    private readonly ISettingsService _settings;
    private CancellationTokenSource? _cts;
    private UdpClient? _dataClient;
    private UdpClient? _discoveryClient;
    private readonly System.Collections.Concurrent.ConcurrentDictionary<string, DeviceModel> _senderCache = new();
    private readonly System.Collections.Concurrent.ConcurrentDictionary<string, AlbumArtTransfer> _albumArtTransfers = new();

    public IPEndPoint? ActiveEndpoint { get; set; }

    public event EventHandler<MediaStateReceivedEventArgs>? MediaStateReceived;
    public event EventHandler<SenderDiscoveredEventArgs>? SenderDiscovered;
    public event EventHandler<SenderDiscoveredEventArgs>? SenderLost;

    public NetworkService(ISettingsService settings)
    {
        _settings = settings;
    }

    public void StartListening()
    {
        StopListening();
        _cts = new CancellationTokenSource();

        StartBroadcastTask(_cts.Token);
        StartDataListenerTask(_cts.Token);
        StartDiscoveryListenerTask(_cts.Token);
    }

    public void StopListening()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        _cts = null;

        _dataClient?.Close();
        _dataClient?.Dispose();
        _dataClient = null;

        _discoveryClient?.Close();
        _discoveryClient?.Dispose();
        _discoveryClient = null;
    }

    private int _dataPort = 0;

    private void StartBroadcastTask(CancellationToken token)
    {
        Task.Run(async () =>
        {
            var osVersion = Environment.OSVersion.Version;
            string osName = osVersion.Build >= 22000 ? "Windows 11" : "Windows 10";
            
            while (!token.IsCancellationRequested)
            {
                if (_dataPort > 0)
                {
                    byte[] message = Encoding.UTF8.GetBytes($"CARPECAST_RECEIVER:{_settings.DeviceName}:{_dataPort}:Desktop:{osName}");

                    foreach (var ni in System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces())
                    {
                        if (ni.OperationalStatus == System.Net.NetworkInformation.OperationalStatus.Up &&
                            ni.NetworkInterfaceType != System.Net.NetworkInformation.NetworkInterfaceType.Loopback)
                        {
                            foreach (var ip in ni.GetIPProperties().UnicastAddresses)
                            {
                                if (ip.Address.AddressFamily == AddressFamily.InterNetwork)
                                {
                                    try
                                    {
                                        using (var client = new UdpClient(new IPEndPoint(ip.Address, 0)))
                                        {
                                            client.EnableBroadcast = true;
                                            var endpoint = new IPEndPoint(IPAddress.Broadcast, _settings.DiscoveryPort);
                                            await client.SendAsync(message, message.Length, endpoint);
                                        }
                                    }
                                    catch { }
                                }
                            }
                        }
                    }
                }
                await Task.Delay(2000, token);
            }
        }, token);
    }

    private void StartDataListenerTask(CancellationToken token)
    {
        Task.Run(async () =>
        {
            _dataClient = new UdpClient(0);
            _dataPort = ((IPEndPoint)_dataClient.Client.LocalEndPoint!).Port;
            while (!token.IsCancellationRequested)
            {
                try
                {
                    var result = await _dataClient.ReceiveAsync();
                    var incomingEndpoint = result.RemoteEndPoint;

                    string jsonString = Encoding.UTF8.GetString(result.Buffer);
                    using var document = JsonDocument.Parse(jsonString);

                    if (document.RootElement.TryGetProperty("command", out var cmdProp) && cmdProp.GetString() == "DISCONNECT")
                    {
                        MediaStateReceived?.Invoke(this, new MediaStateReceivedEventArgs
                        {
                            State = new MediaState(),
                            SenderIp = incomingEndpoint.Address.ToString(),
                            SenderEndpoint = incomingEndpoint,
                            IsDisconnect = true
                        });
                        continue;
                    }

                    string? albumArt = document.RootElement.TryGetProperty("albumArt", out var artbProp)
                        ? artbProp.GetString()
                        : null;

                    if (document.RootElement.TryGetProperty("albumArtTransferId", out var transferIdProp) &&
                        document.RootElement.TryGetProperty("albumArtChunkIndex", out var chunkIndexProp) &&
                        document.RootElement.TryGetProperty("albumArtChunkCount", out var chunkCountProp) &&
                        document.RootElement.TryGetProperty("albumArtChunk", out var chunkProp))
                    {
                        var transferId = transferIdProp.GetString();
                        var chunk = chunkProp.GetString();
                        var chunkIndex = chunkIndexProp.GetInt32();
                        var chunkCount = chunkCountProp.GetInt32();

                        if (!string.IsNullOrEmpty(transferId) &&
                            chunk != null &&
                            chunkCount > 0 &&
                            chunkCount <= 1_000 &&
                            chunkIndex >= 0 &&
                            chunkIndex < chunkCount)
                        {
                            string transferKey = $"{incomingEndpoint.Address}:{transferId}";
                            var transfer = _albumArtTransfers.AddOrUpdate(
                                transferKey,
                                _ => new AlbumArtTransfer(chunkCount),
                                (_, existing) => existing.ChunkCount == chunkCount ? existing : new AlbumArtTransfer(chunkCount));

                            if (transfer.AddChunk(chunkIndex, chunk))
                            {
                                albumArt = transfer.GetAlbumArt();
                                _albumArtTransfers.TryRemove(transferKey, out _);
                            }
                        }
                    }

                    var state = new MediaState
                    {
                        Title = document.RootElement.TryGetProperty("title", out var titleProp) ? titleProp.GetString() ?? "Unknown" : "Unknown",
                        Artist = document.RootElement.TryGetProperty("artist", out var artProp) ? artProp.GetString() ?? "Unknown" : "Unknown",
                        Album = document.RootElement.TryGetProperty("album", out var albProp) ? albProp.GetString() ?? "" : "",
                        AlbumArtBase64 = albumArt,
                        IsPlaying = document.RootElement.TryGetProperty("isPlaying", out var pProp) && pProp.GetBoolean(),
                        Position = document.RootElement.TryGetProperty("position", out var posProp) ? posProp.GetDouble() : 0.0,
                        Duration = document.RootElement.TryGetProperty("duration", out var durProp) ? durProp.GetDouble() : 0.0,
                        RemoteDeviceName = document.RootElement.TryGetProperty("deviceName", out var dNameProp) ? dNameProp.GetString() ?? "Device" : "Device",
                        RemoteDeviceType = document.RootElement.TryGetProperty("deviceType", out var dTypeProp) ? dTypeProp.GetString() ?? "Phone" : "Phone",
                        RemoteOsVersion = document.RootElement.TryGetProperty("osVersion", out var osProp) ? osProp.GetString() ?? "Android" : "Android"
                    };

                    int cmdPort = 5002;
                    if (document.RootElement.TryGetProperty("commandPort", out var cmdPortProp))
                    {
                        cmdPort = cmdPortProp.GetInt32();
                    }

                    MediaStateReceived?.Invoke(this, new MediaStateReceivedEventArgs
                    {
                        State = state,
                        CommandPort = cmdPort,
                        SenderIp = result.RemoteEndPoint.Address.ToString(),
                        SenderEndpoint = incomingEndpoint
                    });
                }
                catch
                {
                    // Ignore bad packets or task cancellation
                }
            }
        }, token);
    }

    private void StartDiscoveryListenerTask(CancellationToken token)
    {
        Task.Run(async () =>
        {
            try
            {
                _discoveryClient = new UdpClient(_settings.SenderDiscoveryPort);
                while (!token.IsCancellationRequested)

                {
                    var result = await _discoveryClient.ReceiveAsync();
                    string message = Encoding.UTF8.GetString(result.Buffer);
                    var parts = message.Split(':');
                    if (parts.Length >= 5 && parts[0] == "CARPECAST_SENDER")
                    {
                        string ip = result.RemoteEndPoint.Address.ToString();
                        string name = parts[1];
                        if (int.TryParse(parts[2], out int commandPort))
                        {
                            string type = parts[3];
                            string os = parts[4];
                            
                            var device = new DeviceModel
                            {
                                IPAddress = ip,
                                DeviceName = name,
                                CommandPort = commandPort,
                                DeviceType = type,
                                OsVersion = os,
                                LastSeen = DateTime.Now
                            };

                            bool isNew = !_senderCache.ContainsKey(ip);
                            _senderCache[ip] = device;

                            if (isNew)
                            {
                                SenderDiscovered?.Invoke(this, new SenderDiscoveredEventArgs { Sender = device });
                            }
                        }
                    }
                }
            }
            catch { }
        }, token);

        Task.Run(async () =>
        {
            while (!token.IsCancellationRequested)
            {
                var now = DateTime.Now;
                foreach (var kvp in _senderCache)
                {
                    if ((now - kvp.Value.LastSeen).TotalSeconds > 5)
                    {
                        if (_senderCache.TryRemove(kvp.Key, out var removedDevice))
                        {
                            SenderLost?.Invoke(this, new SenderDiscoveredEventArgs { Sender = removedDevice });
                        }
                    }
                }
                await Task.Delay(1000, token);
            }
        }, token);
    }

    public async Task SendCommandAsync(string command)
    {
        if (ActiveEndpoint != null)
        {
            await SendCommandToEndpointAsync(command, ActiveEndpoint);
        }
    }

    public async Task SendSeekAsync(long positionMs)
    {
        await SendCommandAsync($"SEEK:{positionMs}");
    }

    public async Task SendCommandToEndpointAsync(string command, IPEndPoint endpoint)
    {
        if (_dataClient != null)
        {
            try
            {
                byte[] data = Encoding.UTF8.GetBytes(command);
                await _dataClient.SendAsync(data, data.Length, endpoint);
            }
            catch { }
        }
    }

    public void DisconnectLocal()
    {
        MediaStateReceived?.Invoke(this, new MediaStateReceivedEventArgs
        {
            State = new MediaState(),
            IsDisconnect = true
        });
    }
}
