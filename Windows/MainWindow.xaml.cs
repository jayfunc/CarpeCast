using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System;
using System.Runtime.InteropServices;
using WindowsMediaReceiver.Views;
using WindowsMediaReceiver.ViewModels;
using Microsoft.Extensions.DependencyInjection;

namespace WindowsMediaReceiver;

public sealed partial class MainWindow : Window
{
    public MainViewModel ViewModel { get; }

    public MainWindow()
    {
        ViewModel = App.Current.Services.GetRequiredService<MainViewModel>();
        this.InitializeComponent();

        this.ExtendsContentIntoTitleBar = true;
        this.SetTitleBar(AppTitleBar);

        // Apply theme from settings
        var settingsService = App.Current.Services.GetRequiredService<WindowsMediaReceiver.Services.ISettingsService>();
        if (this.Content is FrameworkElement root)
        {
            root.RequestedTheme = settingsService.AppTheme switch
            {
                "Light" => ElementTheme.Light,
                "Dark" => ElementTheme.Dark,
                _ => ElementTheme.Default
            };
        }
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

    private void ShowLogs_Click(object sender, RoutedEventArgs e)
    {
        this.Activate();
        ShowWindow(GetWindowHandle(), 9); // SW_RESTORE
        SetForegroundWindow(GetWindowHandle());
    }

    private void Exit_Click(object sender, RoutedEventArgs e)
    {
        Application.Current.Exit();
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
