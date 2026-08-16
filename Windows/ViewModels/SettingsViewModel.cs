using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WindowsMediaReceiver.Services;

namespace WindowsMediaReceiver.ViewModels;

public partial class SettingsViewModel : ObservableObject
{
    private readonly ISettingsService _settingsService;

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

    [RelayCommand]
    private void SaveAndRestart()
    {
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
        
        _settingsService.Save();

        Microsoft.Windows.AppLifecycle.AppInstance.Restart("");
    }
}
