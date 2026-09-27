using System.Text;
using System.Text.Json;

namespace Metrolist.AuthHelper;

internal static class PoTokenProtocol
{
    internal const string GoogleApiKey = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw";
    internal const string RequestKey = "O43z0dpjhgX20SCx4KAo";
    internal const string UserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3";

    internal static string ParseChallengeData(string rawChallengeData)
    {
        using var outerDocument = JsonDocument.Parse(rawChallengeData);
        var outer = outerDocument.RootElement;
        if (outer.ValueKind != JsonValueKind.Array)
        {
            throw new InvalidOperationException("BotGuard challenge root is not an array.");
        }

        if (outer.GetArrayLength() > 1 && outer[1].ValueKind == JsonValueKind.String)
        {
            var decoded = Descramble(outer[1].GetString()!);
            using var decodedDocument = JsonDocument.Parse(decoded);
            return BuildChallengeJson(decodedDocument.RootElement);
        }

        if (outer.GetArrayLength() == 0 || outer[0].ValueKind != JsonValueKind.Array)
        {
            throw new InvalidOperationException("BotGuard challenge payload is missing.");
        }

        return BuildChallengeJson(outer[0]);
    }

    internal static (byte[] Token, long ExpirationSeconds) ParseIntegrityTokenData(
        string rawIntegrityTokenData
    )
    {
        using var document = JsonDocument.Parse(rawIntegrityTokenData);
        var root = document.RootElement;
        if (root.ValueKind != JsonValueKind.Array || root.GetArrayLength() < 2)
        {
            throw new InvalidOperationException("Integrity token payload is invalid.");
        }

        var token = DecodeWebBase64(root[0].GetString() ?? string.Empty);
        var expiration = root[1].GetInt64();
        return (token, expiration);
    }

    internal static string BytesToJavaScriptUint8Array(byte[] bytes) =>
        "new Uint8Array([" + string.Join(",", bytes.Select(value => value.ToString())) + "])";

    internal static string ToUrlSafeBase64(byte[] bytes) =>
        Convert.ToBase64String(bytes)
            .Replace('+', '-')
            .Replace('/', '_');

    internal static int SelfTest()
    {
        const string challenge =
            "[[\"message\",[\"console.log('ok')\"],[\"https://example\"]," +
            "\"hash\",\"program\",\"globalName\",\"unused\",\"blob\"]]";

        using (var parsed = JsonDocument.Parse(ParseChallengeData(challenge)))
        {
            var root = parsed.RootElement;
            if (root.GetProperty("messageId").GetString() != "message") return 30;
            if (root.GetProperty("interpreterHash").GetString() != "hash") return 31;
            if (root.GetProperty("program").GetString() != "program") return 32;
            if (root.GetProperty("globalName").GetString() != "globalName") return 33;
        }

        var (token, expiration) = ParseIntegrityTokenData("[\"AQID\",1200]");
        if (!token.SequenceEqual(new byte[] { 1, 2, 3 })) return 34;
        if (expiration != 1200) return 35;
        if (BytesToJavaScriptUint8Array(token) != "new Uint8Array([1,2,3])") return 36;
        if (ToUrlSafeBase64(new byte[] { 251, 255 }) != "-_8=") return 37;

        return 0;
    }

    private static string BuildChallengeJson(JsonElement challengeData)
    {
        if (challengeData.ValueKind != JsonValueKind.Array || challengeData.GetArrayLength() < 8)
        {
            throw new InvalidOperationException("BotGuard challenge data is incomplete.");
        }

        var payload = new
        {
            messageId = challengeData[0].GetString(),
            interpreterJavascript = new
            {
                privateDoNotAccessOrElseSafeScriptWrappedValue = FirstString(challengeData[1]),
                privateDoNotAccessOrElseTrustedResourceUrlWrappedValue = FirstString(challengeData[2]),
            },
            interpreterHash = challengeData[3].GetString(),
            program = challengeData[4].GetString(),
            globalName = challengeData[5].GetString(),
            clientExperimentsStateBlob = challengeData[7].GetString(),
        };

        return JsonSerializer.Serialize(payload);
    }

    private static string? FirstString(JsonElement element)
    {
        if (element.ValueKind is JsonValueKind.Null or JsonValueKind.Undefined)
        {
            return null;
        }
        if (element.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        foreach (var item in element.EnumerateArray())
        {
            if (item.ValueKind == JsonValueKind.String)
            {
                return item.GetString();
            }
        }
        return null;
    }

    private static string Descramble(string scrambledChallenge)
    {
        var bytes = DecodeWebBase64(scrambledChallenge);
        for (var index = 0; index < bytes.Length; index++)
        {
            bytes[index] = unchecked((byte)(bytes[index] + 97));
        }
        return Encoding.UTF8.GetString(bytes);
    }

    private static byte[] DecodeWebBase64(string value)
    {
        var normalized =
            value
                .Replace('-', '+')
                .Replace('_', '/')
                .Replace('.', '=');

        var remainder = normalized.Length % 4;
        if (remainder != 0)
        {
            normalized += new string('=', 4 - remainder);
        }

        return Convert.FromBase64String(normalized);
    }
}
