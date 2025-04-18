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
    private val tntSpawnInterval = 5000L // 5 seconds in milliseconds

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
    private val bulletRefillRate = 1f // bullets per second
    private val meterWidth = 200f
    private val meterHeight = 30f
    private val meterPadding = 20f

    // Bullet properties
    private val bulletSpeed = 15f
    private val bulletRadius = 10f

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
    private var rock_bonus = 10

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

    init {
        holder.addCallback(this)
        paint = Paint()
        gameFont = ResourcesCompat.getFont(context, R.font.press_start_2p) ?: Typeface.DEFAULT
        paint.color = Color.BLUE
        paint.style = Paint.Style.FILL
        score = 0
        gameTime = 0L
        lastUpdateTime = System.currentTimeMillis()
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
                rock.y += rock.speed
                
                val cannonX = width / 2f
                val cannonY = height.toFloat() - meterHeight - meterPadding - 20f
                if (Math.hypot((rock.x - cannonX).toDouble(), (rock.y - cannonY).toDouble()) <= rock.size + 30f) {
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
                                Math.hypot((bullet.x - bubble.x).toDouble(), (bullet.y - bubble.y).toDouble()) <= bubble.radius + bulletRadius
                            }?.let { bubble ->
                                if (bubble.isTnt) {
                                    createBubblePopEffect(bubble.x, bubble.y, bubble.radius, bombParticleColor)
                                    soundManager.playSound(SoundManager.SoundType.BOMB_EXPLODE)
                                    isGameFrozen = true
                                    gameOverStartTime = System.currentTimeMillis()
                                    return
                                } else {
                                    // Create pop effect immediately before removing bubble
                                    createBubblePopEffect(bubble.x, bubble.y, bubble.radius, particleColor)
                                    soundManager.playSound(SoundManager.SoundType.BUBBLE_BURST)
                                    score += bubble.points
                                    bubblesToRemove.add(bubble)
                                    bulletsToRemove.add(bullet)
                                }
                            }

                            // Check rock collisions
                            rocks.find { rock ->
                                Math.hypot((bullet.x - rock.x).toDouble(), (bullet.y - rock.y).toDouble()) <= rock.size + bulletRadius
                            }?.let { rock ->
                                rock.health--
                                if (rock.health <= 0) {
                                    rocksToRemove.add(rock)
                                    score += rock_bonus
                                    floatingScores.add(FloatingScore(rock.x, rock.y, rock_bonus))
                                    soundManager.playSound(SoundManager.SoundType.BUBBLE_BURST)
                                } else {
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
                canvas.drawCircle(bullet.x, bullet.y, bulletRadius, paint)
            }
        }

        // Draw cannon
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

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                updateCannonAngle(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                updateCannonAngle(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                if (bulletMeter >= 1f) {
                    shootBullet()
                    bulletMeter -= 1f
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

        val dx = bulletSpeed * kotlin.math.sin(cannonAngle)
        val dy = -bulletSpeed * kotlin.math.cos(cannonAngle)
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
}