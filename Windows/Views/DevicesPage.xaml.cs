using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI;
using System;
using Microsoft.Extensions.DependencyInjection;
using CarpeCast.ViewModels;
using CarpeCast.Models;

namespace CarpeCast.Views;

public sealed partial class DevicesPage : Page
{
    public DevicesViewModel ViewModel { get; }

    public DevicesPage()
    {
        ViewModel = App.Current.Services.GetRequiredService<DevicesViewModel>();
        this.InitializeComponent();
    }

    private void DisconnectDeviceButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e)
    {
        var device = (sender as Button)?.DataContext as DeviceModel;
        ViewModel.DisconnectDeviceCommand.Execute(device);
    }

    private void ConnectToSenderButton_Click(object sender, Microsoft.UI.Xaml.RoutedEventArgs e)
    {
        var device = (sender as Button)?.DataContext as DeviceModel;
        ViewModel.ConnectToSenderCommand.Execute(device);
    }
}

public partial class StatusToColorConverter : IValueConverter
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

public partial class BoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        return (value is bool b && b) ? Microsoft.UI.Xaml.Visibility.Visible : Microsoft.UI.Xaml.Visibility.Collapsed;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}

public partial class InverseBoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        return (value is bool b && !b) ? Microsoft.UI.Xaml.Visibility.Visible : Microsoft.UI.Xaml.Visibility.Collapsed;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotImplementedException();
}
