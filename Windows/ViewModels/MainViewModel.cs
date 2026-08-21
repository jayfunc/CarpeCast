using CommunityToolkit.Mvvm.ComponentModel;

namespace CarpeCast.ViewModels;

public partial class MainViewModel : ObservableObject
{
    [ObservableProperty]
    public partial string Title { get; set; } = "CarpeCast";

    // Used for navigation frame state, if needed.
    // In our simplified version, NavigationView will just handle framing.
}
