package com.lareward.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton // PERBAIKAN: Import yang benar untuk tombol kustom
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    // PERBAIKAN: Tipe variabel diubah menjadi MaterialButton agar cocok dengan XML
    private lateinit var btnSignIn: MaterialButton
    private lateinit var progressBar: ProgressBar
    private val TAG = "LoginActivity"

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleUiState(false) // Selalu sembunyikan loading setelah kembali
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { idToken ->
                    handleUiState(true, "Memverifikasi...")
                    handleGoogleToken(idToken)
                } ?: handleUiState(false, "Gagal mendapatkan token dari Google.")
            } catch (e: ApiException) {
                handleUiState(false, "Login Google Gagal: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Cek jika user sudah pernah login sebelumnya
        if (AuthPrefs.getInstance(this).isLoggedIn()) {
            val jwt = AuthPrefs.getInstance(this).jwtToken
            AuthManager.jwtToken = jwt
            startWebViewActivity(jwt ?: "")
            finish()
            return
        }

        // Inisialisasi UI components menggunakan findViewById
        btnSignIn = findViewById(R.id.signInButton) // PERBAIKAN: ID disesuaikan dengan XML terbaru
        progressBar = findViewById(R.id.progressBar)

        // Konfigurasi Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.WEB_CLIENT_ID)) // Menggunakan nama resource Anda
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnSignIn.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        handleUiState(true, "Membuka pilihan akun Google...")
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun handleGoogleToken(idToken: String) {
        val client = OkHttpClient()
        val jsonPayload = JSONObject()
        jsonPayload.put("google_id_token", idToken)

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://app.lareward.com/api/v1/auth")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handleUiState(false, "Koneksi ke server gagal.")
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                try {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.optString("status") == "success") {
                            val jwt = jsonResponse.optString("token", "")
                            if (jwt.isNotEmpty()) {
                                AuthPrefs.getInstance(this@LoginActivity).jwtToken = jwt
                                AuthManager.jwtToken = jwt
                                startWebViewActivity(jwt)
                                finish()
                            } else {
                                handleUiState(false, "Respons token kosong dari server.")
                            }
                        } else {
                            handleUiState(false, "Autentikasi gagal: " + jsonResponse.optString("message", ""))
                        }
                    } else {
                        handleUiState(false, "Error: " + JSONObject(responseBody ?: "{}").optString("message", "Respons server tidak berhasil."))
                    }
                } catch (e: Exception) {
                    handleUiState(false, "Format respons dari server salah.")
                }
            }
        })
    }

    private fun startWebViewActivity(jwt: String) {
        if (jwt.isEmpty()) {
            handleUiState(false, "Token tidak valid untuk memulai sesi.")
            return
        }
        val intent = Intent(this, MainActivity::class.java)
        val url = "https://app.lareward.com/native_login.php?jwt=$jwt"
        intent.putExtra("EXTRA_URL", url)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun handleUiState(isLoading: Boolean, message: String? = null) {
        runOnUiThread {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnSignIn.isEnabled = !isLoading
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }
}
