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

    public void UpdateMediaState(string title, string artist, string album, bool isPlaying, double position, double duration)
    {
        if (_smtc == null) return;
        
        _smtc.IsEnabled = true;

        var updater = _smtc.DisplayUpdater;
        updater.Type = MediaPlaybackType.Music;
        updater.MusicProperties.Title = title;
        updater.MusicProperties.Artist = artist;
        updater.MusicProperties.AlbumTitle = album;
        updater.Update();

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
        if (_smtc == null) return;
        
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
    }
}
