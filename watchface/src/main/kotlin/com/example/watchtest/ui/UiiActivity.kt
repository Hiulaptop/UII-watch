package com.example.watchtest.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** True while the app's screens are in front; read from Java by the fall code. */
object UiVisibility {
    @Volatile @JvmField var visible = false
}

/** The only launcher activity: hosts every screen of the app. */
class UiiActivity : ComponentActivity() {
    override fun onStart() { super.onStart(); UiVisibility.visible = true }
    override fun onStop() { UiVisibility.visible = false; super.onStop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UiiApp() }
    }
}
