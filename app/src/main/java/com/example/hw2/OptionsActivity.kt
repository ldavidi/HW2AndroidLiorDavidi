package com.example.hw2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class OptionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)

        // Leaderboards
        findViewById<Button>(R.id.btn_leaderboards).setOnClickListener {
            startActivity(Intent(this, LeaderboardsActivity::class.java))
        }

        // Two-button Slow (current behavior)
        findViewById<Button>(R.id.btn_buttons_slow).setOnClickListener {
            startGame(mode = "buttons", speed = "slow")
        }

        // Two-button Fast
        findViewById<Button>(R.id.btn_buttons_fast).setOnClickListener {
            startGame(mode = "buttons", speed = "fast")
        }

        // Sensor mode
        findViewById<Button>(R.id.btn_sensor_mode).setOnClickListener {
            startGame(mode = "sensors", speed = "slow")
        }
    }

    private fun startGame(mode: String, speed: String) {
        Intent(this, MainActivity::class.java).apply {
            putExtra("mode", mode)
            putExtra("speed", speed)
            startActivity(this)
        }
    }
}
