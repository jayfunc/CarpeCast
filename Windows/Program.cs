using System;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.UI.Dispatching;
using Microsoft.Windows.AppLifecycle;
using Windows.ApplicationModel.Activation;
using System.Runtime.InteropServices;

namespace CarpeCast;

public static class Program
{
    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    private const int SW_RESTORE = 9;

    [STAThread]
    static async Task<int> Main(string[] args)
    {
        WinRT.ComWrappersSupport.InitializeComWrappers();

        bool isRedirect = await DecideRedirection();
        if (!isRedirect)
        {
            Microsoft.UI.Xaml.Application.Start((p) =>
            {
                var context = new DispatcherQueueSynchronizationContext(
                    DispatcherQueue.GetForCurrentThread());
                SynchronizationContext.SetSynchronizationContext(context);
                new App();
            });
        }

        return 0;
    }

    private static async Task<bool> DecideRedirection()
    {
        bool isRedirect = false;
        try
        {
            var mainInstance = AppInstance.FindOrRegisterForKey("CarpeCast_MainInstance");
            if (!mainInstance.IsCurrent)
            {
                isRedirect = true;
                var activatedEventArgs = AppInstance.GetCurrent().GetActivatedEventArgs();
                await mainInstance.RedirectActivationToAsync(activatedEventArgs);
            }
            else
            {
                mainInstance.Activated += MainInstance_Activated;
            }
        }
        catch { }
        return isRedirect;
    }

    private static void MainInstance_Activated(object? sender, AppActivationArguments e)
    {
        var app = App.Current as App;
        var window = app?.GetMainWindow();
        if (window != null)
        {
            var dispatcherQueue = Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread();
            if (dispatcherQueue != null)
            {
                dispatcherQueue.TryEnqueue(() => 
                {
                    var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(window);
                    ShowWindow(hwnd, SW_RESTORE);
                    SetForegroundWindow(hwnd);
                    window.Activate();
                });
            }
            else
            {
                var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(window);
                ShowWindow(hwnd, SW_RESTORE);
                SetForegroundWindow(hwnd);
                window.Activate();
            }
        }
    }
}
