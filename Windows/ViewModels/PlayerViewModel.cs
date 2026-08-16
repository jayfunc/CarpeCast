using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WindowsMediaReceiver.Models;
using WindowsMediaReceiver.Services;
using Microsoft.UI.Dispatching;

namespace WindowsMediaReceiver.ViewModels;

public partial class PlayerViewModel : ObservableObject
{
    private readonly INetworkService _networkService;
    private readonly ISmtcService _smtcService;
    private readonly DispatcherQueue _dispatcherQueue;
    
    private Microsoft.UI.Xaml.DispatcherTimer _uiTimer;
    private DateTime _lastUpdateTime;
    private double _basePosition;

    [ObservableProperty]
    public partial MediaState Media { get; set; } = new();

    [ObservableProperty]
    public partial string FormattedPosition { get; set; } = "00:00";

    [ObservableProperty]
    public partial string FormattedDuration { get; set; } = "00:00";

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(WaitingVisibility))]
    [NotifyPropertyChangedFor(nameof(ConnectedVisibility))]
    public partial bool IsWaitingForConnection { get; set; } = true;

    [ObservableProperty]
    public partial string PlayPauseIcon { get; set; } = "\uE768"; // Play icon

    public Microsoft.UI.Xaml.Visibility WaitingVisibility => IsWaitingForConnection ? Microsoft.UI.Xaml.Visibility.Visible : Microsoft.UI.Xaml.Visibility.Collapsed;
    public Microsoft.UI.Xaml.Visibility ConnectedVisibility => !IsWaitingForConnection ? Microsoft.UI.Xaml.Visibility.Visible : Microsoft.UI.Xaml.Visibility.Collapsed;

    public PlayerViewModel(INetworkService networkService, ISmtcService smtcService)
    {
        _networkService = networkService;
        _smtcService = smtcService;
        _dispatcherQueue = DispatcherQueue.GetForCurrentThread();

        _networkService.MediaStateReceived += NetworkService_MediaStateReceived;

        _smtcService.PlayPressed += async (s, e) => await PlayPauseCommand.ExecuteAsync(null);
        _smtcService.PausePressed += async (s, e) => await PlayPauseCommand.ExecuteAsync(null);
        _smtcService.NextPressed += async (s, e) => await NextCommand.ExecuteAsync(null);
        _smtcService.PreviousPressed += async (s, e) => await PreviousCommand.ExecuteAsync(null);

        _uiTimer = new Microsoft.UI.Xaml.DispatcherTimer();
        _uiTimer.Interval = TimeSpan.FromMilliseconds(500);
        _uiTimer.Tick += UiTimer_Tick;
        _uiTimer.Start();
    }

    public void StartNetworking()
    {
        _networkService.StartListening();
        _smtcService.Initialize();
    }

    private void NetworkService_MediaStateReceived(object? sender, MediaStateReceivedEventArgs e)
    {
        _dispatcherQueue.TryEnqueue(() =>
        {
            if (e.IsDisconnect)
            {
                ResetState();
                return;
            }

            var newState = e.State;
            
            bool titleChanged = Media.Title != newState.Title;
            bool playStateChanged = Media.IsPlaying != newState.IsPlaying;

            Media = newState;
            IsWaitingForConnection = false;
            
            _lastUpdateTime = DateTime.Now;
            _basePosition = Media.Position;
            FormattedDuration = FormatTime(Media.Duration);
            PlayPauseIcon = Media.IsPlaying ? "\uE769" : "\uE768"; // Pause : Play

            _smtcService.UpdateMediaState(Media.Title, Media.Artist, Media.Album, Media.IsPlaying, Media.Position, Media.Duration);
        });
    }

    private void UiTimer_Tick(object? sender, object e)
    {
        if (Media.IsPlaying && Media.Duration > 0)
        {
            var elapsedMs = (DateTime.Now - _lastUpdateTime).TotalMilliseconds;
            var estimatedPosition = _basePosition + elapsedMs;
            if (estimatedPosition > Media.Duration) estimatedPosition = Media.Duration;

            Media.Position = estimatedPosition;
            FormattedPosition = FormatTime(estimatedPosition);
            
            _smtcService.UpdateTimeline(estimatedPosition, Media.Duration);
        }
    }

    private string FormatTime(double ms)
    {
        TimeSpan t = TimeSpan.FromMilliseconds(ms);
        if (t.Hours > 0)
            return t.ToString(@"hh\:mm\:ss");
        return t.ToString(@"mm\:ss");
    }

    [RelayCommand]
    private async Task PlayPause()
    {
        await _networkService.SendCommandAsync("TOGGLE_PLAY");
    }

    [RelayCommand]
    private async Task Next()
    {
        await _networkService.SendCommandAsync("NEXT");
    }

    [RelayCommand]
    private async Task Previous()
    {
        await _networkService.SendCommandAsync("PREV");
    }

    private void ResetState()
    {
        Media = new MediaState();
        IsWaitingForConnection = true;
        FormattedPosition = "00:00";
        FormattedDuration = "00:00";
        PlayPauseIcon = "\uE768";
        _smtcService.UpdateMediaState("", "", "", false, 0, 0);
    }
}
