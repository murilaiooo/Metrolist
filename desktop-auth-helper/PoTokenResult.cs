namespace Metrolist.AuthHelper;

internal sealed record PoTokenResult(
    string PlayerRequestToken,
    string StreamingDataToken,
    string VisitorData
);
