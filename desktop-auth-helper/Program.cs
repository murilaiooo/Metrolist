using System.Text.Json;

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
}
