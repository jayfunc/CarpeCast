using Microsoft.UI.Xaml.Controls;
using Microsoft.Extensions.DependencyInjection;
using CarpeCast.ViewModels;

namespace CarpeCast.Views;

public sealed partial class PlayerPage : Page
{
    public PlayerViewModel ViewModel { get; }

    public PlayerPage()
    {
        ViewModel = App.Current.Services.GetRequiredService<PlayerViewModel>();
        this.InitializeComponent();

        ViewModel.StartNetworking();
    }

    private void ProgressSlider_ValueChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e)
    {
        if (ViewModel == null) return;
        
        // Ignore programmatic updates from the timer or SeekCommand itself
        if (System.Math.Abs(e.NewValue - ViewModel.CurrentPosition) > 1.0)
        {
            ViewModel.SeekCommand.Execute(e.NewValue);
        }
    }
}
