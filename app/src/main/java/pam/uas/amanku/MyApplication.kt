package pam.uas.amanku

import com.google.firebase.FirebaseApp
import androidx.multidex.MultiDexApplication

class MyApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}