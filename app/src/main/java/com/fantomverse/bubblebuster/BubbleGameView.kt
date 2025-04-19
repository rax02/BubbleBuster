package com.fantomverse.bubblebuster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.atan2
import kotlin.random.Random
import kotlin.math.sin
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import android.graphics.Matrix
import java.util.Collections
import pl.droidsonroids.gif.GifDrawable
import android.graphics.BlurMaskFilter

class BubbleGameView(
    context: Context, 
    private val soundManager: SoundManager,
    private val isDarkTheme: Boolean = context.getSharedPreferences("BubbleBusterPrefs", Context.MODE_PRIVATE)
        .getBoolean("is_dark_theme", false)
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private var thread: Thread? = null
    private var isPlaying = false
    private var isGameOver = false
    private lateinit var paint: Paint
    private var bubbles = Collections.synchronizedList(mutableListOf<Bubble>())
    private var bullets = Collections.synchronizedList(mutableListOf<Bullet>())
    private var score = 0
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var gameTime = 0L
    private var lastUpdateTime = System.currentTimeMillis()
    private var random = Random(System.currentTimeMillis())
    private var lastBulletRefillTime = System.currentTimeMillis()

    // TNT display properties
    private var lastTntSpawnTime = 0L
    private val tntSpawnInterval = 7000L // 7 seconds in milliseconds

    // Bubble display properties
    private val minBubbleRadius = 20f
    private val maxBubbleRadius = 80f
    private var lastBubbleSpawnTime = 0L
    private val bubbleSpawnInterval = 3000L // 3 second in milliseconds

    // Cannon display properties
    private var cannonAngle = 0f
    private val cannonLength = 100f
    private var cannonRecoil = 0f
    private val maxRecoil = 20f
    private val recoilRecoverySpeed = 2f
    private var cannonColor = if (isDarkTheme) Color.WHITE else Color.BLACK

    // Score display properties
    private var scoreLabel = "Score :"
    private var scoreNumber = "0"
    private var scoreLabelWidth = 0f
    private var startX = 0f
    private var scoreScale = 1f
    private var lastScore = 0
    
    // Bullet meter properties
    private var bulletMeter = 0f
    private val maxBullets = 10f
    private var bulletRefillRate = 1f // bullets per second
    private val meterWidth = 200f
    private val meterHeight = 30f
    private val meterPadding = 20f

    // Bullet properties
    private val bulletSpeed = 15f
    private var currentBulletRadius = 10f  // Replace bulletRadius val with this var
    private var originalBulletRefillRate = 1f
    private var originalBulletRadius = 10f

    // Add this near other bullet properties
    private var bulletSpeedMultiplier = 1f

    // Home button properties
    private val homeButtonRect = RectF()
    private var homeButtonX = 0f
    private var homeButtonY = 0f
    private val homeButtonWidth = 300f
    private val homeButtonHeight = 100f
    private val homeButtonPadding = 20f

    // Bubble particle properties
    private var particleColor = Color.rgb(173, 216, 230)
    private var bombParticleColor = Color.rgb(100,65,0)
    private var particleScale = 0.60f

    // Add bitmap fields
    private lateinit var backgroundBitmap: Bitmap
    private lateinit var blueBubbleBitmap: Bitmap
    private lateinit var blackBombBitmap: Bitmap

    private lateinit var gameFont: Typeface

    // Rock properties
    private var rocks = Collections.synchronizedList(mutableListOf<Rock>())
    private var lastRockSpawnTime = 0L
    private val rockSpawnInterval = 30000L // 30 seconds
    private lateinit var rockBitmap: Bitmap
    private var rock_bonus = 20

    private data class Rock(
        var x: Float,
        var y: Float,
        var health: Int,
        val speed: Float,
        val size: Float,
        var shakeAmount: Float = 0f,
        var shakeTime: Long = 0L
    )

    private data class FloatingScore(
        var x: Float,
        var y: Float,
        val score: Int,
        var alpha: Int = 255,
        val startTime: Long = System.currentTimeMillis()
    )

    private data class BubbleParticle(
        var x: Float,
        var y: Float,
        var dx: Float,
        var dy: Float,
        var alpha: Int = 255,
        var scale: Float = 1f,
        val startTime: Long = System.currentTimeMillis(),
        val color: Int  // Add color parameter
    )

    private var particles = Collections.synchronizedList(mutableListOf<BubbleParticle>())
    private var floatingScores = Collections.synchronizedList(mutableListOf<FloatingScore>())

    // Game properties
    private var isGameFrozen = false
    private var isGameOverStarted = false
    private var gameOverStartTime = 0L
    private val gameOverDelay = 3000L // 3 seconds

    private val prefs = context.getSharedPreferences("BubbleBusterPrefs", Context.MODE_PRIVATE)
    private var highScore = prefs.getInt("high_score", 0)
    private var isNewHighScore = false

    // Add property near other game state properties
    private var gameOverMusicHandled = false

    // Add these properties after other particle-related properties
    private data class CelebrationParticle(
        var x: Float,
        var y: Float,
        var dx: Float,
        var dy: Float,
        var color: Int,
        var size: Float,
        var alpha: Int = 255,
        val startTime: Long = System.currentTimeMillis()
    )

    private var celebrationParticles = Collections.synchronizedList(mutableListOf<CelebrationParticle>())
    private val celebrationColors = listOf(
        Color.YELLOW,
        Color.CYAN,
        Color.MAGENTA,
        Color.GREEN,
        Color.RED
    )

    // Add after other celebration properties
    private var lastCornerCelebrationTime = 0L
    private var currentCorner = 0 // 0 = left, 1 = right
    private val cornerCelebrationInterval = 1000L // Switch corners every 1 second

    // Add near other timing properties
    private var lastParticleBurstTime = 0L
    private val burstInterval = 1000L // Once per second (1000/1)

    // Add after other data classes
    private data class Witch(
        var x: Float,
        var y: Float,
        var health: Int,
        val speed: Float,
        val size: Float,
        var targetX: Float = 0f,
        var targetY: Float = 0f,
        var shakeAmount: Float = 0f,
        var shakeTime: Long = 0L
    )

    // Add after other properties
    private var witches = Collections.synchronizedList(mutableListOf<Witch>())
    private var lastWitchSpawnTime = 0L
    private val witchSpawnInterval = 120000L // 2 minutes
    private lateinit var witchBitmap: Bitmap
    private var witch_bonus = 50

    // Add after other data classes
    private data class Explosion(
        var x: Float,
        var y: Float,
        val size: Float = 100f,
        val startTime: Long = System.currentTimeMillis()
    )

    // Add after other properties
    private var explosions = Collections.synchronizedList(mutableListOf<Explosion>())
    private lateinit var explosionGif: GifDrawable

    // Add this field after other properties
    private var isCannonExplosion = false

    // Add after other data classes
    private data class PowerUp(
        var x: Float,
        var y: Float,
        val speed: Float,
        val radius: Float,
        val type: PowerUpType,
        val timeWallet: Int, // Time in seconds
        var isCollected: Boolean = false,
        var collectedTime: Long = 0L,
        var isActive: Boolean = false,
        var activeStartTime: Long = 0L
    )

    private enum class PowerUpType(val color: Int, val symbol: String) {
        RAPID_FIRE(Color.RED, "⚡"),      // Fire symbol for rapid fire
        DOUBLE_POINTS(Color.YELLOW, "×2"), // Keep ×2 as it's clear
        SHIELD(Color.BLUE,"🛡️"),          // Shield symbol for shield
        MEGA_BULLET(Color.GREEN, "🔥")    // Explosion symbol for mega bullet
    }

    // Add after other properties
    private var powerUps = Collections.synchronizedList(mutableListOf<PowerUp>())
    private var collectedPowerUps = Collections.synchronizedList(mutableListOf<PowerUp>())
    private var lastPowerUpSpawnTime = 0L
    private val powerUpSpawnInterval = 25000L // 25 seconds
    private val maxCollectedPowerUps = 5
    private val powerUpGlowPaint = Paint().apply {
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.OUTER)
    }

    // Add these properties near other game state properties
    private var scoreMultiplier = 1
    private var isShieldActive = false

    // Add these constants near other properties
    private val powerUpRadius = 35f  
    private val powerUpTapRadius = 55f  

    // Add this property with other class properties
    private var isDownOnPowerUp = false 

    init {
        holder.addCallback(this)
        paint = Paint()
        gameFont = ResourcesCompat.getFont(context, R.font.press_start_2p) ?: Typeface.DEFAULT
        paint.color = Color.BLUE
        paint.style = Paint.Style.FILL
        score = 0
        gameTime = 0L
        lastUpdateTime = System.currentTimeMillis()
        lastRockSpawnTime = System.currentTimeMillis() + 1000L // Set initial spawn 1 seconds in the future
        lastWitchSpawnTime = System.currentTimeMillis() + 1000L // Set initial spawn 1 seconds in the future
        bulletMeter = maxBullets
        lastBulletRefillTime = System.currentTimeMillis()
        isGameOver = false

        // Load all bitmaps
        backgroundBitmap = BitmapFactory.decodeResource(
            context.resources,
            if (isDarkTheme) R.drawable.game_background_dark else R.drawable.game_background_light
        )
        blueBubbleBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.blue_bubble)
        blackBombBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.black_bomb)
        rockBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.rock)
        witchBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.witch)
        explosionGif = GifDrawable(context.resources, R.drawable.explosion)

        lastPowerUpSpawnTime = System.currentTimeMillis()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isPlaying = true
        thread = Thread(this)
        thread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Update home button position
        homeButtonX = width / 2f - homeButtonWidth / 2
        homeButtonY = height / 2f + 100f
        homeButtonRect.set(
            homeButtonX,
            homeButtonY,
            homeButtonX + homeButtonWidth,
            homeButtonY + homeButtonHeight
        )
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isPlaying = false
        thread?.join()
        soundManager.release()
    }

    override fun run() {
        while (isPlaying) {
            update()
            draw()
            sleep()
        }
    }

    private fun update() {
        if (isGameOver && !isGameFrozen) {
            draw()
            return
        }

        if (isGameFrozen) {
            // Update particles during frozen state
            synchronized(particles) {
                particles.forEach { particle ->
                    particle.x += particle.dx
                    particle.y += particle.dy
                    particle.alpha = ((500 - (System.currentTimeMillis() - particle.startTime)) * 255 / 500)
                        .toInt().coerceIn(0, 255)
                    particle.scale *= particleScale
                }
                particles.removeAll { System.currentTimeMillis() - it.startTime > 500 }
            }

            // Check if animation completed and delay passed
            if (particles.isEmpty() && System.currentTimeMillis() - gameOverStartTime >= gameOverDelay) {
                isGameOver = true
                bubbles.clear()
                bullets.clear()
                rocks.clear()
            }
            draw()
            return
        }

        // Score Pulse animation
        if (score != lastScore) {
            scoreScale = 1.5f
            lastScore = score
        }
        scoreScale = (scoreScale - 0.05f).coerceAtLeast(1f)

        
        //Cannon recoil animation
        cannonRecoil = (cannonRecoil - recoilRecoverySpeed).coerceAtLeast(0f)


        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastUpdateTime
        lastUpdateTime = currentTime
        gameTime += deltaTime

        // Update bullet meter
        bulletMeter = (bulletMeter + bulletRefillRate * deltaTime / 1000f).coerceAtMost(maxBullets)

        synchronized(bubbles) {
            // Spawn TNT bomb every 4 seconds
            if (currentTime - lastTntSpawnTime >= tntSpawnInterval) {
                // Randomly choose left (25%) or right (75%) side
                val x = if (Random.nextBoolean()) {
                    Random.nextFloat() * (width * 0.25f) // Left 25%
                } else {
                    width * 0.75f + Random.nextFloat() * (width * 0.25f) // Right 25%
                }
                val y = height.toFloat()
                val speed = Random.nextFloat() * 5 + 2
                val radius = Random.nextFloat() * (maxBubbleRadius - minBubbleRadius) + minBubbleRadius
                bubbles.add(Bubble(x, y, speed, radius, 0, gameTime, true, Random.nextBoolean()))
                lastTntSpawnTime = currentTime
            }

            // Spawn regular bubble every second
            if (currentTime - lastBubbleSpawnTime >= bubbleSpawnInterval) {
                // Randomly choose left (25%) or right (75%) side
                val x = if (Random.nextBoolean()) {
                    Random.nextFloat() * (width * 0.25f) // Left 25%
                } else {
                    width * 0.75f + Random.nextFloat() * (width * 0.25f) // Right 25%
                }
                val y = height.toFloat()
                val speed = Random.nextFloat() * 5 + 2
                val radius = Random.nextFloat() * (maxBubbleRadius - minBubbleRadius) + minBubbleRadius
                val points = calculatePoints(radius)
                bubbles.add(Bubble(x, y, speed, radius, points, gameTime, false, Random.nextBoolean()))
                lastBubbleSpawnTime = currentTime
            }

            // Update bubble positions
            bubbles.toList().forEach { bubble ->
                bubble.y -= bubble.speed
            }
            bubbles.removeAll { it.y < -it.radius * 2 }
        }

        synchronized(particles) {
            // bubble pop
            particles.forEach { particle ->
                particle.x += particle.dx
                particle.y += particle.dy
                particle.alpha = ((500 - (System.currentTimeMillis() - particle.startTime)) * 255 / 500)
                    .toInt().coerceIn(0, 255)
                particle.scale *= particleScale
            }
            particles.removeAll { System.currentTimeMillis() - it.startTime > 500 }
        }

        synchronized(rocks) {
            // Spawn rocks
            if (currentTime - lastRockSpawnTime >= rockSpawnInterval) {
                val x = Random.nextFloat() * width
                val y = -50f
                val health = (gameTime / 60000L).toInt() + 1
                val speed = Random.nextFloat() * 2 + 1
                val size = 60f
                rocks.add(Rock(x, y, health, speed, size))
                soundManager.playSound(SoundManager.SoundType.ROCK_LAUGH)
                lastRockSpawnTime = currentTime
            }

            // Update rock positions
            rocks.toList().forEach { rock ->
                if (!checkShieldCollision(rock.x, rock.y + rock.speed, rock.size)) {
                    rock.y += rock.speed
                }
                
                if (checkCannonCollision(rock.x, rock.y, rock.size)) {
                    createMultipleExplosions(width / 2f, height.toFloat() - meterHeight - meterPadding - 20f)
                    isGameFrozen = true
                    gameOverStartTime = System.currentTimeMillis()
                    soundManager.playSound(SoundManager.SoundType.BOMB_EXPLODE)
                    return
                }

                // Rock Shake animation
                 if (rock.shakeAmount > 0f) {
                    val timeSinceShake = System.currentTimeMillis() - rock.shakeTime
                    rock.x += sin(timeSinceShake * 0.5f) * rock.shakeAmount
                    rock.shakeAmount *= 0.9f
                }
            }
            rocks.removeAll { it.y > height + it.size }
        }

        synchronized(witches) {
            // Spawn witches every 2 minutes
            if (currentTime - lastWitchSpawnTime >= witchSpawnInterval) {
                val startX = Random.nextFloat() * width  // Random x position across screen width
                val startY = -50f  // Start above screen
                val health = ((gameTime / 60000L).toInt() + 5) 
                val speed = Random.nextFloat() * 3 + 2
                val size = 70f
                
                // Target position is the cannon
                val targetX = width / 2f
                val targetY = height.toFloat() - meterHeight - meterPadding - 20f
                
                witches.add(Witch(startX, startY, health, speed, size, targetX, targetY))
                soundManager.playSound(SoundManager.SoundType.WITCH_LAUGH) 
                lastWitchSpawnTime = currentTime
            }

            // Update witch positions
            witches.toList().forEach { witch ->
                // Calculate direction to target (cannon)
                val dx = witch.targetX - witch.x
                val dy = witch.targetY - witch.y
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                
                if (distance > 0) {
                    val nextX = witch.x + (dx / distance) * witch.speed
                    val nextY = witch.y + (dy / distance) * witch.speed
                    
                    if (!checkShieldCollision(nextX, nextY, witch.size)) {
                        witch.x = nextX
                        witch.y = nextY
                    }
                }

                if (checkCannonCollision(witch.x, witch.y, witch.size)) {
                    createMultipleExplosions(width / 2f, height.toFloat() - meterHeight - meterPadding - 20f)
                    isGameFrozen = true
                    gameOverStartTime = System.currentTimeMillis()
                    soundManager.playSound(SoundManager.SoundType.BOMB_EXPLODE)
                    return
                }

                // Witch Shake animation
                if (witch.shakeAmount > 0f) {
                    val timeSinceShake = System.currentTimeMillis() - witch.shakeTime
                    witch.x += sin(timeSinceShake * 0.5f) * witch.shakeAmount
                    witch.shakeAmount *= 0.9f
                }
            }
        }

        synchronized(bullets) {
            synchronized(bubbles) {
                synchronized(rocks) {
                    synchronized(floatingScores) {
                        val bulletsToRemove = mutableListOf<Bullet>()
                        val bubblesToRemove = mutableListOf<Bubble>()
                        val rocksToRemove = mutableListOf<Rock>()

                        bullets.forEach { bullet ->
                            bullet.x += bullet.dx
                            bullet.y += bullet.dy

                            if (bullet.x < 0 || bullet.x > width || bullet.y < 0 || bullet.y > height) {
                                bulletsToRemove.add(bullet)
                                return@forEach
                            }

                            // Check bubble collisions
                            bubbles.find { bubble ->
                                Math.hypot((bullet.x - bubble.x).toDouble(), (bullet.y - bubble.y).toDouble()) <= bubble.radius + currentBulletRadius
                            }?.let { bubble ->
                                if (bubble.isTnt) {
                                    explosions.add(Explosion(bubble.x, bubble.y))
                                    soundManager.playSound(SoundManager.SoundType.BOMB_EXPLODE)
                                    bubblesToRemove.add(bubble)
                                    bulletsToRemove.add(bullet)
                                    bubbles.removeAll(bubblesToRemove.toSet()) // Remove TNT immediately
                                    bullets.removeAll(bulletsToRemove.toSet()) // Remove bullet immediately
                                    isGameFrozen = true
                                    gameOverStartTime = System.currentTimeMillis()
                                    return
                                } else {
                                    // Create pop effect immediately before removing bubble
                                    createBubblePopEffect(bubble.x, bubble.y, bubble.radius, particleColor)
                                    soundManager.playSound(SoundManager.SoundType.BUBBLE_BURST)
                                    score += bubble.points * scoreMultiplier  // Apply score multiplier
                                    bubblesToRemove.add(bubble)
                                    bulletsToRemove.add(bullet)
                                }
                            }

                            // Check rock collisions
                            rocks.find { rock ->
                                Math.hypot((bullet.x - rock.x).toDouble(), (bullet.y - rock.y).toDouble()) <= rock.size + currentBulletRadius
                            }?.let { rock ->
                                rock.health--
                                if (rock.health <= 0) {
                                    explosions.add(Explosion(rock.x, rock.y))
                                    rocksToRemove.add(rock)
                                    score += rock_bonus * scoreMultiplier  // Apply score multiplier
                                    floatingScores.add(FloatingScore(rock.x, rock.y, rock_bonus))
                                    soundManager.playSound(SoundManager.SoundType.BUBBLE_BURST)
                                } else {
                                    score += 1 * scoreMultiplier  // Add 1 point for hitting the rock
                                    floatingScores.add(FloatingScore(bullet.x, bullet.y, 1))
                                    rock.shakeAmount = 10f
                                    rock.shakeTime = System.currentTimeMillis()
                                }
                                bulletsToRemove.add(bullet)
                            }
                        }

                        bullets.removeAll(bulletsToRemove.toSet())
                        bubbles.removeAll(bubblesToRemove.toSet())
                        rocks.removeAll(rocksToRemove.toSet())

                        // Update floating scores
                        floatingScores.removeAll { System.currentTimeMillis() - it.startTime > 1000 }
                        floatingScores.forEach { 
                            it.y -= 2f
                            it.alpha = ((1000 - (System.currentTimeMillis() - it.startTime)) * 255 / 1000).toInt()
                                .coerceIn(0, 255)
                        }
                    }
                }
            }
        }

        synchronized(bullets) {
            synchronized(witches) {
                val bulletsToRemove = mutableListOf<Bullet>()
                val witchesToRemove = mutableListOf<Witch>()

                bullets.forEach { bullet ->
                    // ...existing code...

                    // Check witch collisions
                    witches.find { witch ->
                        Math.hypot((bullet.x - witch.x).toDouble(), (bullet.y - witch.y).toDouble()) <= witch.size + currentBulletRadius
                    }?.let { witch ->
                        witch.health--
                        if (witch.health <= 0) {
                            explosions.add(Explosion(witch.x, witch.y))
                            witchesToRemove.add(witch)
                            score += witch_bonus * scoreMultiplier  // Apply score multiplier
                            floatingScores.add(FloatingScore(witch.x, witch.y, witch_bonus))
                            soundManager.playSound(SoundManager.SoundType.BUBBLE_BURST)
                        } else {
                            score += 1 * scoreMultiplier  // Add 1 point for hitting the witch
                            floatingScores.add(FloatingScore(bullet.x, bullet.y, 1))
                            witch.shakeAmount = 10f
                            witch.shakeTime = System.currentTimeMillis()
                        }
                        bulletsToRemove.add(bullet)
                    }
                }

                bullets.removeAll(bulletsToRemove.toSet())
                witches.removeAll(witchesToRemove.toSet())
            }
        }

        // Update power-ups
        synchronized(powerUps) {
            // Spawn new power-up
            if (currentTime - lastPowerUpSpawnTime >= powerUpSpawnInterval) {
                val x = Random.nextFloat() * width
                val y = -50f
                val speed = Random.nextFloat() * 3 + 2
                val type = PowerUpType.values()[Random.nextInt(PowerUpType.values().size)]
                val timeWallet = ((gameTime / 60000L) * 5 + 5).toInt() // Starts at 5 seconds, increases by 5 every minute
                powerUps.add(PowerUp(x, y, speed, 30f, type, timeWallet))
                lastPowerUpSpawnTime = currentTime
            }

            // Move power-ups
            powerUps.forEach { powerUp ->
                if (!powerUp.isCollected) {
                    powerUp.y += powerUp.speed
                }
            }
            powerUps.removeAll { it.y > height && !it.isCollected }
        }

        // Check power-up collisions with bullets
        synchronized(bullets) {
            synchronized(powerUps) {
                val bulletsToRemove = mutableListOf<Bullet>()
                powerUps.filter { !it.isCollected }.forEach { powerUp ->
                    bullets.find { bullet ->
                        Math.hypot((bullet.x - powerUp.x).toDouble(), 
                                 (bullet.y - powerUp.y).toDouble()) <= powerUp.radius + currentBulletRadius
                    }?.let { bullet ->
                        if (collectedPowerUps.size < maxCollectedPowerUps) {
                            powerUp.isCollected = true
                            powerUp.collectedTime = System.currentTimeMillis()
                            collectedPowerUps.add(powerUp)
                            soundManager.playSound(SoundManager.SoundType.BUBBLE_BURST)
                        }
                        bulletsToRemove.add(bullet)
                    }
                }
                bullets.removeAll(bulletsToRemove)
            }
        }

        // Update active power-ups
        synchronized(collectedPowerUps) {
            collectedPowerUps.removeAll { powerUp ->
                if (powerUp.isActive) {
                    val elapsedTime = (System.currentTimeMillis() - powerUp.activeStartTime) / 1000
                    elapsedTime >= powerUp.timeWallet
                } else false
            }
        }
    }

    private fun calculatePoints(radius: Float): Int {
        // Smaller bubbles are worth more points
        val normalizedSize = (radius - minBubbleRadius) / (maxBubbleRadius - minBubbleRadius)
        return 11 - (normalizedSize * 10).toInt() // Points range from 1 to 10
    }

    private fun draw() {
        if (holder.surface.isValid) {
            val canvas = holder.lockCanvas()
            
            // Draw background
            canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)

            if (isGameOver) {
                drawGameOver(canvas)
                holder.unlockCanvasAndPost(canvas)
                return
            }

            drawGameState(canvas)
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawGameState(canvas: Canvas) {
        drawScore(canvas)
        drawTime(canvas)
        // ...rest of the original draw method content...

        // Draw bullet meter
        val meterX = width/2f - meterWidth/2
        val meterY = height - meterPadding
        val meterRect = RectF(meterX, meterY, meterX + meterWidth, meterY + meterHeight)
        
        // Draw meter background
        paint.color = Color.LTGRAY
        canvas.drawRect(meterRect, paint)
        
        // Draw filled portion
        val fillWidth = (bulletMeter / maxBullets) * meterWidth
        val fillRect = RectF(meterX, meterY, meterX + fillWidth, meterY + meterHeight)
        paint.color = Color.RED
        canvas.drawRect(fillRect, paint)
        
        // Draw meter border
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(meterRect, paint)
        paint.style = Paint.Style.FILL

        // Draw bubbles - use synchronized block for safe iteration
        synchronized(bubbles) {
            bubbles.forEach { bubble ->
                if (bubble.isTnt) {
                    // Draw TNT bomb using image with possible flip
                    val matrix = Matrix()
                    val scaleX = if (bubble.isFlipped) -(bubble.radius * 2) / blackBombBitmap.width 
                                else (bubble.radius * 2) / blackBombBitmap.width
                    val scaleY = (bubble.radius * 2) / blackBombBitmap.width
                    matrix.setScale(scaleX, scaleY)
                    // Adjust x position when flipped to maintain correct position
                    val translateX = if (bubble.isFlipped) bubble.x + bubble.radius else bubble.x - bubble.radius
                    matrix.postTranslate(translateX, bubble.y - bubble.radius)
                    canvas.drawBitmap(blackBombBitmap, matrix, paint)
                } else {
                    // Draw regular bubble
                    val matrix = Matrix()
                    val scale = (bubble.radius * 2) / blueBubbleBitmap.width
                    matrix.setScale(scale, scale)
                    matrix.postTranslate(bubble.x - bubble.radius, bubble.y - bubble.radius)
                    canvas.drawBitmap(blueBubbleBitmap, matrix, paint)

                    // Draw points inside bubble
                    paint.color = Color.BLACK
                    paint.textSize = bubble.radius * 0.8f
                    canvas.drawText(bubble.points.toString(), bubble.x, bubble.y + bubble.radius/3, paint)
                }
            }
        }
        // Draw bubble pop particles - use synchronized block for safe iteration
        synchronized(particles) {
            particles.forEach { particle ->
                paint.color = particle.color  // Use particle's color instead of particleColor
                paint.alpha = particle.alpha
                canvas.drawCircle(particle.x, particle.y, particle.scale, paint)
            }
            paint.alpha = 255
        }

        // Draw rocks - use synchronized block for safe iteration
        synchronized(rocks) {
            rocks.forEach { rock ->
                val matrix = Matrix()
                val scale = (rock.size * 2) / rockBitmap.width
                matrix.setScale(scale, scale)
                matrix.postTranslate(rock.x - rock.size, rock.y - rock.size)
                canvas.drawBitmap(rockBitmap, matrix, paint)

                // Draw health meter
                val meterWidth = rock.size * 2
                val meterHeight = 10f
                val meterY = rock.y - rock.size - meterHeight - 5f // Position above rock with small gap

                // Draw meter background
                paint.color = Color.GRAY
                canvas.drawRect(
                    rock.x - rock.size,
                    meterY,
                    rock.x + rock.size,
                    meterY + meterHeight,
                    paint
                )

                // Draw health bar
                val healthPercentage = rock.health.toFloat() / (gameTime / 60000L + 1).toFloat()
                paint.color = Color.GREEN
                canvas.drawRect(
                    rock.x - rock.size,
                    meterY,
                    rock.x - rock.size + (meterWidth * healthPercentage),
                    meterY + meterHeight,
                    paint
                )

                // Draw meter border
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawRect(
                    rock.x - rock.size,
                    meterY,
                    rock.x + rock.size,
                    meterY + meterHeight,
                    paint
                )
                paint.style = Paint.Style.FILL
            }
        }

        // Draw floating scores - use synchronized block for safe iteration
        synchronized(floatingScores) {
            floatingScores.forEach { floatingScore ->
                paint.color = Color.YELLOW
                paint.alpha = floatingScore.alpha
                paint.textSize = 40f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("+${floatingScore.score}", floatingScore.x, floatingScore.y, paint)
                paint.alpha = 255
            }
        }

        // Draw bullets - use synchronized block for safe iteration
        synchronized(bullets) {
            bullets.forEach { bullet ->
                paint.color = Color.RED
                canvas.drawCircle(bullet.x, bullet.y, currentBulletRadius, paint)
            }
        }

        // Draw cannon only if cannon is not destroyed
        if (!isCannonExplosion) {
            paint.color = cannonColor
            val cannonX = width / 2f
            val cannonY = height.toFloat() - meterHeight - meterPadding - 20f
            
            // Apply recoil to the cannon length
            val recoilAdjustedLength = cannonLength - cannonRecoil
            
            val endX = cannonX + recoilAdjustedLength * kotlin.math.sin(cannonAngle)
            val endY = cannonY - recoilAdjustedLength * kotlin.math.cos(cannonAngle)
            
            paint.strokeWidth = 20f
            canvas.drawLine(cannonX, cannonY, endX, endY, paint)

            // Draw cannon base
            paint.color = Color.DKGRAY
            canvas.drawCircle(cannonX, cannonY, 30f, paint)
        }

        // Draw shield effect if active
        if (isShieldActive) {
            paint.color = PowerUpType.SHIELD.color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 15f  // Increased from 5f to 15f for thicker shield
            paint.alpha = 128
            canvas.drawCircle(
                width / 2f,
                height.toFloat() - meterHeight - meterPadding - 20f,
                width / 4f,
                paint
            )
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }

        // Draw witches
        synchronized(witches) {
            witches.forEach { witch ->
                val matrix = Matrix()
                val scale = (witch.size * 2) / witchBitmap.width
                matrix.setScale(scale, scale)
                matrix.postTranslate(witch.x - witch.size, witch.y - witch.size)
                canvas.drawBitmap(witchBitmap, matrix, paint)

                // Draw health meter
                val meterWidth = witch.size * 2
                val meterHeight = 10f
                val meterY = witch.y - witch.size - meterHeight - 5f

                // Draw meter background
                paint.color = Color.GRAY
                canvas.drawRect(
                    witch.x - witch.size,
                    meterY,
                    witch.x + witch.size,
                    meterY + meterHeight,
                    paint
                )

                // Draw health bar
                val healthPercentage = witch.health.toFloat() / (gameTime / 60000L + 5).toFloat()
                paint.color = Color.MAGENTA
                canvas.drawRect(
                    witch.x - witch.size,
                    meterY,
                    witch.x - witch.size + (meterWidth * healthPercentage),
                    meterY + meterHeight,
                    paint
                )

                // Draw meter border
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawRect(
                    witch.x - witch.size,
                    meterY,
                    witch.x + witch.size,
                    meterY + meterHeight,
                    paint
                )
                paint.style = Paint.Style.FILL
            }
        }

        // Draw explosions
        synchronized(explosions) {
            explosions.forEach { explosion ->
                val left = explosion.x - explosion.size/2
                val top = explosion.y - explosion.size/2
                val right = explosion.x + explosion.size/2
                val bottom = explosion.y + explosion.size/2
                
                explosionGif.setBounds(
                    left.toInt(),
                    top.toInt(),
                    right.toInt(),
                    bottom.toInt()
                )
                
                explosionGif.draw(canvas)
            }
            paint.alpha = 255
            // Only remove explosions if it's not a cannon explosion or if game is over
            if (!isCannonExplosion || isGameOver) {
                explosions.removeAll { System.currentTimeMillis() - it.startTime > 500 }
            }
        }

        // Draw power-ups
        synchronized(powerUps) {
            powerUps.filter { !it.isCollected }.forEach { powerUp ->
                // Draw outer glow
                powerUpGlowPaint.color = powerUp.type.color
                canvas.drawCircle(powerUp.x, powerUp.y, powerUp.radius + 5f, powerUpGlowPaint)
                
                // Draw ring
                paint.color = powerUp.type.color
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                canvas.drawCircle(powerUp.x, powerUp.y, powerUp.radius, paint)
                paint.style = Paint.Style.FILL

                // Draw symbol inside ring
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = powerUp.radius * 1.2f
                paint.style = Paint.Style.FILL
                canvas.drawText(powerUp.type.symbol, powerUp.x, powerUp.y + powerUp.radius/2, paint)

                // Draw time wallet
                paint.textSize = 20f
                paint.typeface = Typeface.DEFAULT_BOLD  // Add this line for bold text
                canvas.drawText("${powerUp.timeWallet}s", powerUp.x, powerUp.y + powerUp.radius + 35f, paint) // Increased from 20f to 35f
            }
        }

        // Draw collected power-ups with symbols
        synchronized(collectedPowerUps) {
            val startX = width - 80f
            val startY = height - meterHeight - meterPadding - 100f
            
            collectedPowerUps.forEachIndexed { index, powerUp ->
                val y = startY - (index * 110f)  
                
                // Draw active/inactive power-up
                powerUpGlowPaint.color = powerUp.type.color
                canvas.drawCircle(startX, y, powerUpRadius, powerUpGlowPaint)
                
                paint.color = powerUp.type.color
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                canvas.drawCircle(startX, y, powerUpRadius, paint)

                // Draw symbol inside ring
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = powerUpRadius * 1.2f
                canvas.drawText(powerUp.type.symbol, startX, y + powerUpRadius/2, paint)

                paint.textSize = 20f
                paint.typeface = Typeface.DEFAULT_BOLD  // Add this line for bold text
                if (powerUp.isActive) {
                    val elapsedTime = (System.currentTimeMillis() - powerUp.activeStartTime) / 1000
                    val remainingTime = powerUp.timeWallet - elapsedTime
                    canvas.drawText("${remainingTime}s", startX, y + powerUpRadius + 25f, paint)  // Reduced from 35f to 25f
                } else {
                    canvas.drawText("${powerUp.timeWallet}s", startX, y + powerUpRadius + 25f, paint)  // Reduced from 35f to 25f
                }
            }
        }
    }

    private fun sleep() {
        try {
            Thread.sleep(17) // Approximately 60 FPS
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y
                if (homeButtonRect.contains(x, y)) {
                    soundManager.release() // Release and reinitialize sound resources
                    (context as MainActivity).returnToHome()
                }
            }
            return true
        }

        if (!isGameOver) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDownOnPowerUp = false // Reset flag
                    val startX = width - 80f  // Moved further from edge for better tapping
                    val startY = height - meterHeight - meterPadding - 100f
                    
                    synchronized(collectedPowerUps) {
                        collectedPowerUps.forEachIndexed { index, powerUp ->
                            val y = startY - (index * 90f)  // Increased spacing between power-ups
                            if (!powerUp.isActive && 
                                Math.hypot((event.x - startX).toDouble(), 
                                         (event.y - y).toDouble()) <= powerUpTapRadius) {
                                isDownOnPowerUp = true
                                powerUp.isActive = true
                                powerUp.activeStartTime = System.currentTimeMillis()
                                applyPowerUpEffect(powerUp)
                                return true // Exit early if power-up was tapped
                            }
                        }
                    }
                    if (!isDownOnPowerUp) {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        updateCannonAngle(event.x, event.y)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDownOnPowerUp) {
                        updateCannonAngle(event.x, event.y)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDownOnPowerUp && bulletMeter >= 1f) {
                        shootBullet()
                        bulletMeter -= 1f
                    }
                }
            }
        }
        return true
    }

    private fun updateCannonAngle(touchX: Float, touchY: Float) {
        val centerX = width / 2f
        val centerY = height.toFloat()
        cannonAngle = atan2(touchX - centerX, centerY - touchY).toFloat()
    }

    private fun shootBullet() {
        val cannonX = width / 2f
        val cannonY = height.toFloat() - meterHeight - meterPadding - 20f
        val endX = cannonX + cannonLength * kotlin.math.sin(cannonAngle)
        val endY = cannonY - cannonLength * kotlin.math.cos(cannonAngle)
        
        cannonRecoil = maxRecoil

        // Add small explosion at bullet spawn position
        explosions.add(Explosion(
            x = endX,
            y = endY,
            size = 30f  // Small explosion size
        ))

        val dx = bulletSpeed * bulletSpeedMultiplier * kotlin.math.sin(cannonAngle)
        val dy = -bulletSpeed * bulletSpeedMultiplier * kotlin.math.cos(cannonAngle)
        bullets.add(Bullet(endX, endY, dx, dy))
        soundManager.playSound(SoundManager.SoundType.SHOOT)
    }

    private fun drawScore(canvas: Canvas) {
        paint.color = if (isDarkTheme) Color.WHITE else Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        
        canvas.save()
        canvas.scale(scoreScale, scoreScale, width/2f, 200f)
        paint.textSize = 60f
        val scoreText = "$score"
        canvas.drawText(scoreText, width/2f, 200f, paint)
        canvas.restore()
    }

    private fun drawTime(canvas: Canvas) {
        paint.color = if (isDarkTheme) Color.LTGRAY else Color.DKGRAY
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 40f
        
        val minutes = gameTime / 60000L
        val seconds = (gameTime % 60000L) / 1000L
        val timeText = "Time: ${minutes}:${String.format("%02d", seconds)}"
        canvas.drawText(timeText, width/2f, 270f, paint)
    }

    private fun createCelebrationEffect() {
        val particleCount = 25
        val startX = if (currentCorner == 0) 0f else width.toFloat()
        for (i in 0..particleCount) {
            val angle = when (currentCorner) {
                0 -> Random.nextDouble() * Math.PI / 2 - Math.PI / 4 // Spray right and up for left corner
                else -> Random.nextDouble() * Math.PI / 2 + Math.PI * 3/4 // Spray left and up for right corner
            }
            val speed = Random.nextDouble() * 15 + 5
            celebrationParticles.add(
                CelebrationParticle(
                    x = startX,
                    y = 0f,
                    dx = (Math.cos(angle) * speed).toFloat(),
                    dy = (Math.sin(angle) * speed).toFloat(),
                    color = celebrationColors[Random.nextInt(celebrationColors.size)],
                    size = Random.nextFloat() * 20f + 10f
                )
            )
        }
        
        // Switch corners
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCornerCelebrationTime > cornerCelebrationInterval) {
            currentCorner = (currentCorner + 1) % 2
            lastCornerCelebrationTime = currentTime
        }
    }

    private fun drawGameOver(canvas: Canvas) {
        // Draw semi-transparent overlay
        canvas.drawColor(Color.argb(200, 0, 0, 0))

        // Check for new high score
        if (score > highScore) {
            highScore = score
            isNewHighScore = true
            prefs.edit().putInt("high_score", highScore).apply()
            soundManager.playSound(SoundManager.SoundType.VICTORY_TRUMPET)
        }

        // Draw high score status
        if (isNewHighScore) {

            if (!gameOverMusicHandled) {
                (context as MainActivity).pauseHomeMusic()
                gameOverMusicHandled = true
            }
            
            paint.color = Color.YELLOW
            canvas.drawText(" NEW HIGH SCORE !", width / 2f, height / 2f - 600f, paint)
        } 

        // Draw game over text
        paint.textSize = 80f
        paint.color = Color.WHITE
        paint.typeface = gameFont
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("GAME OVER", width / 2f, height / 2f - 200f, paint)

        // Draw score
        paint.textSize = 60f
        canvas.drawText("Score: $score", width / 2f, height / 2f - 100f, paint)



        // Draw home button
        val buttonPaint = Paint().apply {
            color = Color.parseColor("#006400") // Dark green
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw button background
        canvas.drawRoundRect(
            homeButtonRect,
            25f, // Corner radius
            25f,
            buttonPaint
        )

        // Draw button border
        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            homeButtonRect,
            25f,
            25f,
            borderPaint
        )

        // Draw button text with same styling as Play Now button
        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.typeface = gameFont
        paint.textAlign = Paint.Align.CENTER
        paint.isAntiAlias = true
        paint.setShadowLayer(8f, 8f, 8f, Color.BLACK)
        
        // Calculate text position to be perfectly centered
        val textBounds = android.graphics.Rect()
        paint.getTextBounds("Home", 0, "Home".length, textBounds)
        val textY = homeButtonRect.centerY() - (textBounds.top + textBounds.bottom) / 2
        
        canvas.drawText("Home", homeButtonRect.centerX(), textY, paint)
        paint.clearShadowLayer()

        // Update and draw celebration particles if new high score
        if (isNewHighScore) {
            // Create random bursts 3 times per second
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastParticleBurstTime >= burstInterval) {
                lastParticleBurstTime = currentTime // Move this before the effects to prevent double triggering
                createCelebrationEffect()
                soundManager.playSound(SoundManager.SoundType.FIREWORK_SHOT)
            }
            
            synchronized(celebrationParticles) {
                celebrationParticles.forEach { particle ->
                    particle.x += particle.dx
                    particle.y += particle.dy
                    particle.dy += 0.5f 
                    
                    paint.color = particle.color
                    paint.alpha = 255
                    canvas.drawCircle(particle.x, particle.y, particle.size, paint)
                }
                paint.alpha = 255
                
                // Remove particles that are off screen
                celebrationParticles.removeAll { particle -> 
                    particle.y > height || particle.x < 0 || particle.x > width 
                }
            }
        }
    }

    fun getScore(): Int {
        return score
    }

    private fun createBubblePopEffect(x: Float, y: Float, radius: Float, color: Int) {
        val particleCount = 8
        val particleSpeed = 8f
        val particleSize = radius / 2f
        
        for (i in 0 until particleCount) {
            val angle = (i * (360f / particleCount)) * (Math.PI / 180)
            particles.add(BubbleParticle(
                x = x,
                y = y,
                dx = (Math.cos(angle) * particleSpeed).toFloat(),
                dy = (Math.sin(angle) * particleSpeed).toFloat(),
                scale = particleSize,
                color = color  // Pass the color to the particle
            ))
        }
    }

    private data class DelayedExplosion(
        val x: Float,
        val y: Float,
        val size: Float,
        val delay: Long
    )

    // In createMultipleExplosions function, add isCannonExplosion flag
    private fun createMultipleExplosions(centerX: Float, centerY: Float) {
        isCannonExplosion = true
        val currentTime = System.currentTimeMillis()
        val explosionPattern = listOf(
            DelayedExplosion(centerX, centerY, 500f, 0L),
            DelayedExplosion(centerX - 100f, centerY, 100f, 200L),
            DelayedExplosion(centerX + 100f, centerY, 100f, 400L),
            DelayedExplosion(centerX, centerY - 50f, 300f, 600L),
            DelayedExplosion(centerX, centerY + 50f, 300f, 800L)
        )

        explosionPattern.forEach { delayed ->
            explosions.add(Explosion(
                x = delayed.x,
                y = delayed.y,
                size = delayed.size,
                startTime = currentTime + delayed.delay
            ))
        }
    }

    private data class Bubble(
        var x: Float, 
        var y: Float, 
        val speed: Float,
        val radius: Float,
        val points: Int,
        val spawnTime: Long,
        val isTnt: Boolean,
        val isFlipped: Boolean = Random.nextBoolean() // Add random flip state
    )
    private data class Bullet(var x: Float, var y: Float, val dx: Float, val dy: Float)

    private fun applyPowerUpEffect(powerUp: PowerUp) {
        when (powerUp.type) {
            PowerUpType.RAPID_FIRE -> {
                bulletRefillRate = originalBulletRefillRate * 2
                bulletSpeedMultiplier = 3f
                android.os.Handler().postDelayed({
                    if (isPlaying) {
                        bulletRefillRate = originalBulletRefillRate
                        bulletSpeedMultiplier = 1f
                    }
                }, powerUp.timeWallet * 1000L)
            }
            PowerUpType.DOUBLE_POINTS -> {
                scoreMultiplier = 2
                android.os.Handler().postDelayed({
                    if (isPlaying) {
                        scoreMultiplier = 1
                    }
                }, powerUp.timeWallet * 1000L)
            }
            PowerUpType.SHIELD -> {
                isShieldActive = true
                android.os.Handler().postDelayed({
                    if (isPlaying) {
                        isShieldActive = false
                    }
                }, powerUp.timeWallet * 1000L)
            }
            PowerUpType.MEGA_BULLET -> {
                currentBulletRadius = originalBulletRadius * 2
                android.os.Handler().postDelayed({
                    if (isPlaying) {
                        currentBulletRadius = originalBulletRadius
                    }
                }, powerUp.timeWallet * 1000L)
            }
        }
    }

    // Modify collision checks with cannon to respect shield
    private fun checkShieldCollision(x: Float, y: Float, size: Float): Boolean {
        if (!isShieldActive) return false
        
        val cannonX = width / 2f
        val cannonY = height.toFloat() - meterHeight - meterPadding - 20f
        val distanceToCenter = Math.hypot((x - cannonX).toDouble(), (y - cannonY).toDouble())
        return distanceToCenter <= (width / 4f) + size
    }

    private fun checkCannonCollision(x: Float, y: Float, size: Float): Boolean {
        if (isShieldActive) return false
        
        val cannonX = width / 2f
        val cannonY = height.toFloat() - meterHeight - meterPadding - 20f
        return Math.hypot((x - cannonX).toDouble(), (y - cannonY).toDouble()) <= size + 30f
    }
}