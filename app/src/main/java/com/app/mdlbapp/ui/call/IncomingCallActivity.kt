package com.app.mdlbapp.ui.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.app.mdlbapp.MainActivity

/**
 * Супер-лёгкая активити, которую система показывает ПОВЕРХ замка.
 * Она сразу «будит» экран и переправляет в MainActivity к твоему UI звонка.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // максимально рано — будим экран
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        super.onCreate(savedInstanceState)

        // простенький лоадер на случай доли секунды
        setContent {
            Box(Modifier.Companion.fillMaxSize(), contentAlignment = Alignment.Companion.Center) {
                CircularProgressIndicator()
            }
        }

        // запускаем твою MainActivity с openCall=true (как было), чтобы там показался экран звонка
        val forward = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtras(intent?.extras ?: Bundle())
            .putExtra("openCall", true)

        startActivity(forward)
        // эта активити — просто «трамплин» для фуллскрина
        finish()
    }
}