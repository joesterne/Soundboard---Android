package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.SoundPool
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import java.io.File

class AudioEngine(private val context: Context) {
    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<String, Int>()
    private val activeStreams = mutableMapOf<Int, Int>()
    
    private val activeMediaPlayers = mutableMapOf<Int, MediaPlayer>()
    private val stopRunnables = mutableMapOf<Int, Runnable>()
    private val fadeOutRunnables = mutableMapOf<Int, Runnable>()
    private val activeEnhancers = mutableMapOf<Int, LoudnessEnhancer>()
    private val activeAnimators = mutableMapOf<Int, MutableList<android.animation.ValueAnimator>>()

    private val handler = Handler(Looper.getMainLooper())

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // To be heard by callers
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    fun loadSound(path: String) {
        val file = File(path)
        if (!soundMap.containsKey(path) && file.exists() && file.isFile && file.canRead()) {
            try {
                val soundId = soundPool.load(path, 1)
                soundMap[path] = soundId
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playSound(
        id: Int, 
        path: String, 
        volume: Float = 1.0f, 
        isLooping: Boolean = false,
        trimStartMs: Long? = null,
        trimEndMs: Long? = null,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        playbackSpeed: Float = 1.0f
    ) {
        stopSound(id)

        val file = File(path)
        if (!file.exists() || !file.isFile || !file.canRead()) return
        val safeVolume = volume.coerceIn(0f, 5f)
        val safeSpeed = playbackSpeed.coerceIn(0.5f, 2.0f)

        if (trimStartMs != null || trimEndMs != null || safeVolume > 1.0f || fadeInMs > 0L || fadeOutMs > 0L || safeSpeed != 1.0f) {
            playWithMediaPlayer(id, path, safeVolume, isLooping, trimStartMs, trimEndMs, fadeInMs, fadeOutMs, safeSpeed)
            return
        }

        val loopMode = if (isLooping) -1 else 0
        
        val soundId = soundMap[path]
        if (soundId != null) {
            val streamId = soundPool.play(soundId, safeVolume, safeVolume, 1, loopMode, 1f)
            if (isLooping && streamId != 0) {
                activeStreams[id] = streamId
            }
        } else {
            // Try loading it on the fly if not loaded yet
            try {
                val newId = soundPool.load(path, 1)
                soundMap[path] = newId
                soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
                    if (status == 0 && sampleId == newId) {
                        val streamId = pool.play(sampleId, safeVolume, safeVolume, 1, loopMode, 1f)
                        if (isLooping && streamId != 0) {
                            activeStreams[id] = streamId
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playWithMediaPlayer(
        id: Int, 
        path: String, 
        volume: Float, 
        isLooping: Boolean, 
        startMs: Long?, 
        endMs: Long?, 
        fadeInMs: Long, 
        fadeOutMs: Long,
        playbackSpeed: Float = 1.0f
    ) {
        var mp: MediaPlayer? = null
        try {
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) return
            
            mp = MediaPlayer()
            mp.setDataSource(path)
            
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            mp.setAudioAttributes(audioAttributes)
            
            val targetMpVolume = if (volume > 1.0f) 1.0f else volume
            val initialMpVolume = if (fadeInMs > 0L) 0f else targetMpVolume
            
            if (volume > 1.0f) {
                mp.setVolume(initialMpVolume, initialMpVolume)
                try {
                    val enhancer = LoudnessEnhancer(mp.audioSessionId)
                    val gainmB = (Math.log10(volume.toDouble()) * 2000.0).toInt()
                    enhancer.setTargetGain(gainmB)
                    enhancer.enabled = true
                    activeEnhancers[id] = enhancer
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                mp.setVolume(initialMpVolume, initialMpVolume)
            }
            
            mp.prepare()
            
            val actualStartMs = startMs ?: 0L
            if (actualStartMs > 0) {
                mp.seekTo(actualStartMs.toInt())
            }

            val safeSpeed = playbackSpeed.coerceIn(0.5f, 2.0f)
            try {
                mp.playbackParams = PlaybackParams().setSpeed(safeSpeed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val audioTrackDurationMs = if (endMs != null) (endMs - actualStartMs) else (mp.duration - actualStartMs).toLong()
            val durationToPlay = (audioTrackDurationMs / safeSpeed).toLong()
            
            val animators = mutableListOf<android.animation.ValueAnimator>()
            if (fadeInMs > 0L) {
                val fadeIn = android.animation.ValueAnimator.ofFloat(0f, targetMpVolume).apply {
                    duration = fadeInMs
                    addUpdateListener {
                        val v = it.animatedValue as Float
                        if (activeMediaPlayers[id] == mp) {
                            mp.setVolume(v, v)
                        }
                    }
                }
                animators.add(fadeIn)
            }
            
            // For loops, we re-trigger fade-in and fade-out on each loop
            if (isLooping) {
                if (endMs != null || fadeOutMs > 0L) {
                    val actualLoopDuration = if (endMs != null) durationToPlay else (mp.duration.toLong() / safeSpeed).toLong()
                    if (actualLoopDuration > 0) {
                        val loopRunnable = object : Runnable {
                            override fun run() {
                                if (activeMediaPlayers[id] == mp) {
                                    mp.seekTo(actualStartMs.toInt())
                                    mp.start()
                                    if (fadeInMs > 0L) animators.forEach { if(it.duration == fadeInMs) it.start() }
                                    
                                    if (fadeOutMs > 0L && actualLoopDuration > fadeOutMs) {
                                        val fadeOut = android.animation.ValueAnimator.ofFloat(targetMpVolume, 0f).apply {
                                            duration = fadeOutMs
                                            addUpdateListener {
                                                val v = it.animatedValue as Float
                                                if (activeMediaPlayers[id] == mp) {
                                                    mp.setVolume(v, v)
                                                }
                                            }
                                        }
                                        val runOut = Runnable { if (activeMediaPlayers[id] == mp) fadeOut.start() }
                                        handler.postDelayed(runOut, actualLoopDuration - fadeOutMs)
                                        fadeOutRunnables[id] = runOut
                                        animators.add(fadeOut)
                                    }
                                    
                                    handler.postDelayed(this, actualLoopDuration)
                                }
                            }
                        }
                        mp.start()
                        if (fadeInMs > 0L) animators.forEach { it.start() }
                        
                        if (fadeOutMs > 0L && actualLoopDuration > fadeOutMs) {
                            val fadeOut = android.animation.ValueAnimator.ofFloat(targetMpVolume, 0f).apply {
                                duration = fadeOutMs
                                addUpdateListener {
                                    val v = it.animatedValue as Float
                                    if (activeMediaPlayers[id] == mp) {
                                        mp.setVolume(v, v)
                                    }
                                }
                            }
                            val runOut = Runnable { if (activeMediaPlayers[id] == mp) fadeOut.start() }
                            handler.postDelayed(runOut, actualLoopDuration - fadeOutMs)
                            fadeOutRunnables[id] = runOut
                            animators.add(fadeOut)
                        }
                        
                        handler.postDelayed(loopRunnable, actualLoopDuration)
                        stopRunnables[id] = loopRunnable
                    }
                } else {
                    mp.isLooping = true
                    mp.start()
                    if (fadeInMs > 0L) animators.forEach { it.start() }
                }
            } else {
                if (endMs != null || fadeOutMs > 0L) {
                    val actualStopDuration = if (endMs != null) durationToPlay else ((mp.duration.toLong() - actualStartMs) / safeSpeed).toLong()
                    if (actualStopDuration > 0) {
                        val stopRunnable = Runnable {
                            if (activeMediaPlayers[id] == mp) {
                                mp.stop()
                                mp.release()
                                activeMediaPlayers.remove(id)
                                stopRunnables.remove(id)
                                fadeOutRunnables.remove(id)
                                activeAnimators.remove(id)
                            }
                        }
                        handler.postDelayed(stopRunnable, actualStopDuration)
                        stopRunnables[id] = stopRunnable
                        
                        if (fadeOutMs > 0L && actualStopDuration > fadeOutMs) {
                            val fadeOut = android.animation.ValueAnimator.ofFloat(targetMpVolume, 0f).apply {
                                duration = fadeOutMs
                                addUpdateListener {
                                    val v = it.animatedValue as Float
                                    if (activeMediaPlayers[id] == mp) {
                                        mp.setVolume(v, v)
                                    }
                                }
                            }
                            val runOut = Runnable { if (activeMediaPlayers[id] == mp) fadeOut.start() }
                            handler.postDelayed(runOut, actualStopDuration - fadeOutMs)
                            fadeOutRunnables[id] = runOut
                            animators.add(fadeOut)
                        }
                    }
                }
                mp.setOnCompletionListener {
                    it.release()
                    activeMediaPlayers.remove(id)
                    stopRunnables.remove(id)
                    fadeOutRunnables.remove(id)
                    activeAnimators.remove(id)
                }
                mp.start()
                if (fadeInMs > 0L) animators.forEach { it.start() }
            }
            
            activeMediaPlayers[id] = mp
            if (animators.isNotEmpty()) {
                activeAnimators[id] = animators
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                mp?.release()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            activeMediaPlayers.remove(id)
            activeEnhancers.remove(id)?.release()
        }
    }

    fun stopSound(id: Int) {
        val streamId = activeStreams.remove(id)
        if (streamId != null) {
            soundPool.stop(streamId)
        }
        
        val mp = activeMediaPlayers.remove(id)
        if (mp != null) {
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val enhancer = activeEnhancers.remove(id)
        if (enhancer != null) {
            try {
                enhancer.enabled = false
                enhancer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val runnable = stopRunnables.remove(id)
        if (runnable != null) {
            handler.removeCallbacks(runnable)
        }
        
        val fadeOutRunnable = fadeOutRunnables.remove(id)
        if (fadeOutRunnable != null) {
            handler.removeCallbacks(fadeOutRunnable)
        }
        
        val animators = activeAnimators.remove(id)
        animators?.forEach { it.cancel() }
    }
    
    fun isLoopingActive(id: Int): Boolean {
        return activeStreams.containsKey(id) || activeMediaPlayers.containsKey(id)
    }

    fun release() {
        soundPool.release()
        soundMap.clear()
        activeStreams.clear()
        
        activeMediaPlayers.values.forEach {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeMediaPlayers.clear()
        
        activeEnhancers.values.forEach {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeEnhancers.clear()
        
        stopRunnables.values.forEach { handler.removeCallbacks(it) }
        stopRunnables.clear()
        
        fadeOutRunnables.values.forEach { handler.removeCallbacks(it) }
        fadeOutRunnables.clear()
        
        activeAnimators.values.forEach { animators -> animators.forEach { it.cancel() } }
        activeAnimators.clear()
    }
}
