namespace Metrolist.AuthHelper;

internal sealed record AuthResult(
    string Cookie,
    string VisitorData,
    string DataSyncId,
    string AuthUser
);
