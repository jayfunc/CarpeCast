using Windows.ApplicationModel;
using Windows.ApplicationModel.Activation;
using Windows.Foundation;
using Windows.Foundation.Collections;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using Microsoft.UI.Xaml.Shapes;
using Microsoft.Extensions.DependencyInjection;
using CarpeCast.Services;
using CarpeCast.ViewModels;

namespace CarpeCast;

/// <summary>
/// Provides application-specific behavior to supplement the default Application class.
/// </summary>
public partial class App : Application
{
    private Window? _window;
    
    public new static App Current => (App)Application.Current;
    public IServiceProvider Services { get; }

    public App()
    {
        Services = ConfigureServices();
        var localSettings = Windows.Storage.ApplicationData.Current.LocalSettings;
        if (localSettings.Values.TryGetValue("AppLanguage", out object langObj) && langObj is string lang)
        {
            Windows.Globalization.ApplicationLanguages.PrimaryLanguageOverride = lang;
        }

        InitializeComponent();
    }

    /// <summary>
    /// Invoked when the application is launched.
    /// </summary>
    /// <param name="args">Details about the launch request and process.</param>
    protected override void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
    {
        _window = new MainWindow();
        _window.Activate();
    }

    private static IServiceProvider ConfigureServices()
    {
        var services = new ServiceCollection();

        // Services
        services.AddSingleton<ISettingsService, SettingsService>();
        services.AddSingleton<INetworkService, NetworkService>();
        services.AddSingleton<ISmtcService, SmtcService>();

        // ViewModels
        services.AddSingleton<MainViewModel>();
        services.AddSingleton<PlayerViewModel>();
        services.AddSingleton<SettingsViewModel>();
        services.AddSingleton<DevicesViewModel>();

        return services.BuildServiceProvider();
    }
}
