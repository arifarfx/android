package com.lareward.app

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    // Endpoint untuk login native
    @POST("api/v1/auth")
    suspend fun authenticate(@Body request: AuthRequest): Response<AuthResponse>

    // TAMBAHKAN ENDPOINT INI UNTUK ADMOB REWARDS
    // Ini akan memanggil endpoint /api/v1/rewards.php di backend Anda
    @POST("api/v1/rewards")
    suspend fun claimRewardedAdBonus(@Body request: Map<String, String>): Response<GenericApiResponse>
}
