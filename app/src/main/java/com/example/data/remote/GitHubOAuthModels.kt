package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceCodeResponse(
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_uri") val verificationUri: String,
    @Json(name = "verification_uri_complete") val verificationUriComplete: String? = null,
    @Json(name = "expires_in") val expiresIn: Int = 900,
    val interval: Int = 5
)

@JsonClass(generateAdapter = true)
data class DeviceTokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
    @Json(name = "error_uri") val errorUri: String? = null,
    val interval: Int? = null
)
