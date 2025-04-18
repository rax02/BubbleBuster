package com.fantomverse.bubblebuster

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.widget.ImageButton
import android.widget.ImageView

class MainActivity : AppCompatActivity() {
    private lateinit var soundManager: SoundManager
    private var gameView: BubbleGameView? = null
    private lateinit var playButton: Button
    private var highScore = 0
    private var isDarkTheme = false

    companion object {
        private const val PREFS_NAME = "BubbleBusterPrefs"
        private const val HIGH_SCORE_KEY = "high_score"
        private const val THEME_KEY = "is_dark_theme"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        soundManager = SoundManager(this)
        setContentView(R.layout.activity_main)
        loadHighScore()
        loadThemeState()
        initializeHomeScreen() // Use the same initialization for onCreate
    }

    private fun loadHighScore() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        highScore = prefs.getInt(HIGH_SCORE_KEY, 0)
    }

    private fun saveHighScore() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(HIGH_SCORE_KEY, highScore).apply()
    }

    private fun updateHighScore(newScore: Int) {
        if (newScore > highScore) {
            highScore = newScore
            saveHighScore()
        }
    }

    private fun updateHighScoreDisplay() {
        val highScoreText = findViewById<TextView>(R.id.highScoreText)
        highScoreText.text = "High Score: $highScore"
    }

    private fun setupTitleColors() {
        val titleText1 = findViewById<TextView>(R.id.titleText1)
        val titleText2 = findViewById<TextView>(R.id.titleText2)

        val colors = arrayOf(
            Color.RED,
            Color.rgb(255, 165, 0), // Orange
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.MAGENTA
        )

        // Color "BUBBLE"
        val spannableBubble = SpannableString("BUBBLE")
        for (i in "BUBBLE".indices) {
            spannableBubble.setSpan(
                ForegroundColorSpan(colors[i % colors.size]),
                i, i + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        titleText1.text = spannableBubble

        // Color "BUSTER"
        val spannableBuster = SpannableString("BUSTER")
        for (i in "BUSTER".indices) {
            spannableBuster.setSpan(
                ForegroundColorSpan(colors[i % colors.size]),
                i, i + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        titleText2.text = spannableBuster
    }

    private fun setupPlayButton() {
        playButton = findViewById(R.id.playButton)
        playButton.setOnClickListener {
            startGame(it)
        }
    }

    private fun setupVersionText() {
        val versionText = findViewById<TextView>(R.id.versionText)
        versionText.text = "v${BuildConfig.VERSION_NAME}"
        versionText.bringToFront()
    }

    private fun loadThemeState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkTheme = prefs.getBoolean(THEME_KEY, false)
    }

    private fun saveThemeState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(THEME_KEY, isDarkTheme).apply()
    }

    private fun setupThemeToggle() {
        findViewById<ImageButton>(R.id.themeToggleButton)?.setOnClickListener {
            isDarkTheme = !isDarkTheme
            saveThemeState()
            applyTheme()
        }
    }

    private fun applyTheme() {
        val backgroundImage = findViewById<ImageView>(R.id.backgroundImage)
        val themeToggleButton = findViewById<ImageButton>(R.id.themeToggleButton)
        
        if (isDarkTheme) {
            backgroundImage.setImageResource(R.drawable.home_background_dark)
            themeToggleButton.setImageResource(R.drawable.ic_light_mode)
        } else {
            backgroundImage.setImageResource(R.drawable.home_background_light)
            themeToggleButton.setImageResource(R.drawable.ic_dark_mode)
        }
    }

    override fun onResume() {
        super.onResume()
        soundManager.resumeAllMusic()
    }

    override fun onPause() {
        super.onPause()
        soundManager.pauseAllMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }

    fun startGame(view: View) {
        gameView = BubbleGameView(this, soundManager)
        setContentView(gameView)
        hideSystemUi()
    }

    fun returnToHome() {
        gameView?.let { view ->
            updateHighScore(view.getScore())
        }
        gameView = null
        setContentView(R.layout.activity_main)
        initializeHomeScreen()
        hideSystemUi()
    }

    private fun initializeHomeScreen() {
        setupPlayButton()
        setupTitleColors()
        updateHighScoreDisplay()
        setupThemeToggle()
        setupVersionText() // Add version text setup
        applyTheme()  
        soundManager.playHomeMusic()
    }

    fun pauseHomeMusic() {
        soundManager.pauseHomeMusic()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && gameView != null) {
            hideSystemUi()
        }
    }

    private fun hideSystemUi() {
        val decorView = window.decorView
        decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }
}