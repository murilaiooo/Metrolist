namespace Metrolist.AuthHelper;

internal static class AuthProtocol
{
    internal static bool HasSapisid(IEnumerable<string> cookieNames) =>
        cookieNames.Any(name => string.Equals(name, "SAPISID", StringComparison.Ordinal));

    internal static string NormalizeDataSyncId(string? value) =>
        (value ?? string.Empty).Split("||", 2, StringSplitOptions.None)[0];

    internal static string NormalizeAuthUser(string? value)
    {
        var digits = new string((value ?? string.Empty).Where(char.IsDigit).ToArray());
        return string.IsNullOrEmpty(digits) ? "0" : digits;
    }

    internal static bool IsComplete(
        IEnumerable<string> cookieNames,
        string? visitorData
    ) => HasSapisid(cookieNames) && !string.IsNullOrWhiteSpace(visitorData);

    internal static int SelfTest()
    {
        if (!HasSapisid(new[] { "HSID", "SAPISID" })) return 10;
        if (HasSapisid(new[] { "HSID" })) return 11;
        if (NormalizeDataSyncId("page-id||user-id") != "page-id") return 12;
        if (NormalizeAuthUser("user-12") != "12") return 13;
        if (NormalizeAuthUser(null) != "0") return 14;
        if (!IsComplete(new[] { "SAPISID" }, "visitor")) return 15;
        if (IsComplete(new[] { "SAPISID" }, "")) return 16;

        var poTokenResult = PoTokenProtocol.SelfTest();
        if (poTokenResult != 0) return poTokenResult;

        var webViewUserDataFolder = WebView2Profile.UserDataFolder();
        var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        if (!Path.GetFullPath(webViewUserDataFolder).StartsWith(
                Path.GetFullPath(localAppData),
                StringComparison.OrdinalIgnoreCase
            )) return 30;
        if (!webViewUserDataFolder.EndsWith(
                Path.Combine("MetroListWindows", "WebView2"),
                StringComparison.OrdinalIgnoreCase
            )) return 31;

        Console.WriteLine("SELF_TEST_OK");
        return 0;
    }
}
