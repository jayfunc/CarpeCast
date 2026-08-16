using Windows.Storage;

namespace WindowsMediaReceiver.Services;

public class SettingsService : ISettingsService
{
    private readonly ApplicationDataContainer _localSettings;

    public SettingsService()
    {
        _localSettings = ApplicationData.Current.LocalSettings;
        Initialize();
    }

    public string DeviceName { get; set; } = "PC";
    public int DiscoveryPort { get; set; } = 5001;
    public int DataPort { get; set; } = 5000;
    public string AppTheme { get; set; } = "System";
    public string AppLanguage { get; set; } = "zh-CN";

    public void Initialize()
    {
        DeviceName = _localSettings.Values["DeviceName"] as string ?? Environment.MachineName;
        
        if (string.IsNullOrEmpty(_localSettings.Values["DeviceName"] as string))
        {
            _localSettings.Values["DeviceName"] = DeviceName;
        }

        DiscoveryPort = _localSettings.Values["DiscoveryPort"] is int dp ? dp : 5001;
        DataPort = _localSettings.Values["DataPort"] is int dsp ? dsp : 5000;

        AppTheme = _localSettings.Values["AppTheme"] as string ?? "System";
        AppLanguage = _localSettings.Values["AppLanguage"] as string ?? "zh-CN";
    }

    public void Save()
    {
        _localSettings.Values["DeviceName"] = DeviceName;
        _localSettings.Values["DiscoveryPort"] = DiscoveryPort;
        _localSettings.Values["DataPort"] = DataPort;
        _localSettings.Values["AppTheme"] = AppTheme;
        _localSettings.Values["AppLanguage"] = AppLanguage;

        if (Windows.Globalization.ApplicationLanguages.PrimaryLanguageOverride != AppLanguage)
        {
            Windows.Globalization.ApplicationLanguages.PrimaryLanguageOverride = AppLanguage;
        }
    }
}
