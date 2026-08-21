using Microsoft.UI.Xaml.Controls;
using Microsoft.Extensions.DependencyInjection;
using CarpeCast.ViewModels;

namespace CarpeCast.Views;

public sealed partial class SettingsPage : Page
{
    public SettingsViewModel ViewModel { get; }

    public SettingsPage()
    {
        ViewModel = App.Current.Services.GetRequiredService<SettingsViewModel>();
        this.InitializeComponent();
    }
}
