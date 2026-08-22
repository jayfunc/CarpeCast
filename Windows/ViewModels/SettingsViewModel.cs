using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CarpeCast.Services;
using System;

namespace CarpeCast.ViewModels;

public partial class SettingsViewModel : ObservableObject
{
    private readonly ISettingsService _settingsService;
    private bool _isInitialized;

    private readonly double _initialDiscoveryPort;
    private readonly double _initialDataPort;
    private readonly int _initialThemeIndex;
    private readonly int _initialLanguageIndex;

    public SettingsViewModel(ISettingsService settingsService)
    {
        _settingsService = settingsService;
        
        DeviceName = _settingsService.DeviceName;
        DiscoveryPort = _settingsService.DiscoveryPort;
        DataPort = _settingsService.DataPort;

        SelectedThemeIndex = _settingsService.AppTheme switch
        {
            "Light" => 1,
            "Dark" => 2,
            _ => 0
        };

        SelectedLanguageIndex = _settingsService.AppLanguage == "zh-CN" ? 1 : 0;
        ShowOnStartup = _settingsService.ShowOnStartup;

        _initialDiscoveryPort = DiscoveryPort;
        _initialDataPort = DataPort;
        _initialThemeIndex = SelectedThemeIndex;
        _initialLanguageIndex = SelectedLanguageIndex;

        _isInitialized = true;
    }

    [ObservableProperty]
    public partial string DeviceName { get; set; }

    [ObservableProperty]
    public partial double DiscoveryPort { get; set; }

    [ObservableProperty]
    public partial double DataPort { get; set; }

    [ObservableProperty]
    public partial int SelectedThemeIndex { get; set; }

    [ObservableProperty]
    public partial int SelectedLanguageIndex { get; set; }

    [ObservableProperty]
    public partial bool ShowOnStartup { get; set; }

    [ObservableProperty]
    public partial bool IsRestartRequired { get; set; }

    public string VersionPrefix => $"v1.0.0 ({VersionInfo.Date} - ";
    public string CommitHash => VersionInfo.Hash;
    public Uri CommitUri => new Uri($"https://github.com/jayfunc/CarpeCast/commit/{VersionInfo.Hash}");

    partial void OnDeviceNameChanged(string value) => SaveSettings();
    partial void OnDiscoveryPortChanged(double value) => SaveSettings();
    partial void OnDataPortChanged(double value) => SaveSettings();
    partial void OnSelectedThemeIndexChanged(int value) => SaveSettings();
    partial void OnSelectedLanguageIndexChanged(int value) => SaveSettings();
    partial void OnShowOnStartupChanged(bool value) => SaveSettings();

    private void SaveSettings()
    {
        if (!_isInitialized) return;

        _settingsService.DeviceName = DeviceName;
        _settingsService.DiscoveryPort = (int)DiscoveryPort;
        _settingsService.DataPort = (int)DataPort;
        
        _settingsService.AppTheme = SelectedThemeIndex switch
        {
            1 => "Light",
            2 => "Dark",
            _ => "System"
        };
        
        _settingsService.AppLanguage = SelectedLanguageIndex == 1 ? "zh-CN" : "en-US";
        _settingsService.ShowOnStartup = ShowOnStartup;
        
        _settingsService.Save();

        IsRestartRequired = 
            DiscoveryPort != _initialDiscoveryPort ||
            DataPort != _initialDataPort ||
            SelectedLanguageIndex != _initialLanguageIndex;
    }

    [RelayCommand]
    private void RestoreDefaultName()
    {
        DeviceName = Environment.MachineName;
    }

    [RelayCommand]
    private async System.Threading.Tasks.Task OpenStartupSettings()
    {
        await Windows.System.Launcher.LaunchUriAsync(new Uri("ms-settings:startupapps"));
    }

    [RelayCommand]
    private async System.Threading.Tasks.Task OpenBetterLyrics()
    {
        await Windows.System.Launcher.LaunchUriAsync(new Uri("https://github.com/jayfunc/BetterLyrics"));
    }

    [RelayCommand]
    private async System.Threading.Tasks.Task DownloadAndroid()
    {
        await Windows.System.Launcher.LaunchUriAsync(new Uri("https://github.com/jayfunc/CarpeCast"));
    }

    [RelayCommand]
    private void SaveAndRestart()
    {
        Microsoft.Windows.AppLifecycle.AppInstance.Restart("");
    }
}
