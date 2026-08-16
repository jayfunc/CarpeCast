using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI;
using System;
using Microsoft.Extensions.DependencyInjection;
using WindowsMediaReceiver.ViewModels;

namespace WindowsMediaReceiver.Views;

public sealed partial class DevicesPage : Page
{
    public DevicesViewModel ViewModel { get; }

    public DevicesPage()
    {
        ViewModel = App.Current.Services.GetRequiredService<DevicesViewModel>();
        this.InitializeComponent();
    }
}

public class StatusToColorConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        if (value is string status)
        {
            return status == "Active" 
                ? new SolidColorBrush(Colors.MediumSeaGreen) 
                : new SolidColorBrush(Colors.Gray);
        }
        return new SolidColorBrush(Colors.Gray);
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language)
    {
        throw new NotImplementedException();
    }
}
