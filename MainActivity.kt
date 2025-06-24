package com.lareward.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var googleSignInClient: GoogleSignInClient
    private val TAG = "MainActivity"

    // Menggunakan Activity Result API yang modern
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.let {
                    Log.d(TAG, "Google Sign-In Sukses. Mengirim token ke server.")
                    sendLoginRequest(
                        idToken = it.idToken ?: "",
                        email = it.email ?: "",
                        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                    )
                } ?: callJsOnError("Gagal mendapatkan akun dari Google.")
            } catch (e: ApiException) {
                Log.e(TAG, "signInResult:failed code=" + e.statusCode)
                callJsOnError("Login Google Gagal: ${e.message}")
            }
        } else {
            Log.w(TAG, "Login dibatalkan oleh pengguna.")
        }
    }

    // Jembatan dari JavaScript ke Kode Kotlin
    inner class WebAppInterface {
        @JavascriptInterface
        fun startGoogleSignIn() {
            runOnUiThread {
                signIn()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. Atur layout untuk Activity ini
        setContentView(R.layout.activity_main)

        // 2. SEGERA inisialisasi semua komponen UI setelah setContentView
        //    Ini untuk memperbaiki crash UninitializedPropertyAccessException.
        webView = findViewById(R.id.webview)

        // 3. Setup komponen lain seperti Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.WEB_CLIENT_ID)) // Pastikan string ini ada di res/values/strings.xml
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 4. Lakukan setup pada WebView SETELAH diinisialisasi
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        // 5. PERBAIKAN LOGIKA: Ambil URL dari intent yang dikirim oleh LoginActivity.
        //    Jika tidak ada URL dari intent, baru gunakan URL login default.
        val urlToLoad = intent.getStringExtra("EXTRA_URL") ?: "https://app.lareward.com/login"
        Log.d(TAG, "Memuat URL: $urlToLoad")
        webView.loadUrl(urlToLoad)
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun sendLoginRequest(idToken: String, email: String, deviceId: String) {
        if (idToken.isEmpty() || email.isEmpty()) {
            callJsOnError("Token atau Email dari Google kosong.")
            return
        }

        val client = OkHttpClient()

        // Backend mengharapkan JSON, jadi kita kirim JSON.
        val json = JSONObject()
        json.put("google_id_token", idToken)
        json.put("email", email)
        json.put("device_id", deviceId)
        json.put("platform", "android")

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://app.lareward.com/api/v1/auth") // URL ke endpoint API
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "OkHttp Failure: ", e)
                callJsOnError("Koneksi ke server gagal.")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                try {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        val jwt = jsonResponse.optString("jwt", "")
                        if (jwt.isNotEmpty()) {
                            // Jika berhasil login dari dalam webview, muat halaman dashboard.
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Login Server Berhasil!", Toast.LENGTH_LONG).show()
                                // Muat ulang halaman dashboard dengan token baru jika diperlukan,
                                // atau biarkan web app yang menangani redirect.
                                // Untuk saat ini, memuat dashboard secara eksplisit sudah cukup.
                                webView.loadUrl("https://app.lareward.com/dashboard")
                            }
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Token JWT kosong dari server.")
                            callJsOnError(errorMessage)
                        }
                    } else {
                        val errorFromServer = JSONObject(responseBody ?: "{}").optString("message", "Respons server tidak berhasil.")
                        callJsOnError(errorFromServer)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "JSON Parsing error: ", e)
                    callJsOnError("Format respons dari server salah.")
                }
            }
        })
    }

    // Fungsi bantuan untuk memanggil JavaScript jika terjadi error
    private fun callJsOnError(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            // Anda bisa memanggil fungsi JS di web untuk menampilkan error jika perlu
            // webView.evaluateJavascript("window.onGoogleSignInError('$message');", null)
        }
    }
}
