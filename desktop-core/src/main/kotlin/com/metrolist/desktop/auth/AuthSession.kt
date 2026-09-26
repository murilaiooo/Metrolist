package com.metrolist.desktop.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val accountChannelHandle: String? = null,
)
