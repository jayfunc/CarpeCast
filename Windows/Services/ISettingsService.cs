namespace CarpeCast.Services;

public interface ISettingsService
{
    string DeviceName { get; set; }
    int DiscoveryPort { get; set; }
    int DataPort { get; set; }
    string AppTheme { get; set; }
    string AppLanguage { get; set; }
    bool ShowOnStartup { get; set; }
    
    event System.Action<string>? ThemeChanged;

    void Initialize();
    void Save();
}
