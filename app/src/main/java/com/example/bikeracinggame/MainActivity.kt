
package com.example.bikeracinggame

import android.annotation.SuppressLint
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    // ===============================
    // XML Views
    // ===============================

    private lateinit var ivRoad1: ImageView
    private lateinit var ivRoad2: ImageView

    private lateinit var ivBike: ImageView
    private lateinit var ivRedBike: ImageView
    private lateinit var ivRedBike2: ImageView

    private lateinit var tvScore: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvGameOver: TextView

    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnStart: Button

    private lateinit var gameMenuLayout: View

    // ===============================
    // Music Variables
    // ===============================

    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var isSynthPlaying = false
    private var synthThread: Thread? = null

    // ===============================
    // Game Variables
    // ===============================

    private val handler = Handler(Looper.getMainLooper())

    private var isPlaying = false
    private var isCrashed = false

    private var screenHeight = 0
    private var screenWidth = 0

    // Road movement
    private var roadY1 = 0f
    private var roadY2 = 0f

    // Player bike
    private var bikeX = 0f

    private var isSteeringLeft = false
    private var isSteeringRight = false

    // Opponent Bike 1
    private var redBikeX = 0f
    private var redBikeY = -300f

    // Opponent Bike 2
    private var redBike2X = 0f
    private var redBike2Y = -700f
    private var redBike2TargetX = 0f

    // Speed and score
    private var speed = 20
    private val maxSpeed = 80

    private var score = 0
    private var distanceAccumulator = 0f

    // ===============================
    // Game Loop
    // ===============================

    private val gameRunnable = object : Runnable {

        override fun run() {

            if (isPlaying) {

                updateGameFrame()

                handler.postDelayed(this, 16)
            }
        }
    }

    // ===============================
    // Activity OnCreate
    // ===============================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        // Initialize views FIRST
        initViews()

        // Setup controls
        setupListeners()

        // Initialize music
        initMediaPlayer()
    }

    // ===============================
    // Initialize All Views
    // ===============================

    private fun initViews() {

        ivRoad1 = findViewById(R.id.ivRoad1)
        ivRoad2 = findViewById(R.id.ivRoad2)

        ivBike = findViewById(R.id.ivBike)

        ivRedBike = findViewById(R.id.ivRedBike)

        // IMPORTANT FIX:
        // Initialize second opponent bike
        ivRedBike2 = findViewById(R.id.ivRedBike2)

        tvScore = findViewById(R.id.tvScore)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvGameOver = findViewById(R.id.tvGameOver)

        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)
        btnStart = findViewById(R.id.btnStart)

        gameMenuLayout = findViewById(R.id.gameMenuLayout)

        val mainLayout = findViewById<View>(R.id.mainLayout)

        // Wait until layout is measured
        mainLayout.post {

            screenHeight = mainLayout.height
            screenWidth = mainLayout.width

            roadY1 = 0f
            roadY2 = -screenHeight.toFloat()

            ivRoad1.translationY = roadY1
            ivRoad2.translationY = roadY2

            // Reset opponent bikes
            resetRedBike1Position()
            resetRedBike2Position()
        }
    }

    // ===============================
    // Get Random Lane
    // ===============================

    private fun getRandomLaneX(): Float {

        if (screenWidth == 0) return 0f

        // Left, Center, Right lanes

        val lanes = floatArrayOf(

            -screenWidth * 0.26f,

            0f,

            screenWidth * 0.26f
        )

        return lanes[Random.nextInt(lanes.size)]
    }

    // ===============================
    // Reset Opponent Bike 1
    // ===============================

    private fun resetRedBike1Position() {

        if (screenWidth != 0) {

            redBikeX = -screenWidth * 0.28f
        }

        redBikeY = -300f

        ivRedBike.translationX = redBikeX
        ivRedBike.translationY = redBikeY
    }

    // ===============================
    // Reset Opponent Bike 2
    // ===============================

    private fun resetRedBike2Position() {

        // Safety check
        if (!::ivRedBike2.isInitialized) return

        redBike2TargetX = getRandomLaneX()

        redBike2X = redBike2TargetX

        redBike2Y = -700f

        ivRedBike2.translationX = redBike2X

        ivRedBike2.translationY = redBike2Y
    }

    // ===============================
    // Initialize Media Player
    // ===============================

    private fun initMediaPlayer() {

        try {

            mediaPlayer = MediaPlayer.create(
                this,
                R.raw.bg_music
            )

            mediaPlayer?.isLooping = true

        } catch (_: Exception) {

            mediaPlayer = null
        }
    }

    // ===============================
    // Start Music
    // ===============================

    private fun startMusic() {

        try {

            if (mediaPlayer == null) {

                initMediaPlayer()
            }

            if (mediaPlayer != null &&
                mediaPlayer?.isPlaying == false
            ) {

                mediaPlayer?.start()

            } else {

                startSynthMusic()
            }

        } catch (_: Exception) {

            startSynthMusic()
        }
    }

    // ===============================
    // Synthesized Background Music
    // ===============================

    private fun startSynthMusic() {

        if (isSynthPlaying) return

        isSynthPlaying = true

        synthThread = Thread {

            val sampleRate = 22050

            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()

                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_MUSIC
                        )
                        .build()
                )

                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        .setSampleRate(sampleRate)
                        .setChannelMask(
                            AudioFormat.CHANNEL_OUT_MONO
                        )
                        .build()
                )

                .setBufferSizeInBytes(
                    minBufSize.coerceAtLeast(4096)
                )

                .build()

            try {

                track.play()

            } catch (e: Exception) {

                e.printStackTrace()
            }

            val notes = doubleArrayOf(

                261.63,
                329.63,
                392.00,
                523.25,

                392.00,
                329.63,
                440.00,
                523.25,

                659.25,
                523.25,
                440.00,
                392.00,

                493.88,
                587.33,
                783.99,
                523.25
            )

            var noteIdx = 0

            while (isSynthPlaying) {

                val freq = notes[noteIdx]

                noteIdx = (noteIdx + 1) % notes.size

                val numSamples = (sampleRate * 0.22).toInt()

                val samples = ShortArray(numSamples)

                for (i in 0 until numSamples) {

                    val t = i.toDouble() / sampleRate

                    val wave = sin(
                        2.0 * Math.PI * freq * t
                    )

                    samples[i] =
                        if (wave > 0) 3500 else -3500
                }

                try {

                    track.write(
                        samples,
                        0,
                        samples.size
                    )

                } catch (_: Exception) {

                    break
                }
            }

            try {

                track.stop()
                track.release()

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }

        synthThread?.start()
    }

    // ===============================
    // Pause Music
    // ===============================

    private fun pauseMusic() {

        try {

            if (mediaPlayer?.isPlaying == true) {

                mediaPlayer?.pause()
            }

        } catch (e: Exception) {

            e.printStackTrace()
        }

        stopSynthMusic()
    }

    // ===============================
    // Stop Synth Music
    // ===============================

    private fun stopSynthMusic() {

        isSynthPlaying = false
        synthThread = null
    }

    // ===============================
    // Setup Game Controls
    // ===============================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {

        btnStart.setOnClickListener {

            if (isCrashed) {

                restartGame()

            } else if (isPlaying) {

                pauseGame()

            } else {

                startGame()
            }
        }

        // Left Button
        btnLeft.setOnTouchListener { v, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    if (!isCrashed) {
                        isSteeringLeft = true
                    }

                    v.performClick()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    isSteeringLeft = false
                }
            }

            true
        }

        // Right Button
        btnRight.setOnTouchListener { v, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    if (!isCrashed) {
                        isSteeringRight = true
                    }

                    v.performClick()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    isSteeringRight = false
                }
            }

            true
        }
    }

    // ===============================
    // Start Game
    // ===============================

    private fun startGame() {

        if (screenWidth == 0 || screenHeight == 0) return

        isPlaying = true
        isCrashed = false

        tvGameOver.visibility = View.GONE

        gameMenuLayout.visibility = View.GONE

        btnStart.text = getString(R.string.btn_pause)

        startMusic()

        handler.removeCallbacks(gameRunnable)

        handler.post(gameRunnable)
    }

    // ===============================
    // Pause Game
    // ===============================

    private fun pauseGame() {

        isPlaying = false

        handler.removeCallbacks(gameRunnable)

        pauseMusic()

        btnStart.text = getString(R.string.btn_start)

        gameMenuLayout.visibility = View.VISIBLE
    }

    // ===============================
    // Restart Game
    // ===============================

    private fun restartGame() {

        isCrashed = false

        speed = 20

        score = 0

        distanceAccumulator = 0f

        bikeX = 0f

        ivBike.translationX = 0f

        isSteeringLeft = false
        isSteeringRight = false

        // Reset road positions
        roadY1 = 0f
        roadY2 = -screenHeight.toFloat()

        ivRoad1.translationY = roadY1
        ivRoad2.translationY = roadY2

        // Reset opponent bikes
        resetRedBike1Position()
        resetRedBike2Position()

        tvScore.text = getString(R.string.score_format, score)

        tvSpeed.text = getString(R.string.speed_format, speed)

        tvGameOver.visibility = View.GONE

        startGame()
    }

    // ===============================
    // Game Over / Collision
    // ===============================

    private fun handleCollision() {

        if (isCrashed) return

        isCrashed = true

        isSteeringLeft = false
        isSteeringRight = false

        speed = 0

        pauseMusic()

        tvGameOver.visibility = View.VISIBLE

        btnStart.text = getString(R.string.btn_restart)

        gameMenuLayout.visibility = View.VISIBLE
    }

    // ===============================
    // Main Game Frame
    // ===============================

    private fun updateGameFrame() {

        if (screenHeight == 0 || screenWidth == 0) return

        // ===========================
        // 1. Road Scrolling
        // ===========================

        val scrollSpeed = speed / 4f

        roadY1 += scrollSpeed

        roadY2 += scrollSpeed

        if (roadY1 >= screenHeight) {

            roadY1 = roadY2 - screenHeight
        }

        if (roadY2 >= screenHeight) {

            roadY2 = roadY1 - screenHeight
        }

        ivRoad1.translationY = roadY1

        ivRoad2.translationY = roadY2

        if (!isCrashed) {

            // ===========================
            // 2. Opponent Bike 1 Movement
            // ===========================

            val redBikeSpeed = scrollSpeed + 3f

            redBikeY += redBikeSpeed

            if (redBikeY > screenHeight) {

                resetRedBike1Position()
            }

            ivRedBike.translationX = redBikeX

            ivRedBike.translationY = redBikeY

            // ===========================
            // 3. Opponent Bike 2 Movement
            // ===========================

            val redBike2Speed = scrollSpeed + 2.5f

            redBike2Y += redBike2Speed

            // Randomly select a new lane
            if (Math.random() < 0.015) {

                redBike2TargetX = getRandomLaneX()
            }

            // Smooth left-center-right movement
            redBike2X +=
                (redBike2TargetX - redBike2X) * 0.05f

            if (redBike2Y > screenHeight) {

                resetRedBike2Position()
            }

            ivRedBike2.translationX = redBike2X

            ivRedBike2.translationY = redBike2Y

            // ===========================
            // 4. Player Bike Steering
            // ===========================

            val steerSpeed = 15f

            val maxBound =
                ((screenWidth / 2f) -
                        (ivBike.width / 2f) - 20f)
                    .coerceAtLeast(0f)

            if (isSteeringLeft) {

                bikeX -= steerSpeed
            }

            if (isSteeringRight) {

                bikeX += steerSpeed
            }

            bikeX = bikeX.coerceIn(
                -maxBound,
                maxBound
            )

            ivBike.translationX = bikeX

            // ===========================
            // 5. Collision Detection
            // ===========================

            checkCollision()

            // ===========================
            // 6. Increase Speed
            // ===========================

            if (speed < maxSpeed) {

                if (Math.random() < 0.05) {

                    speed += 1
                }
            }

            // ===========================
            // 7. Score Calculation
            // ===========================

            distanceAccumulator += speed / 100f

            score = distanceAccumulator.toInt()
        }

        // ===========================
        // 8. Update Score and Speed
        // ===========================

        tvScore.text = getString(
            R.string.score_format,
            score
        )

        tvSpeed.text = getString(
            R.string.speed_format,
            speed
        )
    }

    // ===============================
    // Collision Detection
    // ===============================

    private fun checkCollision() {

        if (
            ivBike.width == 0 ||
            ivRedBike.width == 0 ||
            ivRedBike2.width == 0
        ) {
            return
        }

        // Player Bike Rectangle
        val playerLeft = (ivBike.x + 10).toInt()

        val playerTop = (ivBike.y + 10).toInt()

        val playerRight =
            (ivBike.x + ivBike.width - 10).toInt()

        val playerBottom =
            (ivBike.y + ivBike.height - 10).toInt()

        val playerRect = Rect(
            playerLeft,
            playerTop,
            playerRight,
            playerBottom
        )

        // Opponent Bike 1 Rectangle
        val redBike1Left = (ivRedBike.x + 10).toInt()

        val redBike1Top = (ivRedBike.y + 10).toInt()

        val redBike1Right =
            (ivRedBike.x + ivRedBike.width - 10).toInt()

        val redBike1Bottom =
            (ivRedBike.y + ivRedBike.height - 10).toInt()

        val redBike1Rect = Rect(
            redBike1Left,
            redBike1Top,
            redBike1Right,
            redBike1Bottom
        )

        // Opponent Bike 2 Rectangle
        val redBike2Left = (ivRedBike2.x + 10).toInt()

        val redBike2Top = (ivRedBike2.y + 10).toInt()

        val redBike2Right =
            (ivRedBike2.x + ivRedBike2.width - 10).toInt()

        val redBike2Bottom =
            (ivRedBike2.y + ivRedBike2.height - 10).toInt()

        val redBike2Rect = Rect(
            redBike2Left,
            redBike2Top,
            redBike2Right,
            redBike2Bottom
        )

        // Check collision with both bikes
        if (
            Rect.intersects(playerRect, redBike1Rect) ||
            Rect.intersects(playerRect, redBike2Rect)
        ) {

            handleCollision()
        }
    }

    // ===============================
    // Activity OnPause
    // ===============================

    override fun onPause() {

        super.onPause()

        if (isPlaying && !isCrashed) {

            pauseGame()

        } else {

            pauseMusic()
        }
    }

    // ===============================
    // Activity OnDestroy
    // ===============================

    override fun onDestroy() {

        super.onDestroy()

        isPlaying = false

        handler.removeCallbacksAndMessages(null)

        pauseMusic()

        try {

            mediaPlayer?.stop()

            mediaPlayer?.release()

            mediaPlayer = null

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }
}
