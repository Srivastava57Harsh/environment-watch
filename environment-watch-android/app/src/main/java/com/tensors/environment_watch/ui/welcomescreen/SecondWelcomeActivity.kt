package com.tensors.environment_watch.ui.welcomescreen

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import com.tensors.environment_watch.R
import com.tensors.environment_watch.ui.mainscreen.MainScreen
import kotlinx.android.synthetic.main.activity_welcome2.*

class SecondWelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome2)

        next_welcome.setOnClickListener {
            startActivity(Intent(this, ThirdWelcomeScreen::class.java))
        }
    }
}