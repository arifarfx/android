package com.lareward.app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RewardedAdManager(private val context: Context) {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val TAG = "RewardedAdManager"

    companion object {
        // GANTI DENGAN ID UNIT IKLAN REWARDS ANDA YANG SEBENARNYA
        private const val AD_UNIT_ID = "ca-app-pub-7383139686764923/6728557231"
    }

    fun loadAd() {
        if (isLoading) return
        isLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.d(TAG, "Ad failed to load: ${loadAdError.message}")
                rewardedAd = null
                isLoading = false
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Ad was loaded.")
                rewardedAd = ad
                isLoading = false
            }
        })
    }
    
    // PERBAIKAN: Fungsi showAd sekarang menerima 'rewardType'
    fun showAd(
        activity: Activity,
        rewardType: String, // "UNLOCK_QUOTA" atau "DOUBLE_POINTS"
        onRewarded: () -> Unit,
        onAdFailedToShow: (String) -> Unit
    ) {
        if (rewardedAd == null) {
            onAdFailedToShow("Iklan belum siap. Coba lagi nanti.")
            loadAd() // Coba muat iklan lagi untuk kesempatan berikutnya
            return
        }

        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadAd() // Selalu muat iklan baru setelah yang lama ditutup
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                onAdFailedToShow(adError.message)
                loadAd()
            }
        }

        rewardedAd?.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward. Reward Type Requested: $rewardType")

            // PERBAIKAN: Logika dibedakan berdasarkan jenis reward
            if (rewardType == "UNLOCK_QUOTA") {
                // Jika tujuannya menambah kuota, panggil API backend
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            val requestBody = mapOf("action" to "unlock_quiz")
                            ApiClient.api.claimRewardedAdBonus(requestBody)
                        }
                        if (response.isSuccessful && response.body()?.status == "success") {
                            onRewarded() // Panggil callback sukses jika API berhasil
                        } else {
                            onAdFailedToShow("Gagal mencatat hadiah: ${response.body()?.message}")
                        }
                    } catch (e: Exception) {
                        onAdFailedToShow("Kesalahan jaringan: ${e.message}")
                    }
                }
            } else if (rewardType == "DOUBLE_POINTS") {
                // Jika hanya untuk poin ganda, TIDAK perlu panggil API.
                // Langsung panggil callback sukses untuk mengaktifkan UI di web.
                onRewarded()
            }
        }
    }

    fun destroy() {
        rewardedAd = null
    }
}
