using System;
using System.Threading.Tasks;
using WindowsMediaReceiver.Models;

namespace WindowsMediaReceiver.Services;

public class MediaStateReceivedEventArgs : EventArgs
{
    public MediaState State { get; set; } = new();
    public int CommandPort { get; set; }
    public string SenderIp { get; set; } = string.Empty;
    public bool IsDisconnect { get; set; } = false;
}

public interface INetworkService
{
    event EventHandler<MediaStateReceivedEventArgs> MediaStateReceived;

    void StartListening();
    void StopListening();
    Task SendCommandAsync(string command);
    void DisconnectLocal();
}
