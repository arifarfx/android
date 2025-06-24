package com.lareward.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.MobileAds

class QuizWebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var rewardedAdManager: RewardedAdManager
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_webview)

        MobileAds.initialize(this)
        rewardedAdManager = RewardedAdManager(this)
        rewardedAdManager.loadAd()

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progressBar)
        
        setupWebView()

        // LANGKAH 1: Tambahkan parameter ke URL untuk menandai sebagai aplikasi Android
        val url = "https://app.lareward.com/members/quizz?is_android=true"
        webView.loadUrl(url)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(false)
        }

        webView.webChromeClient = object : WebChromeClient() {
             override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d("WebViewConsole", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                return true
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                super.onPageFinished(view, url)
            }

            // LANGKAH 2: Tangkap skema URL kustom 'lareward://'
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                
                if (url.startsWith("lareward://")) {
                    val uri = Uri.parse(url)
                    val rewardType = uri.getQueryParameter("type")
                    
                    Log.d("WebViewAction", "Intercepted custom URL: $url")

                    if (rewardType == "unlock_quota") {
                        handleUnlockQuotaAd()
                    } else if (rewardType == "double_points") {
                        handleDoublePointsAd()
                    }
                    
                    return true // Penting: Mengindikasikan bahwa kita telah menangani URL ini
                }
                
                // Untuk semua URL lain (http, https), biarkan WebView menanganinya
                return false
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (errorResponse?.statusCode == 401) {
                    // Penanganan sesi tidak valid (tetap sama)
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }
        
        // KITA TIDAK MEMBUTUHKAN JAVASCRIPTINTERFACE LAGI DENGAN METODE INI
        // webView.addJavascriptInterface(...)
    }

    // Fungsi-fungsi ini sekarang dipanggil dari shouldOverrideUrlLoading
    private fun handleUnlockQuotaAd() {
        runOnUiThread {
            rewardedAdManager.showAd(
                activity = this,
                rewardType = "UNLOCK_QUOTA",
                onRewarded = {
                    webView.evaluateJavascript("if(window.onQuotaUnlocked) window.onQuotaUnlocked();", null)
                },
                onAdFailedToShow = { message ->
                    Toast.makeText(this, "Gagal menampilkan iklan: $message", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun handleDoublePointsAd() {
        runOnUiThread {
            rewardedAdManager.showAd(
                activity = this,
                rewardType = "DOUBLE_POINTS",
                onRewarded = {
                    webView.evaluateJavascript("if(window.onDoublePointsActivated) window.onDoublePointsActivated();", null)
                },
                onAdFailedToShow = { message ->
                    Toast.makeText(this, "Gagal menampilkan iklan: $message", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroy() {
        rewardedAdManager.destroy()
        super.onDestroy()
    }
}
