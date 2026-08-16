using Microsoft.UI.Xaml.Controls;
using Microsoft.Extensions.DependencyInjection;
using WindowsMediaReceiver.ViewModels;

namespace WindowsMediaReceiver.Views;

public sealed partial class PlayerPage : Page
{
    public PlayerViewModel ViewModel { get; }

    public PlayerPage()
    {
        ViewModel = App.Current.Services.GetRequiredService<PlayerViewModel>();
        this.InitializeComponent();

        ViewModel.StartNetworking();
    }
}
