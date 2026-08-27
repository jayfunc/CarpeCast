using System;
using Windows.Media;
using Windows.Media.Playback;

namespace CarpeCast.Services;

public class SmtcService : ISmtcService
{
    private MediaPlayer? _mediaPlayer;
    private SystemMediaTransportControls? _smtc;

    public event EventHandler? PlayPressed;
    public event EventHandler? PausePressed;
    public event EventHandler? NextPressed;
    public event EventHandler? PreviousPressed;

    public void Initialize()
    {
        _mediaPlayer = new MediaPlayer();
        _mediaPlayer.CommandManager.IsEnabled = false; 

        _smtc = _mediaPlayer.SystemMediaTransportControls;
        _smtc.IsEnabled = true;
        _smtc.IsPlayEnabled = true;
        _smtc.IsPauseEnabled = true;
        _smtc.IsNextEnabled = true;
        _smtc.IsPreviousEnabled = true;

        _smtc.ButtonPressed += Smtc_ButtonPressed;
    }

    private void Smtc_ButtonPressed(SystemMediaTransportControls sender, SystemMediaTransportControlsButtonPressedEventArgs args)
    {
        switch (args.Button)
        {
            case SystemMediaTransportControlsButton.Play:
                PlayPressed?.Invoke(this, EventArgs.Empty);
                break;
            case SystemMediaTransportControlsButton.Pause:
                PausePressed?.Invoke(this, EventArgs.Empty);
                break;
            case SystemMediaTransportControlsButton.Next:
                NextPressed?.Invoke(this, EventArgs.Empty);
                break;
            case SystemMediaTransportControlsButton.Previous:
                PreviousPressed?.Invoke(this, EventArgs.Empty);
                break;
        }
    }

    private string _lastTitle = string.Empty;
    private string _lastArtist = string.Empty;
    private string _lastAlbum = string.Empty;
    private string _lastAlbumArtBase64 = string.Empty;

    public void UpdateMediaState(string title, string artist, string album, bool isPlaying, double position, double duration, string albumArtBase64 = "")
    {
        if (_smtc == null) return;
        
        _smtc.IsEnabled = true;

        bool metadataChanged = false;
        if (_lastTitle != title || _lastArtist != artist || _lastAlbum != album || _lastAlbumArtBase64 != albumArtBase64)
        {
            metadataChanged = true;
            _lastTitle = title ?? "";
            _lastArtist = artist ?? "";
            _lastAlbum = album ?? "";
            _lastAlbumArtBase64 = albumArtBase64 ?? "";
        }

        if (metadataChanged)
        {
            var updater = _smtc.DisplayUpdater;
            updater.Type = MediaPlaybackType.Music;
            updater.MusicProperties.Title = title;
            updater.MusicProperties.Artist = artist;
            updater.MusicProperties.AlbumTitle = album;

            if (!string.IsNullOrEmpty(albumArtBase64))
            {
                try
                {
                    var bytes = Convert.FromBase64String(albumArtBase64);
                    var stream = new Windows.Storage.Streams.InMemoryRandomAccessStream();
                    using (var writer = new Windows.Storage.Streams.DataWriter(stream.GetOutputStreamAt(0)))
                    {
                        writer.WriteBytes(bytes);
                        writer.StoreAsync().AsTask().Wait();
                    }
                    stream.Seek(0);
                    updater.Thumbnail = Windows.Storage.Streams.RandomAccessStreamReference.CreateFromStream(stream);
                }
                catch { }
            }
            else
            {
                updater.Thumbnail = null;
            }

            updater.Update();
        }
        
        _smtc.PlaybackStatus = isPlaying ? MediaPlaybackStatus.Playing : MediaPlaybackStatus.Paused;

        var timeline = new SystemMediaTransportControlsTimelineProperties
        {
            StartTime = TimeSpan.Zero,
            MinSeekTime = TimeSpan.Zero,
            Position = TimeSpan.FromMilliseconds(position),
            MaxSeekTime = TimeSpan.FromMilliseconds(duration),
            EndTime = TimeSpan.FromMilliseconds(duration)
        };
        _smtc.UpdateTimelineProperties(timeline);
    }

    public void UpdateTimeline(double position, double duration)
    {
        if (_smtc == null || !_smtc.IsEnabled) return;
        
        var timeline = new SystemMediaTransportControlsTimelineProperties
        {
            StartTime = TimeSpan.Zero,
            MinSeekTime = TimeSpan.Zero,
            Position = TimeSpan.FromMilliseconds(position),
            MaxSeekTime = TimeSpan.FromMilliseconds(duration),
            EndTime = TimeSpan.FromMilliseconds(duration)
        };
        _smtc.UpdateTimelineProperties(timeline);
    }

    public void ClearMediaState()
    {
        if (_smtc == null) return;
        
        _smtc.PlaybackStatus = MediaPlaybackStatus.Closed;
        var updater = _smtc.DisplayUpdater;
        updater.ClearAll();
        updater.Update();
        _smtc.IsEnabled = false;

        _lastTitle = string.Empty;
        _lastArtist = string.Empty;
        _lastAlbum = string.Empty;
        _lastAlbumArtBase64 = string.Empty;
    }
}
