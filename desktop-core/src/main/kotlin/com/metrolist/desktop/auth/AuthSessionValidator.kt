package com.metrolist.desktop.auth

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeAccount

data class ValidatedAuthSession(
    val session: AuthSession,
    val accounts: List<YouTubeAccount>,
)

class AuthSessionValidator {
    suspend fun validate(session: AuthSession): ValidatedAuthSession {
        YouTube.cookie = session.cookie
        YouTube.visitorData = session.visitorData
        YouTube.dataSyncId = session.dataSyncId
        YouTube.authUser = session.authUser
        YouTube.useLoginForBrowse = true

        val accounts = YouTube.accountsList().getOrElse { emptyList() }
        val accountInfo = YouTube.accountInfo().getOrThrow()

        return ValidatedAuthSession(
            session = session.copy(
                accountName = accountInfo.name,
                accountEmail = accountInfo.email,
                accountChannelHandle = accountInfo.channelHandle,
            ),
            accounts = accounts,
        )
    }
}
