using System.Drawing;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace Metrolist.AuthHelper;

internal sealed class PoTokenWindow : Form
{
    private const string CreateUrl = "https://www.youtube.com/api/jnn/v1/Create";
    private const string GenerateIntegrityUrl = "https://www.youtube.com/api/jnn/v1/GenerateIT";
    private const int TimeoutMs = 60_000;

    private static readonly HttpClient Http = new();

    private readonly string videoId;
    private readonly string visitorData;
    private readonly WebView2 webView = new() { Dock = DockStyle.Fill };
    private readonly System.Windows.Forms.Timer timeoutTimer = new() { Interval = TimeoutMs };

    private bool initializationStarted;
    private bool completed;

    internal PoTokenResult? Result { get; private set; }
    internal Exception? Error { get; private set; }

    internal PoTokenWindow(string videoId, string visitorData)
    {
        this.videoId = videoId;
        this.visitorData = visitorData;

        Text = "MetroList PO Token";
        Width = 2;
        Height = 2;
        ShowInTaskbar = false;
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.Manual;
        Location = new Point(-32000, -32000);
        Controls.Add(webView);

        Shown += OnShown;
        timeoutTimer.Tick += (_, _) => Fail(new TimeoutException("PO-token generation timed out."));
    }

    private async void OnShown(object? sender, EventArgs args)
    {
        try
        {
            timeoutTimer.Start();
            await webView.EnsureCoreWebView2Async();

            var core = webView.CoreWebView2;
            core.Settings.AreDefaultContextMenusEnabled = false;
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            core.WebMessageReceived += OnWebMessageReceived;
            core.NavigationCompleted += OnNavigationCompleted;

            await core.CallDevToolsProtocolMethodAsync(
                "Network.setUserAgentOverride",
                JsonSerializer.Serialize(new { userAgent = PoTokenProtocol.UserAgent })
            );

            var asset = Path.Combine(AppContext.BaseDirectory, "po_token.html");
            if (!File.Exists(asset))
            {
                throw new FileNotFoundException("PO-token HTML asset is missing.", asset);
            }

            core.SetVirtualHostNameToFolderMapping(
                "www.youtube.com",
                AppContext.BaseDirectory,
                CoreWebView2HostResourceAccessKind.DenyCors
            );
            webView.Source = new Uri("https://www.youtube.com/po_token.html");
        }
        catch (Exception exception)
        {
            Fail(exception);
        }
    }

    private async void OnNavigationCompleted(
        object? sender,
        CoreWebView2NavigationCompletedEventArgs args
    )
    {
        if (completed || initializationStarted) return;
        if (!args.IsSuccess)
        {
            Fail(new InvalidOperationException("PO-token WebView navigation failed."));
            return;
        }

        initializationStarted = true;
        try
        {
            var rawChallenge = await PostBotguardAsync(
                CreateUrl,
                JsonSerializer.Serialize(new[] { PoTokenProtocol.RequestKey })
            );
            var challenge = PoTokenProtocol.ParseChallengeData(rawChallenge);
            var script =
                """
                (async function() {
                    try {
                        const data = __CHALLENGE__;
                        const result = await runBotGuard(data);
                        window.webPoSignalOutput = result.webPoSignalOutput;
                        window.chrome.webview.postMessage({
                            type: "botguard",
                            response: result.botguardResponse
                        });
                    } catch (error) {
                        window.chrome.webview.postMessage({
                            type: "error",
                            message: String(error)
                        });
                    }
                })();
                """.Replace("__CHALLENGE__", challenge);

            await webView.CoreWebView2.ExecuteScriptAsync(script);
        }
        catch (Exception exception)
        {
            Fail(exception);
        }
    }

