package com.metrolist.desktop.auth

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.YouTubeAccount

data class ValidatedAuthSession(
    val session: AuthSession,
    val accounts: List<YouTubeAccount>,
)

class AuthSessionValidator(
    private val accountLoader: suspend () -> Pair<List<YouTubeAccount>, AccountInfo> = {
        YouTube.accountsList().getOrElse { emptyList() } to
            YouTube.accountInfo().getOrThrow()
    },
) {
    suspend fun validate(session: AuthSession): ValidatedAuthSession {
        val previousCookie = YouTube.cookie
        val previousVisitorData = YouTube.visitorData
        val previousDataSyncId = YouTube.dataSyncId
        val previousAuthUser = YouTube.authUser
        val previousUseLogin = YouTube.useLoginForBrowse

        try {
            YouTube.cookie = session.cookie
            YouTube.visitorData = session.visitorData
            YouTube.dataSyncId = session.dataSyncId
            YouTube.authUser = session.authUser
            YouTube.useLoginForBrowse = true

            val (accounts, accountInfo) = accountLoader()

            return ValidatedAuthSession(
                session = session.copy(
                    accountName = accountInfo.name,
                    accountEmail = accountInfo.email,
                    accountChannelHandle = accountInfo.channelHandle,
                ),
                accounts = accounts,
            )
        } catch (error: Throwable) {
            YouTube.cookie = previousCookie
            YouTube.visitorData = previousVisitorData
            YouTube.dataSyncId = previousDataSyncId
            YouTube.authUser = previousAuthUser
            YouTube.useLoginForBrowse = previousUseLogin
            throw error
        }
    }
}
