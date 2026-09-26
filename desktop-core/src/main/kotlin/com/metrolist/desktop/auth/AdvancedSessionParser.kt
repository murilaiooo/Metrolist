package com.metrolist.desktop.auth

import com.metrolist.innertube.utils.parseCookieString

object AdvancedSessionParser {
    private const val COOKIE = "***INNERTUBE COOKIE*** ="
    private const val VISITOR = "***VISITOR DATA*** ="
    private const val DATASYNC = "***DATASYNC ID*** ="
    private const val AUTH_USER = "***AUTH USER*** ="
    private const val ACCOUNT_NAME = "***ACCOUNT NAME*** ="
    private const val ACCOUNT_EMAIL = "***ACCOUNT EMAIL*** ="
    private const val ACCOUNT_HANDLE = "***ACCOUNT CHANNEL HANDLE*** ="

    fun parse(text: String): AuthSession {
        val values = mutableMapOf<String, String>()

        text.lineSequence().forEach { line ->
            when {
                line.startsWith(COOKIE) -> values[COOKIE] = line.substringAfter(COOKIE).trim()
                line.startsWith(VISITOR) -> values[VISITOR] = line.substringAfter(VISITOR).trim()
                line.startsWith(DATASYNC) -> values[DATASYNC] = line.substringAfter(DATASYNC).trim()
                line.startsWith(AUTH_USER) -> values[AUTH_USER] = line.substringAfter(AUTH_USER).trim()
                line.startsWith(ACCOUNT_NAME) -> values[ACCOUNT_NAME] = line.substringAfter(ACCOUNT_NAME).trim()
                line.startsWith(ACCOUNT_EMAIL) -> values[ACCOUNT_EMAIL] = line.substringAfter(ACCOUNT_EMAIL).trim()
                line.startsWith(ACCOUNT_HANDLE) -> values[ACCOUNT_HANDLE] = line.substringAfter(ACCOUNT_HANDLE).trim()
            }
        }

        val cookie = values[COOKIE].orEmpty()
        require("SAPISID" in parseCookieString(cookie)) { "Advanced session is missing SAPISID" }

        val visitorData = values[VISITOR].orEmpty()
        require(visitorData.isNotBlank()) { "Advanced session is missing visitor data" }

        val dataSyncId = values[DATASYNC].orEmpty().substringBefore("||")
        val authUser = values[AUTH_USER].orEmpty().filter(Char::isDigit).ifBlank { "0" }

        return AuthSession(
            cookie = cookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
            authUser = authUser,
            accountName = values[ACCOUNT_NAME].nullIfBlank(),
            accountEmail = values[ACCOUNT_EMAIL].nullIfBlank(),
            accountChannelHandle = values[ACCOUNT_HANDLE].nullIfBlank(),
        )
    }

    private fun String?.nullIfBlank(): String? = this?.takeIf(String::isNotBlank)
}
