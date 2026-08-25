using Windows.Storage;

namespace CarpeCast.Services;

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
    
    private string _appTheme = "System";
    public string AppTheme 
    { 
        get => _appTheme;
        set 
        {
            if (_appTheme != value)
            {
                _appTheme = value;
                ThemeChanged?.Invoke(value);
            }
        }
    }

    public string AppLanguage { get; set; } = "zh-CN";
    public bool ShowOnStartup { get; set; } = true;

    public event System.Action<string>? ThemeChanged;

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
        ShowOnStartup = _localSettings.Values["ShowOnStartup"] is bool sos ? sos : true;
    }

    public void Save()
    {
        _localSettings.Values["DeviceName"] = DeviceName;
        _localSettings.Values["DiscoveryPort"] = DiscoveryPort;
        _localSettings.Values["DataPort"] = DataPort;
        _localSettings.Values["AppTheme"] = AppTheme;
        _localSettings.Values["AppLanguage"] = AppLanguage;
        _localSettings.Values["ShowOnStartup"] = ShowOnStartup;

        if (Windows.Globalization.ApplicationLanguages.PrimaryLanguageOverride != AppLanguage)
        {
            Windows.Globalization.ApplicationLanguages.PrimaryLanguageOverride = AppLanguage;
        }
    }
}
