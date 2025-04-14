package com.fantomverse.bubblebuster

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

class SoundManager(private val context: Context) {
    private var soundPool: SoundPool
    private val sounds = mutableMapOf<SoundType, Int>()
    private var homeMusic: MediaPlayer? = null
    private val homeMusicVolume = 0.5f // 50% volume for home music
    private val bombExplodeVolume = 0.2f // 20% volume for bomb explosion
    private var isPaused = false

    enum class SoundType {
        SHOOT, BUBBLE_BURST, BOMB_EXPLODE, ROCK_LAUGH
    }

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load sound effects
        sounds[SoundType.SHOOT] = soundPool.load(context, R.raw.shoot, 1)
        sounds[SoundType.BUBBLE_BURST] = soundPool.load(context, R.raw.bubble_burst, 1)
        sounds[SoundType.BOMB_EXPLODE] = soundPool.load(context, R.raw.bomb_explode, 1)
        sounds[SoundType.ROCK_LAUGH] = soundPool.load(context, R.raw.rock_laugh, 1)
        // Initialize background music
        homeMusic = MediaPlayer.create(context, R.raw.home_music)
        homeMusic?.isLooping = true
        homeMusic?.setVolume(homeMusicVolume, homeMusicVolume)
    }

    fun playSound(soundType: SoundType) {
        if (!isPaused) {
            sounds[soundType]?.let { soundId ->
                val volume = if (soundType == SoundType.BOMB_EXPLODE) bombExplodeVolume else 1.0f
                soundPool.play(soundId, volume, volume, 1, 0, 1f)
            }
        }
    }

    fun playHomeMusic() {
        if (!isPaused) {
            homeMusic?.start()
        }
    }

    fun pauseAllMusic() {
        isPaused = true
        homeMusic?.pause()
    }

    fun resumeAllMusic() {
        isPaused = false
        homeMusic?.start()
    }

    fun release() {
        soundPool.release()
        homeMusic?.release()
        homeMusic = null
        
        // Reinitialize sound resources
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        // Reload sound effects
        sounds[SoundType.SHOOT] = soundPool.load(context, R.raw.shoot, 1)
        sounds[SoundType.BUBBLE_BURST] = soundPool.load(context, R.raw.bubble_burst, 1)
        sounds[SoundType.BOMB_EXPLODE] = soundPool.load(context, R.raw.bomb_explode, 1)
        sounds[SoundType.ROCK_LAUGH] = soundPool.load(context, R.raw.rock_laugh, 1)

        // Reinitialize background music
        homeMusic = MediaPlayer.create(context, R.raw.home_music)
        homeMusic?.isLooping = true
        homeMusic?.setVolume(homeMusicVolume, homeMusicVolume)
    }
} 