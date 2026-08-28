using System;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CarpeCast.Models;
using CarpeCast.Services;
using Microsoft.UI.Dispatching;

namespace CarpeCast.ViewModels;

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
    public partial string DisplayTitle { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string DisplayArtist { get; set; } = string.Empty;

    [ObservableProperty]
    public partial string DisplayAlbum { get; set; } = string.Empty;

    [ObservableProperty]
    public partial double CurrentPosition { get; set; }

    [ObservableProperty]
    public partial double CurrentDuration { get; set; }

    [ObservableProperty]
    public partial string FormattedPosition { get; set; } = "00:00";

    [ObservableProperty]
    public partial string FormattedDuration { get; set; } = "00:00";

    [ObservableProperty]
    public partial string PlayPauseIcon { get; set; } = "\uE768"; // Play icon

    [ObservableProperty]
    public partial Microsoft.UI.Xaml.Media.Imaging.BitmapImage? AlbumArtImage { get; set; }

    partial void OnAlbumArtImageChanged(Microsoft.UI.Xaml.Media.Imaging.BitmapImage? value)
    {
        OnPropertyChanged(nameof(NoAlbumArtVisibility));
    }

    public Microsoft.UI.Xaml.Visibility NoAlbumArtVisibility => AlbumArtImage == null ? Microsoft.UI.Xaml.Visibility.Visible : Microsoft.UI.Xaml.Visibility.Collapsed;

    public DevicesViewModel DevicesVM { get; }

    public PlayerViewModel(INetworkService networkService, ISmtcService smtcService, DevicesViewModel devicesVm)
    {
        _networkService = networkService;
        _smtcService = smtcService;
        DevicesVM = devicesVm;
        _dispatcherQueue = DispatcherQueue.GetForCurrentThread();

        _networkService.MediaStateReceived += NetworkService_MediaStateReceived;

        _smtcService.PlayPressed += (s, e) => _dispatcherQueue.TryEnqueue(() => PlayPauseCommand.Execute(null));
        _smtcService.PausePressed += (s, e) => _dispatcherQueue.TryEnqueue(() => PlayPauseCommand.Execute(null));
        _smtcService.NextPressed += (s, e) => _dispatcherQueue.TryEnqueue(() => NextCommand.Execute(null));
        _smtcService.PreviousPressed += (s, e) => _dispatcherQueue.TryEnqueue(() => PreviousCommand.Execute(null));
        _smtcService.SeekRequested += (s, position) => _dispatcherQueue.TryEnqueue(() => SeekCommand.Execute(position));

        DevicesVM.ActiveDeviceChanged += DevicesVM_ActiveDeviceChanged;

        _uiTimer = new Microsoft.UI.Xaml.DispatcherTimer();
        _uiTimer.Interval = TimeSpan.FromMilliseconds(500);
        _uiTimer.Tick += UiTimer_Tick;
        _uiTimer.Start();

        ResetState();
    }

    private void DevicesVM_ActiveDeviceChanged(object? sender, EventArgs e)
    {
        _dispatcherQueue.TryEnqueue(() =>
        {
            _smtcService.ClearMediaState();

            if (DevicesVM.ActiveDevice?.LastMediaState != null)
            {
                var fakeArgs = new MediaStateReceivedEventArgs
                {
                    State = DevicesVM.ActiveDevice.LastMediaState,
                    SenderEndpoint = DevicesVM.ActiveDevice.Endpoint,
                    SenderIp = DevicesVM.ActiveDevice.IPAddress
                };
                NetworkService_MediaStateReceived(this, fakeArgs);
            }
            else
            {
                ResetState();
            }
        });
    }

    private bool _isNetworkingStarted;

    public void StartNetworking()
    {
        if (_isNetworkingStarted) return;
        _isNetworkingStarted = true;
        
        _networkService.StartListening();
        _smtcService.Initialize();
    }

    private void NetworkService_MediaStateReceived(object? sender, MediaStateReceivedEventArgs e)
    {
        _dispatcherQueue.TryEnqueue(() =>
        {
            if (e.SenderEndpoint == null || _networkService.ActiveEndpoint == null) return;
            if (!e.SenderEndpoint.Equals(_networkService.ActiveEndpoint)) return;

            if (e.IsDisconnect)
            {
                ResetState();
                _networkService.ActiveEndpoint = null;
                return;
            }

            var newState = e.State;

            bool hasMedia = !string.IsNullOrWhiteSpace(newState.Title) && newState.Title != "Unknown";

            var loader = new Microsoft.Windows.ApplicationModel.Resources.ResourceLoader();

            DisplayTitle = hasMedia ? newState.Title : (loader.GetString("NoMediaPlaying/Text") ?? "No Media Playing");
            
            DisplayArtist = !string.IsNullOrWhiteSpace(newState.Artist) && newState.Artist != "Unknown" 
                ? newState.Artist 
                : (loader.GetString("UnknownArtist/Text") ?? "Unknown Artist");

            DisplayAlbum = !string.IsNullOrWhiteSpace(newState.Album) && newState.Album != "Unknown"
                ? newState.Album 
                : (loader.GetString("UnknownAlbum/Text") ?? "Unknown Album");
            
            bool titleChanged = Media.Title != newState.Title;
            bool playStateChanged = Media.IsPlaying != newState.IsPlaying;

            bool albumArtChanged = Media.AlbumArtBase64 != newState.AlbumArtBase64;

            Media = newState;
            
            _lastUpdateTime = DateTime.Now;
            _basePosition = Media.Position;
            CurrentDuration = Media.Duration;
            CurrentPosition = _basePosition;
            FormattedDuration = FormatTime(Media.Duration);
            FormattedPosition = FormatTime(_basePosition);
            PlayPauseIcon = Media.IsPlaying ? "\uE769" : "\uE768"; // Pause : Play

            if (albumArtChanged)
            {
                if (!string.IsNullOrEmpty(newState.AlbumArtBase64))
                {
                    try
                    {
                        var bytes = Convert.FromBase64String(newState.AlbumArtBase64);
                        using var stream = new Windows.Storage.Streams.InMemoryRandomAccessStream();
                        using var writer = new Windows.Storage.Streams.DataWriter(stream.GetOutputStreamAt(0));
                        writer.WriteBytes(bytes);
                        writer.StoreAsync().AsTask().Wait();
                        
                        var img = new Microsoft.UI.Xaml.Media.Imaging.BitmapImage();
                        stream.Seek(0);
                        img.SetSource(stream);
                        AlbumArtImage = img;
                    }
                    catch { AlbumArtImage = null; }
                }
                else
                {
                    AlbumArtImage = null;
                }
            }

            if (hasMedia)
            {
                _smtcService.UpdateMediaState(Media.Title, Media.Artist, Media.Album, Media.IsPlaying, Media.Position, Media.Duration, Media.AlbumArtBase64);
                _smtcService.UpdateTimeline(CurrentPosition, CurrentDuration);
            }
            else
            {
                _smtcService.ClearMediaState();
            }
        });
    }

    private void UiTimer_Tick(object? sender, object e)
    {
        bool hasMedia = !string.IsNullOrWhiteSpace(Media.Title) && Media.Title != "Unknown";
        if (hasMedia && Media.IsPlaying && Media.Duration > 0)
        {
            var elapsedMs = (DateTime.Now - _lastUpdateTime).TotalMilliseconds;
            var estimatedPosition = _basePosition + elapsedMs;
            if (estimatedPosition > Media.Duration) estimatedPosition = Media.Duration;

            Media.Position = estimatedPosition;
            CurrentPosition = estimatedPosition;
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

    [RelayCommand]
    private async Task Seek(double position)
    {
        long posMs = (long)position;
        await _networkService.SendSeekAsync(posMs);
        
        // Optimistically update position
        _basePosition = position;
        _lastUpdateTime = DateTime.Now;
        CurrentPosition = position;
        FormattedPosition = FormatTime(position);
        Media.Position = position;
        _smtcService.UpdateTimeline(position, Media.Duration);
    }

    private void ResetState()
    {
        Media = new MediaState();
        
        var loader = new Microsoft.Windows.ApplicationModel.Resources.ResourceLoader();
        DisplayTitle = loader.GetString("NoMediaPlaying/Text") ?? "No Media Playing";
        DisplayArtist = loader.GetString("UnknownArtist/Text") ?? "Unknown Artist";
        DisplayAlbum = loader.GetString("UnknownAlbum/Text") ?? "Unknown Album";

        FormattedPosition = "00:00";
        FormattedDuration = "00:00";
        CurrentPosition = 0;
        CurrentDuration = 0;
        PlayPauseIcon = "\uE768";
        _smtcService.ClearMediaState();
    }
}
