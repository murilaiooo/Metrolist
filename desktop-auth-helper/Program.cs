using System.Text.Json;
using Microsoft.Web.WebView2.Core;

namespace Metrolist.AuthHelper;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        if (args.Contains("--self-test", StringComparer.Ordinal))
        {
            return AuthProtocol.SelfTest();
        }

        if (args.Contains("--runtime-check", StringComparer.Ordinal))
        {
            return CheckWebView2Runtime();
        }

        var poTokenIndex = Array.FindIndex(
            args,
            arg => string.Equals(arg, "--po-token", StringComparison.Ordinal)
        );
        if (poTokenIndex >= 0)
        {
            if (poTokenIndex + 2 >= args.Length)
            {
                Console.Error.WriteLine("PO_TOKEN_ARGUMENTS_MISSING");
                return 3;
            }

            return RunPoToken(
                videoId: args[poTokenIndex + 1],
                visitorData: args[poTokenIndex + 2]
            );
        }

        ApplicationConfiguration.Initialize();

        using var window = new AuthWindow();
        Application.Run(window);

        if (window.Result is null)
        {
            return 1;
        }

        var json = JsonSerializer.Serialize(
            window.Result,
            new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            }
        );

        Console.Out.WriteLine(json);
        return 0;
    }

    private static int RunPoToken(string videoId, string visitorData)
    {
        ApplicationConfiguration.Initialize();

        using var window = new PoTokenWindow(videoId, visitorData);
        Application.Run(window);

        if (window.Result is null)
        {
            var error = window.Error;
            Console.Error.WriteLine(
                "PO_TOKEN_FAILED:" +
                (error is null
                    ? "Unknown"
                    : error.GetType().Name + ":" + error.Message.Take(180).Aggregate("", (a, c) => a + c))
            );
            return 1;
        }

        var json = JsonSerializer.Serialize(
            window.Result,
            new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            }
        );
        Console.Out.WriteLine(json);
        return 0;
    }

    private static int CheckWebView2Runtime()
    {
        try
        {
            var version = CoreWebView2Environment.GetAvailableBrowserVersionString();
            if (string.IsNullOrWhiteSpace(version))
            {
                Console.Error.WriteLine("WEBVIEW2_RUNTIME_MISSING");
                return 2;
            }

            Console.Out.WriteLine("WEBVIEW2_RUNTIME_OK");
            return 0;
        }
        catch
        {
            Console.Error.WriteLine("WEBVIEW2_RUNTIME_MISSING");
            return 2;
        }
    }
}
