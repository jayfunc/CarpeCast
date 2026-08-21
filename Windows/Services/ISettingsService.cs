namespace CarpeCast.Services;

public interface ISettingsService
{
    string DeviceName { get; set; }
    int DiscoveryPort { get; set; }
    int DataPort { get; set; }
    string AppTheme { get; set; }
    string AppLanguage { get; set; }
    
    void Initialize();
    void Save();
}
