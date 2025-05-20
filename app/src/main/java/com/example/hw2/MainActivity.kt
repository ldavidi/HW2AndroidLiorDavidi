package com.example.hw2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.abs
import kotlin.random.Random

class MainActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    // UI
    private lateinit var gameContainer: FrameLayout
    private lateinit var spaceship: ImageView
    private lateinit var leftButton: ImageButton
    private lateinit var rightButton: ImageButton
    private lateinit var lifeIcons: List<ImageView>
    private lateinit var tvCoins: TextView
    private lateinit var tvDistance: TextView

    // Sensors
    private lateinit var sensorMgr: SensorManager

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    // Launch options
    private var mode: String = "buttons"
    private var speed: String = "slow"
    private var isSensorMode = false

    // Game state
    private val laneCount = 5
    private var currentLane = laneCount / 2
    private var lives = 3
    private var coinsCollected = 0
    private var distanceCounter = 0

    // Tilt control
    private val tiltThresholdX = 5f
    private val tiltResetThreshold = 2f
    private var canMoveHorizontally = true

    // Spawn settings (mutable)
    private var asteroidInterval = 1500L
    private var coinInterval     = 2000L
    private val fallDuration     = 4000L
    private val objectSize       = 300

    // Handlers
    private val asteroidHandler = Handler(Looper.getMainLooper())
    private var asteroidSpawner: Runnable? = null

    private val coinHandler = Handler(Looper.getMainLooper())
    private var coinSpawner: Runnable? = null

    private val odometerHandler = Handler(Looper.getMainLooper())

    // Sounds
    private lateinit var hitSound: MediaPlayer
    private lateinit var coinSound: MediaPlayer

    // Pause flag
    private var isGamePaused = false

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Read launch options
        mode  = intent.getStringExtra("mode")  ?: mode
        speed = intent.getStringExtra("speed") ?: speed
        isSensorMode = (mode == "sensors")

        // 2) Adjust intervals for speed
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

        // 3) Bind views
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

        // 4) Text visibility
        tvCoins.setTextColor(Color.WHITE)
        tvDistance.setTextColor(Color.WHITE)

        // 5) Load sounds
        hitSound  = MediaPlayer.create(this, R.raw.hit)
        coinSound = MediaPlayer.create(this, R.raw.coin)

        // 6) Sensor manager
        sensorMgr = getSystemService(SENSOR_SERVICE) as SensorManager

        // 7) Show/hide buttons
        if (isSensorMode) {
            leftButton.visibility  = View.GONE
            rightButton.visibility = View.GONE
        }

        // 8) Button handlers
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

        // 9) Location client & permission
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestLocation()

        // 10) Start game loops after layout
        gameContainer.post {
            moveSpaceshipToLane(currentLane)
            startAsteroidSpawner()
            startCoinSpawner()
        }

        // 11) Odometer + UI
        startOdometer()
        updateLivesUI()
        updateCoinUI()
    }

    // Request fine location permission / fetch last known location
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun requestLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    lastLat = it.latitude
                    lastLon = it.longitude
                }
            }
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            results.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            requestLocation()
        }
    }

    // Resume: sensors + loops
    override fun onResume() {
        super.onResume()
        if (isSensorMode) {
            sensorMgr.registerListener(
                this,
                sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        isGamePaused = false
        asteroidSpawner?.let { asteroidHandler.post(it) }
        coinSpawner?.let     { coinHandler.post(it) }
        startOdometer()
    }

    // Pause: unregister + stop loops
    override fun onPause() {
        super.onPause()
        if (isSensorMode) sensorMgr.unregisterListener(this)
        isGamePaused = true
        asteroidSpawner?.let { asteroidHandler.removeCallbacks(it) }
        coinSpawner?.let     { coinHandler.removeCallbacks(it) }
        odometerHandler.removeCallbacksAndMessages(null)
    }

    // Sensor tilt events
    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]
        val y = e.values[1]

        // reset horizontal movement
        if (abs(x) < tiltResetThreshold) {
            canMoveHorizontally = true
        }

        // stepwise left/right
        if (isSensorMode && canMoveHorizontally) {
            when {
                x < -tiltThresholdX && currentLane < laneCount - 1 -> {
                    currentLane++; moveSpaceshipToLane(currentLane)
                    canMoveHorizontally = false
                }
                x > tiltThresholdX && currentLane > 0 -> {
                    currentLane--; moveSpaceshipToLane(currentLane)
                    canMoveHorizontally = false
                }
            }
        }

        // forward tilt → fast; back → slow
        if (y < -3f) {
            asteroidInterval = 800L
            coinInterval     = 1200L
        } else if (y > 3f) {
            asteroidInterval = 1500L
            coinInterval     = 2000L
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Move ship to lane
    private fun moveSpaceshipToLane(lane: Int) {
        val laneW = gameContainer.width / laneCount
        val x = lane * laneW + (laneW - spaceship.width) / 2
        (spaceship.layoutParams as FrameLayout.LayoutParams).apply {
            gravity    = Gravity.BOTTOM
            leftMargin = x
        }.also { spaceship.layoutParams = it }
    }

    // Spawn asteroids
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

    // Spawn coins
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

    // Generic falling + collision
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
        val lane = Random.nextInt(laneCount)
        val laneW = gameContainer.width / laneCount
        (iv.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = lane * laneW + (laneW - objectSize) / 2
            topMargin  = 0
        }
        gameContainer.addView(iv)

        val startMs = System.currentTimeMillis()
        val step   = 30L
        val h      = Handler(Looper.getMainLooper())
        h.post(object : Runnable {
            override fun run() {
                if (isGamePaused) {
                    h.postDelayed(this, step); return
                }
                val prog = (System.currentTimeMillis() - startMs).toFloat() / fallDuration
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

    // AABB collision
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

    // Handle asteroid hit
    private fun handleHit() {
        if (isGamePaused) return
        vibrate(100)
        playFast(hitSound)
        lives--
        if (lives <= 0) {
            quickToast("Game Over!")
            // record score
            val versionKey = "${mode}_${speed}"
            ScoreManager.addScore(
                versionKey,
                Score(
                    coins   = coinsCollected,
                    seconds = distanceCounter,
                    lat     = lastLat,
                    lon     = lastLon
                )
            )
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
