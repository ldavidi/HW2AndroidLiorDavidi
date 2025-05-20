package com.example.hw1

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.*
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.random.Random

class MainActivity : AppCompatActivity(), SensorEventListener {

    // UI
    private lateinit var gameContainer: FrameLayout
    private lateinit var spaceship: ImageView
    private lateinit var leftButton: ImageButton
    private lateinit var rightButton: ImageButton
    private lateinit var lifeIcons: List<ImageView>
    private lateinit var tvCoins: TextView
    private lateinit var tvDistance: TextView

    // Sensor
    private lateinit var sensorMgr: SensorManager

    // State
    private val laneCount = 5
    private var currentLane = laneCount / 2
    private var lives = 3
    private var coinsCollected = 0
    private var distanceCounter = 0
    // how far you must tilt to trigger a lane change:
    private val tiltThresholdX = 5f
    // how close to level you must return before you can move again:
    private val tiltResetThreshold = 2f
    // guard to prevent repeated moves while still tilted:
    private var canMoveHorizontally = true

    // Spawn settings (mutable for slow/fast)
    private var asteroidInterval = 1500L
    private var coinInterval     = 2000L
    private val fallDuration     = 4000L
    private val objectSize       = 300

    // Mode
    private var isSensorMode = false

    // Handlers & Runnables
    private val asteroidHandler = Handler(Looper.getMainLooper())
    private var asteroidSpawner: Runnable? = null

    private val coinHandler = Handler(Looper.getMainLooper())
    private var coinSpawner: Runnable? = null

    private val odometerHandler = Handler(Looper.getMainLooper())

    // Sounds
    private lateinit var hitSound: MediaPlayer
    private lateinit var coinSound: MediaPlayer

