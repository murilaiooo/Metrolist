using System.Text.Json;
using Microsoft.Web.WebView2.WinForms;

namespace Metrolist.AuthHelper;

internal sealed class AuthWindow : Form
{
    private const string YouTubeMusicUrl = "https://music.youtube.com";
    private const string LoginUrl =
        "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com";

    private readonly WebView2 webView = new()
    {
        Dock = DockStyle.Fill,
    };

    private bool extractionRunning;

    internal AuthResult? Result { get; private set; }

    internal AuthWindow()
    {
        Text = "MetroList Windows - YouTube Music Login";
        Width = 1100;
        Height = 800;
        StartPosition = FormStartPosition.CenterScreen;
        Controls.Add(webView);

        Shown += OnShown;
    }

    private async void OnShown(object? sender, EventArgs args)
    {
        try
        {
            await webView.EnsureCoreWebView2Async();
            webView.CoreWebView2.NavigationCompleted += async (_, _) =>
            {
                var host = webView.Source?.Host;
                if (string.Equals(host, "music.youtube.com", StringComparison.OrdinalIgnoreCase))
                {
                    await TryExtractAuthDataAsync();
                }
            };

            webView.Source = new Uri(LoginUrl);
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine($"WebView2 initialization failed: {exception.GetType().Name}");
            Close();
        }
    }

    private async Task TryExtractAuthDataAsync()
    {
        if (extractionRunning || webView.CoreWebView2 is null) return;
        extractionRunning = true;

        try
        {
            for (var attempt = 0; attempt < 20; attempt++)
            {
                var cookies = await webView.CoreWebView2.CookieManager.GetCookiesAsync(YouTubeMusicUrl);
                var cookieNames = cookies.Select(cookie => cookie.Name).ToArray();
                var cookieHeader = string.Join("; ", cookies.Select(cookie => $"{cookie.Name}={cookie.Value}"));

                const string script = """
                    (() => ({
                        visitorData: window.yt && window.yt.config_ ? window.yt.config_.VISITOR_DATA : null,
                        dataSyncId: window.yt && window.yt.config_ ? window.yt.config_.DATASYNC_ID : null,
                        authUser: window.yt && window.yt.config_
                            ? String(window.yt.config_.SESSION_INDEX || 0)
                            : "0"
                    }))()
                    """;

                var raw = await webView.CoreWebView2.ExecuteScriptAsync(script);
                using var document = JsonDocument.Parse(raw);
                var root = document.RootElement;

                var visitorData = GetOptionalString(root, "visitorData");
                var dataSyncId = GetOptionalString(root, "dataSyncId");
                var authUser = GetOptionalString(root, "authUser");

                if (AuthProtocol.IsComplete(cookieNames, visitorData))
                {
                    Result = new AuthResult(
                        Cookie: cookieHeader,
                        VisitorData: visitorData!,
                        DataSyncId: AuthProtocol.NormalizeDataSyncId(dataSyncId),
                        AuthUser: AuthProtocol.NormalizeAuthUser(authUser)
                    );
                    Close();
                    return;
                }

                await Task.Delay(500);
            }

            Console.Error.WriteLine("Timed out waiting for a complete YouTube Music session.");
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine($"Session extraction failed: {exception.GetType().Name}");
        }
        finally
        {
            extractionRunning = false;
        }
    }

    private static string? GetOptionalString(JsonElement root, string propertyName)
    {
        if (!root.TryGetProperty(propertyName, out var value)) return null;
        if (value.ValueKind is JsonValueKind.Null or JsonValueKind.Undefined) return null;
        return value.GetString();
    }
}
