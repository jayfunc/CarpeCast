using System;
using System.Threading.Tasks;
using CarpeCast.Models;

namespace CarpeCast.Services;

public class MediaStateReceivedEventArgs : EventArgs
{
    public MediaState State { get; set; } = new();
    public int CommandPort { get; set; }
    public string SenderIp { get; set; } = string.Empty;
    public System.Net.IPEndPoint? SenderEndpoint { get; set; }
    public bool IsDisconnect { get; set; } = false;
}

public interface INetworkService
{
    event EventHandler<MediaStateReceivedEventArgs> MediaStateReceived;
    
    System.Net.IPEndPoint? ActiveEndpoint { get; set; }

    void StartListening();
    void StopListening();
    Task SendCommandAsync(string command);
    Task SendCommandToEndpointAsync(string command, System.Net.IPEndPoint endpoint);
    void DisconnectLocal();
}