    private async void OnWebMessageReceived(
        object? sender,
        CoreWebView2WebMessageReceivedEventArgs args
    )
    {
        if (completed) return;

        try
        {
            using var document = JsonDocument.Parse(args.WebMessageAsJson);
            var root = document.RootElement;
            var type = root.GetProperty("type").GetString();

            switch (type)
            {
                case "botguard":
                    await CreateMinterAndTokensAsync(root.GetProperty("response").GetString() ?? "");
                    break;
                case "tokens":
                    Complete(
                        new PoTokenResult(
                            PlayerRequestToken: PoTokenProtocol.ToUrlSafeBase64(
                                ReadByteArray(root.GetProperty("playerRequest"))
                            ),
                            StreamingDataToken: PoTokenProtocol.ToUrlSafeBase64(
                                ReadByteArray(root.GetProperty("streamingData"))
                            ),
                            VisitorData: visitorData
                        )
                    );
                    break;
                case "error":
                    Fail(
                        new InvalidOperationException(
                            "PO-token JavaScript failed: " +
                            (root.TryGetProperty("message", out var message)
                                ? message.GetString()
                                : "unknown error")
                        )
                    );
                    break;
            }
        }
        catch (Exception exception)
        {
            Fail(exception);
        }
    }

    private async Task CreateMinterAndTokensAsync(string botguardResponse)
    {
        var integrityPayload =
            JsonSerializer.Serialize(new[] { PoTokenProtocol.RequestKey, botguardResponse });
        var rawIntegrity = await PostBotguardAsync(GenerateIntegrityUrl, integrityPayload);
        var (integrityToken, _) = PoTokenProtocol.ParseIntegrityTokenData(rawIntegrity);

        var integrityJs = PoTokenProtocol.BytesToJavaScriptUint8Array(integrityToken);
        var visitorJs =
            PoTokenProtocol.BytesToJavaScriptUint8Array(Encoding.UTF8.GetBytes(visitorData));
        var videoJs =
            PoTokenProtocol.BytesToJavaScriptUint8Array(Encoding.UTF8.GetBytes(videoId));

        var script =
            """
            (async function() {
                try {
                    const integrityToken = __INTEGRITY__;
                    await createPoTokenMinter(window.webPoSignalOutput, integrityToken);
                    const playerRequest = await obtainPoToken(__VISITOR__);
                    const streamingData = await obtainPoToken(__VIDEO__);
                    window.chrome.webview.postMessage({
                        type: "tokens",
                        playerRequest: Array.from(playerRequest),
                        streamingData: Array.from(streamingData)
                    });
                } catch (error) {
                    window.chrome.webview.postMessage({
                        type: "error",
                        message: String(error)
                    });
                }
            })();
            """
                .Replace("__INTEGRITY__", integrityJs)
                .Replace("__VISITOR__", visitorJs)
                .Replace("__VIDEO__", videoJs);

        await webView.CoreWebView2.ExecuteScriptAsync(script);
    }

    private static async Task<string> PostBotguardAsync(string url, string payload)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, url);
        request.Headers.TryAddWithoutValidation("User-Agent", PoTokenProtocol.UserAgent);
        request.Headers.TryAddWithoutValidation("Accept", "application/json");
        request.Headers.TryAddWithoutValidation("x-goog-api-key", PoTokenProtocol.GoogleApiKey);
        request.Headers.TryAddWithoutValidation("x-user-agent", "grpc-web-javascript/0.1");

        var content = new ByteArrayContent(Encoding.UTF8.GetBytes(payload));
        content.Headers.ContentType = new MediaTypeHeaderValue("application/json+protobuf");
        request.Content = content;

        using var response = await Http.SendAsync(request);
        var body = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode || string.IsNullOrWhiteSpace(body))
        {
            throw new InvalidOperationException(
                "BotGuard request failed with HTTP " + (int)response.StatusCode
            );
        }
        return body;
    }

    private static byte[] ReadByteArray(JsonElement element) =>
        element.EnumerateArray().Select(item => checked((byte)item.GetInt32())).ToArray();

    private void Complete(PoTokenResult result)
    {
        if (completed) return;
        completed = true;
        timeoutTimer.Stop();
        Result = result;
        Close();
    }

    private void Fail(Exception error)
    {
        if (completed) return;
        completed = true;
        timeoutTimer.Stop();
        Error = error;
        Close();
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            timeoutTimer.Dispose();
            webView.Dispose();
        }
        base.Dispose(disposing);
    }
}
