package com.lareward.app

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    @SerializedName("google_id_token")
    val googleIdToken: String
)

data class AuthResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("token")
    val token: String?,
    @SerializedName("message")
    val message: String?
)

/**
 * Data class generik untuk menangani respons API yang umum,
 * seperti callback AdMob.
 */
data class GenericApiResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String?
)
