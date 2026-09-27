using Microsoft.Web.WebView2.Core;

namespace Metrolist.AuthHelper;

internal static class WebView2Profile
{
    internal static string UserDataFolder() =>
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "MetroListWindows",
            "WebView2"
        );

    internal static async Task<CoreWebView2Environment> CreateEnvironmentAsync()
    {
        var userDataFolder = UserDataFolder();
        Directory.CreateDirectory(userDataFolder);
        return await CoreWebView2Environment.CreateAsync(userDataFolder: userDataFolder);
    }
}
