using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System;
using System.Runtime.InteropServices;
using CarpeCast.Views;
using CarpeCast.ViewModels;
using Microsoft.Extensions.DependencyInjection;

namespace CarpeCast;

public sealed partial class MainWindow : Window
{
    public MainViewModel ViewModel { get; }

    public MainWindow()
    {
        ViewModel = App.Current.Services.GetRequiredService<MainViewModel>();
        
        // Force early instantiation so they subscribe to NetworkService events immediately
        App.Current.Services.GetRequiredService<PlayerViewModel>();
        App.Current.Services.GetRequiredService<DevicesViewModel>();

        this.InitializeComponent();

        this.AppWindow.Closing += AppWindow_Closing;

        this.ExtendsContentIntoTitleBar = true;
        this.SetTitleBar(AppTitleBar);

        // Apply theme from settings
        var settingsService = App.Current.Services.GetRequiredService<CarpeCast.Services.ISettingsService>();
        
        void ApplyTheme(string themeStr)
        {
            var themeEnum = themeStr switch
            {
                "Light" => ElementTheme.Light,
                "Dark" => ElementTheme.Dark,
                _ => ElementTheme.Default
            };
            
            if (this.Content is FrameworkElement root)
            {
                root.RequestedTheme = themeEnum;
            }
            TrayIcon.RequestedTheme = themeEnum;
        }

        ApplyTheme(settingsService.AppTheme);

        settingsService.ThemeChanged += ApplyTheme;
    }

    private void NavView_Loaded(object sender, RoutedEventArgs e)
    {
        NavView.SelectedItem = NavView.MenuItems[0];
        ContentFrame.Navigate(typeof(PlayerPage));

        if (NavView.SettingsItem is NavigationViewItem settingsItem)
        {
            var loader = new Microsoft.Windows.ApplicationModel.Resources.ResourceLoader();
            settingsItem.Content = loader.GetString("SettingsTitle/Text");
        }
    }

    private void NavView_ItemInvoked(NavigationView sender, NavigationViewItemInvokedEventArgs args)
    {
        if (args.IsSettingsInvoked)
        {
            ContentFrame.Navigate(typeof(SettingsPage));
        }
        else if (args.InvokedItemContainer?.Tag?.ToString() == "Player")
        {
            ContentFrame.Navigate(typeof(PlayerPage));
        }
        else if (args.InvokedItemContainer?.Tag?.ToString() == "Devices")
        {
            ContentFrame.Navigate(typeof(DevicesPage));
        }
    }

    private void AppWindow_Closing(Microsoft.UI.Windowing.AppWindow sender, Microsoft.UI.Windowing.AppWindowClosingEventArgs args)
    {
        args.Cancel = true;
        sender.Hide();
    }

    private void ShowLogs_Click(object sender, RoutedEventArgs e)
    {
        this.AppWindow.Show();
        this.Activate();
        ShowWindow(GetWindowHandle(), 9); // SW_RESTORE
        SetForegroundWindow(GetWindowHandle());
    }

    private void Exit_Click(object sender, RoutedEventArgs e)
    {
        TrayIcon?.Dispose();
        var smtcService = App.Current.Services.GetRequiredService<CarpeCast.Services.ISmtcService>();
        smtcService.ClearMediaState();
        var networkService = App.Current.Services.GetRequiredService<CarpeCast.Services.INetworkService>();
        networkService.StopListening();
        Application.Current.Exit();
        Environment.Exit(0);
    }

    private IntPtr GetWindowHandle()
    {
        return WinRT.Interop.WindowNative.GetWindowHandle(this);
    }

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
}
