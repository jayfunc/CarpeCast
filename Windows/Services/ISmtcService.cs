using System;

namespace CarpeCast.Services;

public interface ISmtcService
{
    event EventHandler PlayPressed;
    event EventHandler PausePressed;
    event EventHandler NextPressed;
    event EventHandler PreviousPressed;

    void Initialize();
    void UpdateMediaState(string title, string artist, string album, bool isPlaying, double position, double duration);
    void UpdateTimeline(double position, double duration);
    void ClearMediaState();
}
