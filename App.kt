package com.lareward.app

import android.app.Application
import com.google.android.gms.ads.MobileAds

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Inisialisasi AdMob adalah hal yang benar untuk dilakukan di sini
        // agar siap saat aplikasi dibutuhkan.
        MobileAds.initialize(this) {}
        
        // HAPUS BARIS INI KARENA AuthManager TIDAK MEMILIKI FUNGSI init
        // AuthManager.init(this)
    }
}