    // Pause initializer
    private var isGamePaused = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ── 1) Read “mode” & “speed” extras ───────────────────────
        val mode  = intent.getStringExtra("mode")  ?: "buttons"
        val speed = intent.getStringExtra("speed") ?: "slow"
        isSensorMode = (mode == "sensors")

        // ── 2) Default to slow, override if “fast” selected ───────
        when (speed) {
            "fast" -> {
                asteroidInterval = 800L
                coinInterval     = 1200L
            }
            else -> {
                asteroidInterval = 1500L
                coinInterval     = 2000L
            }
        }

        // ── 3) Bind Views ─────────────────────────────────────────
        gameContainer = findViewById(R.id.game_container)
        spaceship     = findViewById(R.id.spaceship)
        leftButton    = findViewById(R.id.left_button)
        rightButton   = findViewById(R.id.right_button)
        lifeIcons     = listOf(
            findViewById(R.id.life1),
            findViewById(R.id.life2),
            findViewById(R.id.life3)
        )
        tvCoins    = findViewById(R.id.tv_coins)
        tvDistance = findViewById(R.id.tv_distance)

        // Make overlay text visible on dark bg
        tvCoins.setTextColor(Color.WHITE)
        tvDistance.setTextColor(Color.WHITE)

        // ── 4) Load Sounds ────────────────────────────────────────
        hitSound  = MediaPlayer.create(this, R.raw.hit)
        coinSound = MediaPlayer.create(this, R.raw.coin)

        // ── 5) Sensor setup ───────────────────────────────────────
        sensorMgr = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // ── 6) Show/hide buttons ───────────────────────────────────
        if (isSensorMode) {
            leftButton.visibility  = View.GONE
            rightButton.visibility = View.GONE
        } else {
            leftButton.visibility  = View.VISIBLE
            rightButton.visibility = View.VISIBLE
        }

        // ── 7) Button handlers (only if !sensor) ─────────────────
        leftButton.setOnClickListener {
            if (!isSensorMode && currentLane > 0) {
                currentLane--
                moveSpaceshipToLane(currentLane)
            }
        }
        rightButton.setOnClickListener {
            if (!isSensorMode && currentLane < laneCount - 1) {
                currentLane++
                moveSpaceshipToLane(currentLane)
            }
        }

        // ── 8) Kick off game loops after layout ───────────────────
        gameContainer.post {
            moveSpaceshipToLane(currentLane)
            startAsteroidSpawner()
            startCoinSpawner()
        }

        // ── 9) Start odometer & initial UI ───────────────────────
        startOdometer()
        updateLivesUI()
        updateCoinUI()
    }

    // ── Sensor registration ───────────────────────────────────
    override fun onResume() {
        super.onResume()
        if (isSensorMode) {
            sensorMgr.registerListener(
                this,
                sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        startOdometer()
        asteroidSpawner?.let { asteroidHandler.post(it) }
        coinSpawner?.let     { coinHandler.post(it) }
    }

    override fun onPause() {
        super.onPause()
        if (isSensorMode) sensorMgr.unregisterListener(this)
        isGamePaused = true
        asteroidSpawner?.let { asteroidHandler.removeCallbacks(it) }
        coinSpawner?.let     { coinHandler.removeCallbacks(it) }
        odometerHandler.removeCallbacksAndMessages(null)
    }

    // ── Handle tilt events ────────────────────────────────────
    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]  // left/right tilt
        val y = e.values[1]  // forward/back tilt

        // 1) reset ability to move once phone is nearly level:
        if (abs(x) < tiltResetThreshold) {
            canMoveHorizontally = true
        }

        // 2) step‐wise horizontal movement:
        if (isSensorMode && canMoveHorizontally) {
            when {
                x < -tiltThresholdX && currentLane < laneCount - 1 -> {
                    currentLane++
                    moveSpaceshipToLane(currentLane)
                    canMoveHorizontally = false
                }
                x > tiltThresholdX && currentLane > 0 -> {
                    currentLane--
                    moveSpaceshipToLane(currentLane)
                    canMoveHorizontally = false
                }
            }
        }

        // 3) speed control (as before):
        if (y < -3f) {            // tilt toward you → fast
            asteroidInterval = 500L
            coinInterval     = 650L
        } else if (y > 3f) {      // tilt away → slow
            asteroidInterval = 1500L
            coinInterval     = 2000L
        }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    // ── Move ship to lane index ───────────────────────────────
    private fun moveSpaceshipToLane(lane: Int) {
        val laneW = gameContainer.width / laneCount
        val targetX = lane * laneW + (laneW - spaceship.width) / 2
        (spaceship.layoutParams as FrameLayout.LayoutParams).apply {
            gravity    = Gravity.BOTTOM
            leftMargin = targetX
        }.also { spaceship.layoutParams = it }
    }

    // ── Asteroid spawner ──────────────────────────────────────
    private fun startAsteroidSpawner() {
        asteroidSpawner = object : Runnable {
            override fun run() {
                if (!isGamePaused) {
                    spawnFalling(R.drawable.asteroid) { handleHit() }
                    asteroidHandler.postDelayed(this, asteroidInterval)
                }
            }
        }.also { asteroidHandler.post(it) }
    }

    // ── Coin spawner ───────────────────────────────────────────
    private fun startCoinSpawner() {
        coinSpawner = object : Runnable {
            override fun run() {
                if (!isGamePaused) {
                    spawnFalling(R.drawable.coin) {
                        coinsCollected++
                        updateCoinUI()
                        playFast(coinSound)
                    }
                    coinHandler.postDelayed(this, coinInterval)
                }
            }
        }.also { coinHandler.post(it) }
    }

    // ── Generic falling + collision ───────────────────────────
    private fun spawnFalling(
        @DrawableRes resId: Int,
        onCollision: () -> Unit
    ) {
        val iv = ImageView(this).apply {
            setImageResource(resId)
            if (resId == R.drawable.coin) {
                setColorFilter(Color.parseColor("#FFD700"), PorterDuff.Mode.SRC_IN)
            }
            layoutParams = FrameLayout.LayoutParams(objectSize, objectSize)
        }
        // lane X
        val lane = Random.nextInt(laneCount)
        val laneW = gameContainer.width / laneCount
        (iv.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = lane * laneW + (laneW - objectSize) / 2
            topMargin  = 0
        }
        gameContainer.addView(iv)

        // animate fall
        val start = System.currentTimeMillis()
        val step  = 30L
        val h     = Handler(Looper.getMainLooper())
        h.post(object : Runnable {
            override fun run() {
                if (isGamePaused) {
                    h.postDelayed(this, step); return
                }
                val prog = (System.currentTimeMillis() - start).toFloat() / fallDuration
                (iv.layoutParams as FrameLayout.LayoutParams).apply {
                    topMargin = (prog * gameContainer.height).toInt()
                }.also { iv.layoutParams = it }

                if (checkCollision(iv)) {
                    onCollision()
                    gameContainer.removeView(iv)
                } else if (prog < 1f) {
                    h.postDelayed(this, step)
                } else {
                    gameContainer.removeView(iv)
                }
            }
        })
    }

    // ── AABB collision ────────────────────────────────────────
    private fun checkCollision(iv: ImageView): Boolean {
        val ivP = iv.layoutParams as FrameLayout.LayoutParams
        val spP = spaceship.layoutParams as FrameLayout.LayoutParams
        val ivX = ivP.leftMargin + iv.width/2
        val ivY = ivP.topMargin + iv.height/2
        val spX = spP.leftMargin + spaceship.width/2
        val spY = gameContainer.height - spaceship.height/2 - 100
        return abs(ivX - spX) < spaceship.width*0.6 &&
                abs(ivY - spY) < spaceship.height*0.6
    }

    // ── On asteroid hit ───────────────────────────────────────
    private fun handleHit() {
        if (isGamePaused) return
        vibrate(100)
        playFast(hitSound)
        lives--
        if (lives <= 0) {
            quickToast("Game Over!")
            resetGame()
        } else {
            quickToast("Ouch! Lives: $lives")
        }
        updateLivesUI()
    }

    private fun resetGame() {
        lives            = 3
        coinsCollected   = 0
        distanceCounter  = 0
        updateLivesUI()
        updateCoinUI()
        tvDistance.text = "Distance: 0"
    }

    private fun updateLivesUI() {
        lifeIcons.forEachIndexed { i, iv ->
            iv.alpha = if (i < lives) 1f else 0.2f
        }
    }
    private fun updateCoinUI() {
        tvCoins.text = "Coins: $coinsCollected"
    }

    // ── Odometer +100 each sec ───────────────────────────────
    private fun startOdometer() {
        odometerHandler.post(object : Runnable {
            override fun run() {
                if (!isGamePaused) {
                    distanceCounter += 100
                    tvDistance.text = "Distance: $distanceCounter"
                }
                odometerHandler.postDelayed(this, 1000L)
            }
        })
    }

    // ── Play at 1.5× speed on API23+ ─────────────────────────
    private fun playFast(mp: MediaPlayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mp.playbackParams = PlaybackParams().setSpeed(1.5f)
        }
        if (mp.isPlaying) mp.seekTo(0)
        mp.start()
    }

    // ── Quick toast (~500ms) ─────────────────────────────────
    private fun quickToast(msg: String) {
        val t = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        t.show()
        Handler(Looper.getMainLooper()).postDelayed({ t.cancel() }, 500L)
    }

    // ── Vibrate ──────────────────────────────────────────────
    private fun vibrate(ms: Long) {
        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrate(ms)
            }
        }
    }
}
